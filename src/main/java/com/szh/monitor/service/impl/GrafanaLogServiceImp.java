package com.szh.monitor.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import java.text.MessageFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GrafanaLogServiceImp {
    Logger logger = LoggerFactory.getLogger(GrafanaLogServiceImp.class);
    private final TreeMap<String, WebClient> webClientMap = new TreeMap<>();
    private final Map<String, DataSourceInfo> dataSourceInfoMap = new HashMap<>();

    public Map<String, DataSourceInfo> getDataSourceInfoMap() {
        return dataSourceInfoMap;
    }
    private final SendDispatchService sendDispatchService;
    private final GrafanaDataSourceService dataSourceService;
    private final GrafanaMonitorRuleService ruleService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Integer TIME = 30;
    private final Integer DEFAULT_LIMIT = 500;
    private final Map<String, Long> lastTsMap = new HashMap<>();

    private HttpClient httpClient;

    public GrafanaLogServiceImp(SendDispatchService sendDispatchService,
                               GrafanaDataSourceService dataSourceService,
                               GrafanaMonitorRuleService ruleService) {
        this.sendDispatchService = sendDispatchService;
        this.dataSourceService = dataSourceService;
        this.ruleService = ruleService;

        httpClient = HttpClient.create(reactor.netty.resources.ConnectionProvider.builder("grafana-connection-pool")
                        .maxConnections(10)
                        .pendingAcquireTimeout(Duration.ofSeconds(30))
                        .maxIdleTime(Duration.ofSeconds(10))
                        .maxLifeTime(Duration.ofSeconds(30))
                        .evictInBackground(Duration.ofSeconds(8))
                        .build())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 120000)
                .responseTimeout(Duration.ofSeconds(120))
                .keepAlive(true)
                .tcpConfiguration(tcp -> tcp
                        .bootstrap(bootstrap -> bootstrap.option(ChannelOption.SO_KEEPALIVE, true))
                )
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(120, TimeUnit.SECONDS))
                );
    }

    @PostConstruct
    public void init() {
        try {
            refreshConfig();
        } catch (Exception e) {
            logger.warn("初始化配置失败，可能是数据库表尚未创建: {}", e.getMessage());
        }
    }

    public void refreshConfig() {
        synchronized (webClientMap) {
            webClientMap.clear();
            dataSourceInfoMap.clear();

            List<GrafanaDataSource> dataSources = dataSourceService.listEnabled();
            for (GrafanaDataSource ds : dataSources) {
                String basicAuth = Base64Utils.encodeToString(
                        (ds.getUsername() + ":" + ds.getPassword()).getBytes()
                );
                WebClient webClient = WebClient.builder()
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .codecs(config -> config.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .build();
                webClientMap.put(ds.getEnvironmentName(), webClient);

                DataSourceInfo info = new DataSourceInfo();
                info.setId(ds.getId());
                info.setUrl(ds.getUrl());
                info.setDatasourceId(ds.getDatasourceId());
                info.setEnvironmentName(ds.getEnvironmentName());
                info.setWebhook(ds.getWebhook());
                info.setWeek(parseWeek(ds.getWeek()));
                info.setStartTime(parseTime(ds.getStartTime()));
                info.setEndTime(parseTime(ds.getEndTime()));

                List<GrafanaMonitorRule> rules = ruleService.listEnabledByDataSourceId(ds.getId());
                List<MonitorRuleInfo> monitorRules = new ArrayList<>();
                for (GrafanaMonitorRule rule : rules) {
                    MonitorRuleInfo mr = new MonitorRuleInfo();
                    mr.setId(rule.getId());
                    mr.setName(rule.getName());
                    mr.setQueryExpr(rule.getQueryExpr());
                    mr.setKeywords(parseKeywords(rule.getKeywords()));
                    mr.setExclusionKeywords(parseKeywords(rule.getExclusionKeywords()));
                    mr.setContextLines(rule.getContextLines() != null ? rule.getContextLines() : 5);
                    mr.setWebhook(rule.getWebhook());
                    mr.setEnabled(rule.getEnabled() == 1);
                    monitorRules.add(mr);
                }
                info.setMonitors(monitorRules);
                dataSourceInfoMap.put(ds.getEnvironmentName(), info);
            }
            // 从规则表加载 lastTs 到内存
            loadLastTsFromRules();
        }
    }
    private List<Integer> parseWeek(String weekJson) {
        if (weekJson == null || weekJson.isEmpty()) {
            return Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        }
        try {
            return objectMapper.readValue(weekJson, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("解析week配置失败: {}", weekJson, e);
            return Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            logger.warn("解析时间配置失败: {}", timeStr, e);
            return null;
        }
    }

    private List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(keywordsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("解析关键词配置失败: {}", keywordsJson, e);
            return Collections.emptyList();
        }
    }

    public void initLastTsMap(String key, Long lastTs) {
        lastTsMap.put(key, lastTs);
    }

    public void initLastTsMapForRule(Long dataSourceId, Long ruleId, Long lastTs) {
        DataSourceInfo info = dataSourceInfoMap.values().stream()
                .filter(ds -> ds.getId().equals(dataSourceId))
                .findFirst().orElse(null);
        if (info == null) return;
        MonitorRuleInfo rule = info.getMonitors().stream()
                .filter(r -> r.getId() != null && r.getId().equals(ruleId))
                .findFirst().orElse(null);
        if (rule == null) return;
        lastTsMap.put(info.getEnvironmentName() + "_" + rule.getName(), lastTs);
    }

    private void loadLastTsFromRules() {
        List<GrafanaMonitorRule> allRules = ruleService.list();
        for (GrafanaMonitorRule rule : allRules) {
            if (rule.getLastTs() != null && rule.getEnabled() == 1) {
                DataSourceInfo ds = dataSourceInfoMap.values().stream()
                        .filter(d -> d.getId().equals(rule.getDataSourceId()))
                        .findFirst().orElse(null);
                if (ds != null) {
                    lastTsMap.put(ds.getEnvironmentName() + "_" + rule.getName(), rule.getLastTs());
                }
            }
        }
        logger.info("lastTsMap 初始化完成，共 {} 条", lastTsMap.size());
    }

    @Scheduled(initialDelay = 10_000, fixedRate = 30_000)
    public void supplement() {
        // 锁内只做快照，网络调用放到锁外执行，避免长时间占用webClientMap锁
        // 阻塞refreshConfig()（如编辑规则保存时的配置刷新）
        List<Map.Entry<String, WebClient>> snapshot;
        synchronized (webClientMap) {
            snapshot = new ArrayList<>(webClientMap.descendingMap().entrySet());
        }
        for (Map.Entry<String, WebClient> entry : snapshot) {
            String environmentName = entry.getKey();
            //检查数据源是否在线
            GrafanaDataSource dataSource = dataSourceService.getByEnvironmentName(environmentName);
            if (dataSource == null || dataSource.getIsOnline() == null || dataSource.getIsOnline() == 0) {
                logger.debug("数据源 [{}] 离线，跳过日志采集", environmentName);
                continue;
            }

            DataSourceInfo info = dataSourceInfoMap.get(entry.getKey());
            if (info == null || info.getMonitors() == null) {
                continue;
            }
            for (MonitorRuleInfo item : info.getMonitors()) {
                if (!item.isEnabled()) {
                    continue;
                }
                int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
                if (info.getWeek() != null && !info.getWeek().contains(dayOfWeek)) {
                    continue;
                }
                if (info.getStartTime() != null && (LocalTime.now().isBefore(info.getStartTime()) || LocalTime.now().isAfter(info.getEndTime()))) {
                    continue;
                }
                try {
                    processMonitor(item, entry.getValue(), info);
                } catch (Exception e) {
                    logger.error("Monitor {} error", item.getName(), e);
                }
            }
        }
        logger.debug("---------------------------分隔符-------------------------------");
    }

    private void processMonitor(MonitorRuleInfo item, WebClient webClient, DataSourceInfo info) {
        long now = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        // 先从缓存取 lastTs，取不到直接从 DB 查，兜底再用当前时间-30分钟
        Long cachedTs = lastTsMap.get(info.getEnvironmentName() + "_" + item.getName());
        if (cachedTs == null) {
            GrafanaMonitorRule ruleRecord = ruleService.getById(item.getId());
            if (ruleRecord != null && ruleRecord.getLastTs() != null) {
                cachedTs = ruleRecord.getLastTs();
                lastTsMap.put(info.getEnvironmentName() + "_" + item.getName(), cachedTs);
            }
        }
        long globalStart = cachedTs != null ? cachedTs : LocalDateTime.now().minusMinutes(TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        LocalDateTime globalStartTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(globalStart), ZoneId.systemDefault());
        LocalDateTime globalEndTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
        logger.debug("环境：[{}] 微服务：[{}] 开始获取时间区间[{}~{}]内产生的日志进行分析", info.getEnvironmentName(), item.getName(),
                globalStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                globalEndTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        long globalEnd = globalEndTime.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        String baseUrl = info.getUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String dsId = info.getDatasourceId();
        String url = baseUrl + "/api/datasources/proxy/" + dsId + "/loki/api/v1/query_range";

        LocalDateTime currentStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(globalStart), ZoneId.systemDefault());
        LocalDateTime currentEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(globalEnd), ZoneId.systemDefault());

        long sliceStart = globalStart;
        int sliceTotal = 0;
        while (true) {
            Map body = null;
            final int maxRetries = 2;
            for (int retryCount = 0; retryCount <= maxRetries && body == null; retryCount++) {
                final int currentRetry = retryCount;
                try {
                    body = webClient.get()
                            .uri(url + "?direction=forward&query={query}&start={start}&end={end}&limit={limit}",
                                    item.getQueryExpr(), sliceStart * 1_000_000, globalEnd * 1_000_000, DEFAULT_LIMIT)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .onErrorResume(e -> {
                                if (currentRetry < maxRetries) {
                                    logger.warn("{}-{} ⚠️ WebClient 调用 Loki 失败，准备重试 ({}次)", 
                                            info.getEnvironmentName(), item.getName(), currentRetry + 1);
                                } else {
                                    logger.error("{}-{} ❌ WebClient 调用 Loki 失败", info.getEnvironmentName(), item.getName(), e);
                                }
                                return Mono.empty();
                            })
                            .block();
                } catch (Exception e) {
                    if (currentRetry < maxRetries) {
                        logger.warn("{}-{} ⚠️ WebClient 调用异常，准备重试 ({}次)", 
                                info.getEnvironmentName(), item.getName(), currentRetry + 1);
                    } else {
                        logger.error("{}-{} ❌ WebClient 调用异常", info.getEnvironmentName(), item.getName(), e);
                    }
                }
                if (body == null && retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            int batchCount = 0;
            long batchMaxTs = sliceStart;
            List<String> hitLogs = new ArrayList<>();

            if (body != null && !body.isEmpty()) {
                Map data = (Map) body.get("data");
                if (data != null) {
                    List<Map<String, Object>> streams = (List<Map<String, Object>>) data.get("result");
                    if (streams != null) {
                        for (Map<String, Object> stream : streams) {
                            List<List<Object>> values = (List<List<Object>>) stream.get("values");
                            if (values == null || values.isEmpty()) continue;

                            batchCount += values.size();

                            for (List<Object> entry : values) {
                                long ts = Long.parseLong((String) entry.get(0)) / 1_000_000;
                                String log = (String) entry.get(1);
                                if (ts > batchMaxTs) batchMaxTs = ts;

                                if (ts <= lastTsMap.getOrDefault(info.getEnvironmentName() + "_" + item.getName(), globalStart)) {
                                    continue;
                                }

                                if (item.getKeywords().stream().anyMatch(log::contains)) {
                                    int endIdx = Math.min(values.indexOf(entry) + item.getContextLines(), values.size());
                                    List<String> context = values.subList(values.indexOf(entry), endIdx).stream()
                                            .map(v -> (String) v.get(1))
                                            .collect(Collectors.toList());
                                    String waitPushContext = String.join("\n", context);

                                    if (item.getExclusionKeywords().stream().noneMatch(waitPushContext::contains)) {
                                        hitLogs.add(waitPushContext);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            sliceTotal += batchCount;

            if (batchMaxTs > lastTsMap.getOrDefault(info.getEnvironmentName() + "_" + item.getName(), globalStart)) {
                lastTsMap.put(info.getEnvironmentName() + "_" + item.getName(), batchMaxTs);
                ruleService.updateLastTs(item.getId(), info.getEnvironmentName(), info.getId(), batchMaxTs, batchCount);
            }

            if (!hitLogs.isEmpty()) {
                String content = MessageFormat.format("{0}🚨 **检测到异常日志**\n```\n {1} \n```",
                        info.getEnvironmentName(),
                        hitLogs.stream().collect(Collectors.joining("\n---\n")));
                if (content.length() > 1500) {
                    content = content.substring(0, 1500) + "\n...（内容过长已截断）";
                }
                String webhook = item.getWebhook() != null && !item.getWebhook().isEmpty() ? item.getWebhook() : info.getWebhook();
                sendDispatchService.sendSimpleMarkDownMsg(content, info.getEnvironmentName(), webhook);
                logger.info("📩 {} 已推送 {} 条日志，并更新 lastTs={},时间：{} 推送内容：{}", info.getEnvironmentName(), hitLogs.size(), batchMaxTs,
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(batchMaxTs), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), content);
            }

            if (batchCount < DEFAULT_LIMIT) {
                logger.debug("环境：[{}] 微服务：[{}] 时间区间[{}~{}]处理完成，共获取 {} 条日志", info.getEnvironmentName(), item.getName(),
                        currentStartLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        currentEndLdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        sliceTotal);
                break;
            } else {
                logger.debug("环境：[{}] 微服务：[{}] 从[{}~{}]拉取数据满[{}]条，继续从[{}]开始拉取", info.getEnvironmentName(),
                        item.getName(), LocalDateTime.ofInstant(Instant.ofEpochMilli(sliceStart), ZoneId.systemDefault()),
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(globalEnd), ZoneId.systemDefault()), batchCount,
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(batchMaxTs), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                sliceStart = batchMaxTs + 1;
            }
        }
    }

    public static class DataSourceInfo {
        private Long id;
        private String url;
        private String datasourceId;
        private String environmentName;
        private String webhook;
        private List<Integer> week;
        private LocalTime startTime;
        private LocalTime endTime;
        private List<MonitorRuleInfo> monitors;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getDatasourceId() { return datasourceId; }
        public void setDatasourceId(String datasourceId) { this.datasourceId = datasourceId; }
        public String getEnvironmentName() { return environmentName; }
        public void setEnvironmentName(String environmentName) { this.environmentName = environmentName; }
        public String getWebhook() { return webhook; }
        public void setWebhook(String webhook) { this.webhook = webhook; }
        public List<Integer> getWeek() { return week; }
        public void setWeek(List<Integer> week) { this.week = week; }
        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
        public LocalTime getEndTime() { return endTime; }
        public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
        public List<MonitorRuleInfo> getMonitors() { return monitors; }
        public void setMonitors(List<MonitorRuleInfo> monitors) { this.monitors = monitors; }
    }

    public static class MonitorRuleInfo {
        private Long id;
        private String name;
        private String queryExpr;
        private List<String> keywords;
        private List<String> exclusionKeywords;
        private int contextLines = 5;
        private String webhook;
        private boolean enabled = true;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getQueryExpr() { return queryExpr; }
        public void setQueryExpr(String queryExpr) { this.queryExpr = queryExpr; }
        public List<String> getKeywords() { return keywords; }
        public void setKeywords(List<String> keywords) { this.keywords = keywords; }
        public List<String> getExclusionKeywords() { return exclusionKeywords; }
        public void setExclusionKeywords(List<String> exclusionKeywords) { this.exclusionKeywords = exclusionKeywords; }
        public int getContextLines() { return contextLines; }
        public void setContextLines(int contextLines) { this.contextLines = contextLines; }
        public String getWebhook() { return webhook; }
        public void setWebhook(String webhook) { this.webhook = webhook; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
