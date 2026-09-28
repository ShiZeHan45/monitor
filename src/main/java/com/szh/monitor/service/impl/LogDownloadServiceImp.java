package com.szh.monitor.service.impl;

import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.form.LogDownloadRequest;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import com.szh.monitor.service.LogDownloadService;
import com.szh.monitor.vo.LogDownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 日志下载实现：
 * 复用 Grafana→Loki 查询链路（Basic Auth + datasource proxy + query_range），
 * 分页拉取（limit=1000，游标=本批最大时间戳+1ms）流式落盘，防止内存溢出；
 * 后台线程池最多 2 个并发任务，超出排队；兜底上限 100 万条/500MB 截断并在文件头注明；
 * 不读取也不更新现有采集游标 lastTsMap。
 * 文件长期保留，由用户在页面上手动删除；启动时仅清扫 7 天以上的孤儿文件。
 */
@Service
public class LogDownloadServiceImp implements LogDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LogDownloadServiceImp.class);

    static final int PAGE_LIMIT = 1000;
    static final long MAX_LINES = 1_000_000L;
    static final long MAX_BYTES = 500L * 1024 * 1024;
    private static final long ORPHAN_SWEEP_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int PREVIEW_LIMIT = 100;
    private static final DateTimeFormatter REQ_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final GrafanaDataSourceService dataSourceService;
    private final GrafanaMonitorRuleService ruleService;

    /** 后台下载线程池：最多 2 个并发任务，超出排队 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "log-download-worker");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, LogDownloadTask> tasks = new ConcurrentHashMap<>();
    private final String baseDir = resolveBaseDir().getAbsolutePath();

    /**
     * 文件存放目录：优先系统属性 log.download.dir / 环境变量 LOG_DOWNLOAD_DIR；
     * 服务器上自动落在 /soft/actuator/log-download（避开 /tmp 被系统定期清理）；本地开发用临时目录。
     */
    private static File resolveBaseDir() {
        String configured = System.getProperty("log.download.dir");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("LOG_DOWNLOAD_DIR");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim());
        }
        File serverDir = new File("/soft/actuator");
        if (serverDir.isDirectory()) {
            return new File(serverDir, "log-download");
        }
        return new File(System.getProperty("java.io.tmpdir"), "log-download");
    }

    public LogDownloadServiceImp(GrafanaDataSourceService dataSourceService,
                                  GrafanaMonitorRuleService ruleService) {
        this.dataSourceService = dataSourceService;
        this.ruleService = ruleService;
        sweepOrphanFiles();
    }

    /** Loki 批次拉取器（抽象出 HTTP 细节，便于单元测试分页/过滤/上限逻辑） */
    interface BatchFetcher {
        Object fetch(long startMs, long endMs);
    }

    // ==================== 公共接口 ====================

    @Override
    public List<String> listServices(String environmentName) {
        GrafanaDataSource ds = dataSourceService.getByEnvironmentName(environmentName);
        if (ds == null) {
            return Collections.emptyList();
        }
        List<GrafanaMonitorRule> rules = ruleService.listByDataSourceId(ds.getId());
        if (rules == null) {
            return Collections.emptyList();
        }
        return rules.stream().map(GrafanaMonitorRule::getName).collect(Collectors.toList());
    }

    @Override
    public String submitTask(LogDownloadRequest request) {
        ResolvedRequest resolved = resolve(request);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        LogDownloadTask task = new LogDownloadTask();
        task.setTaskId(taskId);
        task.setEnvironmentName(resolved.dataSource.getEnvironmentName());
        task.setService(request.getService().trim());
        task.setStartTimeMs(resolved.startMs);
        task.setEndTimeMs(resolved.endMs);
        task.setCreateTime(System.currentTimeMillis());
        task.setStatus(LogDownloadTask.STATUS_QUEUED);
        tasks.put(taskId, task);

        List<String> includes = splitKeywords(request.getIncludeKeywords());
        List<String> excludes = splitKeywords(request.getExcludeKeywords());
        executor.submit(() -> runTask(task, resolved.dataSource, resolved.selector, includes, excludes));
        return taskId;
    }

    @Override
    public LogDownloadTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public List<LogDownloadTask> listTasks() {
        List<LogDownloadTask> list = new ArrayList<>(tasks.values());
        list.sort((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return list;
    }

    @Override
    public void deleteTask(String taskId) {
        LogDownloadTask task = tasks.remove(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getFilePath() != null) {
            deleteQuietly(new File(task.getFilePath()));
        }
        // 运行中任务的临时 part 文件一并清理
        deleteQuietly(new File(baseDir, taskId + ".part"));
        logger.info("日志下载任务已删除: {} 环境: {} 服务: {}",
                taskId, task.getEnvironmentName(), task.getService());
    }

    @Override
    public List<String> preview(LogDownloadRequest request) {
        ResolvedRequest resolved = resolve(request);
        WebClient client = buildWebClient(resolved.dataSource);
        String url = lokiQueryUrl(resolved.dataSource);
        BatchFetcher fetcher = (s, e) -> fetchBatch(client, url, resolved.selector, s, e, PAGE_LIMIT);

        List<Object[]> entries = parseEntries(fetcher.fetch(resolved.startMs, resolved.endMs));
        List<String> includes = splitKeywords(request.getIncludeKeywords());
        List<String> excludes = splitKeywords(request.getExcludeKeywords());
        List<String> lines = new ArrayList<>();
        for (Object[] entry : entries) {
            String line = (String) entry[1];
            if (matchesFilter(line, includes, excludes)) {
                lines.add(formatLine((Long) entry[0], request.getService().trim(), line));
                if (lines.size() >= PREVIEW_LIMIT) {
                    break;
                }
            }
        }
        return lines;
    }

    // ==================== 任务执行 ====================

    private void runTask(LogDownloadTask task, GrafanaDataSource ds, String selector,
                         List<String> includes, List<String> excludes) {
        task.setStatus(LogDownloadTask.STATUS_RUNNING);
        File partFile = new File(baseDir, task.getTaskId() + ".part");
        File finalFile = null;
        try {
            Files.createDirectories(new File(baseDir).toPath());

            WebClient client = buildWebClient(ds);
            String url = lokiQueryUrl(ds);
            BatchFetcher fetcher = (s, e) -> fetchBatch(client, url, selector, s, e, PAGE_LIMIT);

            // 阶段一：分页拉取并流式写入临时 part 文件（内存只驻留一批）
            try (PrintWriter writer = new PrintWriter(
                    Files.newBufferedWriter(partFile.toPath(), StandardCharsets.UTF_8))) {
                writeBatches(fetcher, task.getStartTimeMs(), task.getEndTimeMs(),
                        includes, excludes, task.getService(), writer, task, MAX_LINES, MAX_BYTES);
            }

            // 阶段二：生成最终文件（文件头含统计信息）+ 追加内容，随后删除 part
            String fileName = task.getService() + "_" + FILE_TS_FMT.format(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(task.getCreateTime()), ZoneId.systemDefault())) + ".log";
            finalFile = new File(baseDir, fileName);
            try (BufferedWriter out = Files.newBufferedWriter(finalFile.toPath(), StandardCharsets.UTF_8)) {
                writeHeader(out, task, includes, excludes);
                try (BufferedReader br = Files.newBufferedReader(partFile.toPath(), StandardCharsets.UTF_8)) {
                    char[] buf = new char[8192];
                    int n;
                    while ((n = br.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
            }
            deleteQuietly(partFile);

            task.setFileName(fileName);
            task.setFilePath(finalFile.getAbsolutePath());
            task.setFinishTime(System.currentTimeMillis());
            task.setStatus(LogDownloadTask.STATUS_SUCCESS);
            logger.info("日志下载任务完成: {} 环境: {} 服务: {} 共 {} 行, 文件: {}",
                    task.getTaskId(), task.getEnvironmentName(), task.getService(), task.getProcessedLines(), fileName);
        } catch (Exception e) {
            logger.error("日志下载任务失败: " + task.getTaskId(), e);
            deleteQuietly(partFile);
            if (finalFile != null) {
                deleteQuietly(finalFile);
            }
            task.setFilePath(null);
            task.setFinishTime(System.currentTimeMillis());
            task.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            task.setStatus(LogDownloadTask.STATUS_FAILED);
        }
    }

    /** 启动时清扫 7 天以上的孤儿文件（任务为内存态，重启后无从引用），防止磁盘占满 */
    private void sweepOrphanFiles() {
        try {
            File[] files = new File(baseDir).listFiles();
            if (files == null) {
                return;
            }
            long cutoff = System.currentTimeMillis() - ORPHAN_SWEEP_MS;
            for (File f : files) {
                if (f.isFile() && f.lastModified() < cutoff) {
                    deleteQuietly(f);
                }
            }
        } catch (Exception e) {
            logger.warn("启动清扫历史下载文件失败", e);
        }
    }

    private void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            logger.warn("删除文件失败: {}", file.getAbsolutePath(), e);
        }
    }

    private void writeHeader(Writer out, LogDownloadTask task,
                             List<String> includes, List<String> excludes) throws java.io.IOException {
        out.write("# 环境: " + task.getEnvironmentName() + "  服务: " + task.getService() + "\n");
        out.write("# 时间范围: " + REQ_FMT.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(task.getStartTimeMs()), ZoneId.systemDefault()))
                + " ~ " + REQ_FMT.format(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(task.getEndTimeMs()), ZoneId.systemDefault())) + "\n");
        out.write("# 关键词过滤: 包含=" + includes + " 排除=" + excludes + "\n");
        out.write("# 总行数: " + task.getProcessedLines() + "\n");
        if (task.isTruncated()) {
            out.write("# 注意: 日志量达到上限(100万条或500MB)，已截断\n");
        }
    }

    /**
     * 分页拉取并写盘核心逻辑：
     * - 游标 = 本批最大时间戳 + 1ms，向前翻页直到不足一页；
     * - 应用包含/排除关键词过滤；
     * - 达到行数或字节上限时置 truncated 并停止；
     * - 游标不前进时终止，防止死循环。
     *
     * @return 实际写入行数
     */
    long writeBatches(BatchFetcher fetcher, long startMs, long endMs,
                      List<String> includeKeywords, List<String> excludeKeywords,
                      String service, PrintWriter writer, LogDownloadTask task,
                      long maxLines, long maxBytes) {
        long cursor = startMs;
        long lines = 0;
        long bytes = 0;
        boolean truncated = false;
        while (true) {
            List<Object[]> entries = parseEntries(fetcher.fetch(cursor, endMs));
            if (entries.isEmpty()) {
                break;
            }

            // 初始化为 MIN_VALUE：若整批条目都早于游标（无可写新数据），
            // nextCursor 不会前进，循环终止，防止游标原地+1死循环
            long batchMaxTs = Long.MIN_VALUE;
            for (Object[] entry : entries) {
                long ts = (Long) entry[0];
                if (ts > batchMaxTs) {
                    batchMaxTs = ts;
                }
            }

            // 批内按时间升序写出，便于阅读
            entries.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));

            for (Object[] entry : entries) {
                long ts = (Long) entry[0];
                String line = (String) entry[1];
                if (ts < cursor || ts > endMs) {
                    continue;
                }
                if (!matchesFilter(line, includeKeywords, excludeKeywords)) {
                    continue;
                }
                String formatted = formatLine(ts, service, line);
                long lineBytes = formatted.getBytes(StandardCharsets.UTF_8).length + 1;
                if (lines >= maxLines || bytes + lineBytes > maxBytes) {
                    truncated = true;
                    break;
                }
                writer.write(formatted + "\n");
                lines++;
                bytes += lineBytes;
                task.setProcessedLines(lines);
            }
            if (truncated) {
                break;
            }

            if (entries.size() < PAGE_LIMIT) {
                break;
            }
            long nextCursor = batchMaxTs + 1;
            if (nextCursor <= cursor) {
                break;
            }
            cursor = nextCursor;
        }
        task.setTruncated(truncated);
        return lines;
    }

    // ==================== Loki HTTP ====================

    private String lokiQueryUrl(GrafanaDataSource ds) {
        String baseUrl = ds.getUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/datasources/proxy/" + ds.getDatasourceId() + "/loki/api/v1/query_range";
    }

    private WebClient buildWebClient(GrafanaDataSource ds) {
        String basicAuth = Base64Utils.encodeToString((ds.getUsername() + ":" + ds.getPassword()).getBytes());
        return WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(config -> config.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    private Object fetchBatch(WebClient client, String url, String query, long startMs, long endMs, int limit) {
        return client.get()
                .uri(url + "?direction=forward&query={query}&start={start}&end={end}&limit={limit}",
                        query, startMs * 1_000_000L, endMs * 1_000_000L, limit)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .block();
    }

    // ==================== 请求解析 ====================

    private static class ResolvedRequest {
        GrafanaDataSource dataSource;
        String selector;
        long startMs;
        long endMs;
    }

    private ResolvedRequest resolve(LogDownloadRequest request) {
        if (request.getEnv() == null || request.getEnv().trim().isEmpty()) {
            throw new IllegalArgumentException("环境不能为空");
        }
        if (request.getService() == null || request.getService().trim().isEmpty()) {
            throw new IllegalArgumentException("服务不能为空");
        }
        long startMs = parseTime(request.getStartTime(), "开始时间");
        long endMs = parseTime(request.getEndTime(), "结束时间");
        if (startMs >= endMs) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }

        GrafanaDataSource ds = dataSourceService.getByEnvironmentName(request.getEnv());
        if (ds == null) {
            throw new IllegalArgumentException("环境不存在: " + request.getEnv());
        }

        GrafanaMonitorRule target = null;
        List<GrafanaMonitorRule> rules = ruleService.listByDataSourceId(ds.getId());
        if (rules != null) {
            for (GrafanaMonitorRule r : rules) {
                if (request.getService().trim().equals(r.getName())) {
                    target = r;
                    break;
                }
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("服务不存在: " + request.getService());
        }

        ResolvedRequest resolved = new ResolvedRequest();
        resolved.dataSource = ds;
        resolved.selector = extractSelector(target.getQueryExpr());
        resolved.startMs = startMs;
        resolved.endMs = endMs;
        return resolved;
    }

    private long parseTime(String time, String field) {
        if (time == null || time.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        try {
            return LocalDateTime.parse(time.trim(), REQ_FMT)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式必须为 yyyy-MM-dd HH:mm:ss: " + time);
        }
    }

    // ==================== 纯逻辑（包内可见便于单测） ====================

    /**
     * 从监控规则查询表达式中截取标签选择器部分（第一个 | 之前），
     * 保证下载的是全量日志而不是被 |= "ERROR" 过滤后的子集。
     */
    static String extractSelector(String queryExpr) {
        if (queryExpr == null) {
            throw new IllegalArgumentException("查询表达式不能为空");
        }
        String trimmed = queryExpr.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("查询表达式不能为空");
        }
        int idx = trimmed.indexOf('|');
        String selector = (idx >= 0 ? trimmed.substring(0, idx) : trimmed).trim();
        if (selector.isEmpty()) {
            throw new IllegalArgumentException("无法从查询表达式中解析标签选择器: " + queryExpr);
        }
        return selector;
    }

    /** 逗号分隔关键词解析：去空白、跳过空项 */
    static List<String> splitKeywords(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** 包含关键词任一命中才保留（为空则全保留）；排除关键词任一命中则丢弃 */
    static boolean matchesFilter(String line, List<String> includeKeywords, List<String> excludeKeywords) {
        if (includeKeywords != null && !includeKeywords.isEmpty()
                && includeKeywords.stream().noneMatch(line::contains)) {
            return false;
        }
        if (excludeKeywords != null && excludeKeywords.stream().anyMatch(line::contains)) {
            return false;
        }
        return true;
    }

    /** 文件行格式：yyyy-MM-dd HH:mm:ss.SSS [服务名] 日志内容 */
    static String formatLine(long tsMs, String service, String log) {
        return LINE_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMs), ZoneId.systemDefault()))
                + " [" + service + "] " + log;
    }

    /** 解析 Loki query_range 响应体为 [tsMs, line] 列表；坏数据一律当作空批次 */
    @SuppressWarnings("unchecked")
    static List<Object[]> parseEntries(Object rawBody) {
        List<Object[]> entries = new ArrayList<>();
        if (!(rawBody instanceof java.util.Map)) {
            return entries;
        }
        Map<String, Object> body = (Map<String, Object>) rawBody;
        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map)) {
            return entries;
        }
        Object resultObj = ((Map<String, Object>) dataObj).get("result");
        if (!(resultObj instanceof List)) {
            return entries;
        }
        for (Object streamObj : (List<Object>) resultObj) {
            if (!(streamObj instanceof Map)) {
                continue;
            }
            Object valuesObj = ((Map<String, Object>) streamObj).get("values");
            if (!(valuesObj instanceof List)) {
                continue;
            }
            for (Object valueObj : (List<Object>) valuesObj) {
                if (!(valueObj instanceof List)) {
                    continue;
                }
                List<Object> value = (List<Object>) valueObj;
                if (value.size() < 2 || value.get(1) == null) {
                    continue;
                }
                try {
                    long ts = Long.parseLong(String.valueOf(value.get(0))) / 1_000_000;
                    entries.add(new Object[]{ts, String.valueOf(value.get(1))});
                } catch (NumberFormatException ignore) {
                    // 跳过时间戳非法的条目
                }
            }
        }
        return entries;
    }
}
