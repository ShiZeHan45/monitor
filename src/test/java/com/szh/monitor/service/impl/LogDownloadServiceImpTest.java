package com.szh.monitor.service.impl;

import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.form.LogDownloadRequest;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import com.szh.monitor.vo.LogDownloadTask;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * LogDownloadServiceImp 单元测试（TDD 先行）。
 * 纯逻辑（选择器截取/关键词过滤/游标分页/上限截断）通过包内可见方法直接验证；
 * 任务生命周期通过 MockWebServer 模拟 Loki 走 submitTask 异步链路验证。
 */
@ExtendWith(MockitoExtension.class)
class LogDownloadServiceImpTest {

    private static final long NOW_MS = System.currentTimeMillis();
    private static final DateTimeFormatter REQ_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Mock
    private GrafanaDataSourceService dataSourceService;
    @Mock
    private GrafanaMonitorRuleService ruleService;

    private LogDownloadServiceImp service;
    private GrafanaDataSource dataSource;
    private GrafanaMonitorRule rule;
    private MockWebServer mockServer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        rule = new GrafanaMonitorRule();
        rule.setId(10L);
        rule.setDataSourceId(1L);
        rule.setName("test-app");
        rule.setQueryExpr("{job=\"BOSS-BCS\"} |= \"ERROR\"");
        rule.setKeywords("[\"ERROR\"]");
        rule.setEnabled(1);

        dataSource = new GrafanaDataSource();
        dataSource.setId(1L);
        dataSource.setEnvironmentName("test-env");
        dataSource.setUrl(String.format("http://localhost:%s", mockServer.getPort()));
        dataSource.setDatasourceId("1");
        dataSource.setUsername("user");
        dataSource.setPassword("pass");
        dataSource.setIsOnline(1);

        org.mockito.Mockito.lenient().when(dataSourceService.getByEnvironmentName("test-env")).thenReturn(dataSource);
        org.mockito.Mockito.lenient().when(ruleService.listByDataSourceId(1L)).thenReturn(Collections.singletonList(rule));

        service = new LogDownloadServiceImp(dataSourceService, ruleService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ==================== 选择器截取 ====================

    @Test
    void extractSelectorShouldKeepOnlyLabelSelector() {
        assertEquals("{job=\"BOSS-BCS\"}", LogDownloadServiceImp.extractSelector("{job=\"BOSS-BCS\"} |= \"ERROR\""));
        assertEquals("{app=\"a\"}", LogDownloadServiceImp.extractSelector("{app=\"a\"} |~ \"err\" | json"));
        assertEquals("{job=\"x\"}", LogDownloadServiceImp.extractSelector("  {job=\"x\"}  |= \"E\"  "));
    }

    @Test
    void extractSelectorShouldReturnWholeExprWhenNoPipeline() {
        assertEquals("{job=\"BOSS-BCS\"}", LogDownloadServiceImp.extractSelector("{job=\"BOSS-BCS\"}"));
        assertEquals("{job=\"a\",level=\"b\"}", LogDownloadServiceImp.extractSelector("{job=\"a\",level=\"b\"}"));
    }

    @Test
    void extractSelectorShouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> LogDownloadServiceImp.extractSelector(null));
        assertThrows(IllegalArgumentException.class, () -> LogDownloadServiceImp.extractSelector("   "));
        assertThrows(IllegalArgumentException.class, () -> LogDownloadServiceImp.extractSelector("|= \"ERROR\""));
    }

    // ==================== 关键词解析与过滤 ====================

    @Test
    void splitKeywordsShouldHandleNullEmptyAndTrim() {
        assertTrue(LogDownloadServiceImp.splitKeywords(null).isEmpty());
        assertTrue(LogDownloadServiceImp.splitKeywords("").isEmpty());
        assertEquals(Arrays.asList("a", "b"), LogDownloadServiceImp.splitKeywords("a,b"));
        assertEquals(Arrays.asList("a", "b"), LogDownloadServiceImp.splitKeywords(" a , b "));
        assertEquals(Arrays.asList("a", "b"), LogDownloadServiceImp.splitKeywords("a,,b"));
    }

    @Test
    void matchesFilterShouldApplyIncludeAndExcludeRules() {
        assertTrue(LogDownloadServiceImp.matchesFilter("any line", Collections.emptyList(), Collections.emptyList()));
        assertTrue(LogDownloadServiceImp.matchesFilter("xx ERROR yy", Arrays.asList("ERROR"), Collections.emptyList()));
        assertFalse(LogDownloadServiceImp.matchesFilter("xx yy", Arrays.asList("ERROR"), Collections.emptyList()));
        assertTrue(LogDownloadServiceImp.matchesFilter("xx Timeout yy", Arrays.asList("ERROR", "Timeout"), Collections.emptyList()));
        // 排除命中 → 丢弃，即使包含命中
        assertFalse(LogDownloadServiceImp.matchesFilter("xx ERROR yy", Arrays.asList("ERROR"), Arrays.asList("xx")));
        // 无排除命中 → 保留
        assertTrue(LogDownloadServiceImp.matchesFilter("xx ERROR yy", Arrays.asList("ERROR"), Arrays.asList("zzz")));
        // 只有排除条件
        assertFalse(LogDownloadServiceImp.matchesFilter("xx ERROR yy", Collections.emptyList(), Arrays.asList("ERROR")));
        assertTrue(LogDownloadServiceImp.matchesFilter("xx yy", Collections.emptyList(), Arrays.asList("ERROR")));
    }

    @Test
    void formatLineShouldUseExpectedPattern() {
        LocalDateTime ldt = LocalDateTime.of(2026, 9, 28, 12, 0, 0, 123_000_000);
        long ts = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals("2026-09-28 12:00:00.123 [order-service] hello world",
                LogDownloadServiceImp.formatLine(ts, "order-service", "hello world"));
    }

    // ==================== writeBatches：分页/过滤/上限 ====================

    /** 可记录调用参数并按顺序返回预设响应体的假 fetcher。 */
    private static class RecordingFetcher implements LogDownloadServiceImp.BatchFetcher {
        final List<Map<String, Object>> bodies;
        final List<Long> startArgs = new ArrayList<>();
        int calls = 0;

        RecordingFetcher(List<Map<String, Object>> bodies) {
            this.bodies = bodies;
        }

        @Override
        public Object fetch(long startMs, long endMs) {
            startArgs.add(startMs);
            if (calls < bodies.size()) {
                return bodies.get(calls++);
            }
            return emptyBody();
        }
    }

    @Test
    void writeBatchesShouldWriteAllLinesSortedAndStopOnPartialBatch() {
        // 乱序输入：50 秒前在前，10 秒前在后 → 写出必须按时间升序
        Map<String, Object> body = lokiBody(new Object[][]{
                {NOW_MS - 50_000, "older line"},
                {NOW_MS - 10_000, "newer line"},
        });
        RecordingFetcher fetcher = new RecordingFetcher(Collections.singletonList(body));
        StringWriter sw = new StringWriter();
        LogDownloadTask task = new LogDownloadTask();

        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS,
                Collections.emptyList(), Collections.emptyList(), "svc",
                new PrintWriter(sw), task, Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(2, lines);
        assertEquals(1, fetcher.startArgs.size());
        assertEquals(2, task.getProcessedLines());
        String[] out = sw.toString().split("\n");
        assertEquals(2, out.length);
        assertTrue(out[0].endsWith("[svc] older line"), "older line should come first");
        assertTrue(out[1].endsWith("[svc] newer line"));
    }

    @Test
    void writeBatchesShouldAdvanceCursorToBatchMaxPlusOne() {
        // 第一批 1000 条（满页），最大 ts = NOW-1ms → 第二次游标必须是 NOW
        Object[][] batch1 = new Object[1000][];
        for (int i = 0; i < 1000; i++) {
            batch1[i] = new Object[]{NOW_MS - 1000 + i, "line-1-" + i};
        }
        Object[][] batch2 = new Object[100][];
        for (int i = 0; i < 100; i++) {
            batch2[i] = new Object[]{NOW_MS + i, "line-2-" + i};
        }
        RecordingFetcher fetcher = new RecordingFetcher(Arrays.asList(
                lokiBody(batch1), lokiBody(batch2)));
        StringWriter sw = new StringWriter();
        LogDownloadTask task = new LogDownloadTask();

        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS + 60_000,
                Collections.emptyList(), Collections.emptyList(), "svc",
                new PrintWriter(sw), task, Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(1100, lines);
        assertEquals(2, fetcher.startArgs.size());
        assertEquals(NOW_MS, fetcher.startArgs.get(1).longValue());
        String content = sw.toString();
        assertTrue(content.contains("line-1-999"));
        assertTrue(content.contains("line-2-99"));
    }

    @Test
    void writeBatchesShouldNotLoopWhenCursorDoesNotAdvance() {
        // fetcher 永远返回同一批满页数据 → 必须终止且不重复写
        Object[][] batch = new Object[1000][];
        for (int i = 0; i < 1000; i++) {
            batch[i] = new Object[]{NOW_MS - 1000 + i, "line-" + i};
        }
        Map<String, Object> body = lokiBody(batch);
        RecordingFetcher fetcher = new RecordingFetcher(Collections.emptyList()) {
            @Override
            public Object fetch(long startMs, long endMs) {
                startArgs.add(startMs);
                calls++;
                return body;
            }
        };
        StringWriter sw = new StringWriter();
        LogDownloadTask task = new LogDownloadTask();

        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS + 60_000,
                Collections.emptyList(), Collections.emptyList(), "svc",
                new PrintWriter(sw), task, Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(1000, lines, "相同批次不应被重复写入");
        assertTrue(fetcher.startArgs.size() <= 3, "游标不前进时应尽快终止");
    }

    @Test
    void writeBatchesShouldApplyKeywordFilters() {
        Object[][] entries = new Object[][]{
                {NOW_MS - 30_000, "ERROR one"},
                {NOW_MS - 20_000, "normal two"},
                {NOW_MS - 10_000, "ERROR three"},
                {NOW_MS - 5_000, "skip-me four"},
        };
        RecordingFetcher fetcher = new RecordingFetcher(Collections.singletonList(lokiBody(entries)));
        StringWriter sw = new StringWriter();

        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS,
                Arrays.asList("ERROR"), Arrays.asList("skip-me"), "svc",
                new PrintWriter(sw), new LogDownloadTask(), Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(2, lines);
        String content = sw.toString();
        assertTrue(content.contains("[svc] ERROR one"));
        assertTrue(content.contains("[svc] ERROR three"));
        assertFalse(content.contains("normal two"));
        assertFalse(content.contains("skip-me four"));
    }

    @Test
    void writeBatchesShouldTruncateAtMaxLines() {
        Object[][] entries = new Object[5][];
        for (int i = 0; i < 5; i++) {
            entries[i] = new Object[]{NOW_MS - 50_000 + i * 1000, "ERROR line-" + i};
        }
        RecordingFetcher fetcher = new RecordingFetcher(Collections.singletonList(lokiBody(entries)));
        StringWriter sw = new StringWriter();
        LogDownloadTask task = new LogDownloadTask();

        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS,
                Collections.emptyList(), Collections.emptyList(), "svc",
                new PrintWriter(sw), task, 3, Long.MAX_VALUE);

        assertEquals(3, lines);
        assertTrue(task.isTruncated());
        assertEquals(3, sw.toString().split("\n").length);
    }

    @Test
    void writeBatchesShouldTruncateAtMaxBytes() {
        String firstLine = LogDownloadServiceImp.formatLine(NOW_MS - 10_000, "svc", "a");
        long oneLineBytes = firstLine.getBytes(StandardCharsets.UTF_8).length + 1;
        Object[][] entries = new Object[][]{
                {NOW_MS - 10_000, "a"},
                {NOW_MS - 9_000, "b"},
                {NOW_MS - 8_000, "c"},
        };
        RecordingFetcher fetcher = new RecordingFetcher(Collections.singletonList(lokiBody(entries)));
        StringWriter sw = new StringWriter();
        LogDownloadTask task = new LogDownloadTask();

        // 只够写一行
        long lines = service.writeBatches(fetcher, NOW_MS - 60_000, NOW_MS,
                Collections.emptyList(), Collections.emptyList(), "svc",
                new PrintWriter(sw), task, Long.MAX_VALUE, oneLineBytes);

        assertEquals(1, lines);
        assertTrue(task.isTruncated());
    }

    @Test
    void writeBatchesShouldHandleMalformedBodies() {
        for (Object bad : Arrays.asList(null, new HashMap<>(), "{\"data\":{}}", "{\"data\":{\"result\":null}}")) {
            // 直接构造返回坏数据
            LogDownloadServiceImp.BatchFetcher badFetcher = (s, e) -> bad;
            StringWriter sw = new StringWriter();
            LogDownloadTask task = new LogDownloadTask();
            long lines = service.writeBatches(badFetcher, NOW_MS - 60_000, NOW_MS,
                    Collections.emptyList(), Collections.emptyList(), "svc",
                    new PrintWriter(sw), task, Long.MAX_VALUE, Long.MAX_VALUE);
            assertEquals(0, lines, "坏响应体应被当作空批次处理: " + bad);
            assertEquals("", sw.toString());
            assertFalse(task.isTruncated());
        }
    }

    // ==================== 服务列表 / 参数校验 ====================

    @Test
    void listServicesShouldReturnRuleNamesOfEnvironment() {
        GrafanaMonitorRule rule2 = new GrafanaMonitorRule();
        rule2.setId(11L);
        rule2.setDataSourceId(1L);
        rule2.setName("other-app");
        when(ruleService.listByDataSourceId(1L)).thenReturn(Arrays.asList(rule, rule2));

        assertEquals(Arrays.asList("test-app", "other-app"), service.listServices("test-env"));
        assertTrue(service.listServices("no-such-env").isEmpty());
    }

    @Test
    void submitTaskShouldRejectInvalidRequests() {
        String start = formatTime(NOW_MS - 600_000);
        String end = formatTime(NOW_MS);

        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request(null, "test-app", start, end, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request("test-env", null, start, end, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request("no-such-env", "test-app", start, end, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request("test-env", "no-such-service", start, end, null, null)));
        // 时间格式错误
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request("test-env", "test-app", "2026-09-28 09:00", end, null, null)));
        // 开始时间必须早于结束时间
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(request("test-env", "test-app", end, end, null, null)));

        assertNull(service.getTask("no-such-task"));
    }

    @Test
    void previewShouldRejectInvalidRequests() {
        String start = formatTime(NOW_MS - 600_000);
        String end = formatTime(NOW_MS);
        assertThrows(IllegalArgumentException.class,
                () -> service.preview(request("test-env", "no-such-service", start, end, null, null)));
    }

    // ==================== 任务生命周期（MockWebServer 模拟 Loki） ====================

    @Test
    void submitTaskShouldCompleteAndWriteFile() throws Exception {
        // 第一批 1000 条（满页），第二批 100 条（不足页 → 结束）
        Object[][] batch1 = new Object[1000][];
        for (int i = 0; i < 1000; i++) {
            batch1[i] = new Object[]{NOW_MS - 1000 + i, "line-1-" + i};
        }
        Object[][] batch2 = new Object[100][];
        for (int i = 0; i < 100; i++) {
            batch2[i] = new Object[]{NOW_MS + i, "line-2-" + i};
        }
        mockServer.enqueue(lokiResponse(batch1));
        mockServer.enqueue(lokiResponse(batch2));

        String taskId = service.submitTask(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS + 60_000), null, null));
        awaitStatus(taskId, LogDownloadTask.STATUS_SUCCESS, 15_000);

        LogDownloadTask task = service.getTask(taskId);
        assertEquals(LogDownloadTask.STATUS_SUCCESS, task.getStatus());
        assertEquals(1100, task.getProcessedLines());
        assertFalse(task.isTruncated());
        assertNull(task.getError());
        assertTrue(Pattern.matches("test-app_\\d{8}_\\d{6}\\.log", task.getFileName()));

        File file = new File(task.getFilePath());
        assertTrue(file.exists(), "下载文件应已生成");
        List<String> allLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        long bodyLines = allLines.stream().filter(l -> !l.startsWith("#")).count();
        assertEquals(1100, bodyLines);
        String header = String.join("\n", allLines.stream().filter(l -> l.startsWith("#")).toArray(String[]::new));
        assertTrue(header.contains("test-env"));
        assertTrue(header.contains("test-app"));
        String content = String.join("\n", allLines);
        assertTrue(content.contains("[test-app] line-1-999"));
        assertTrue(content.contains("[test-app] line-2-99"));

        // 校验 Loki 请求格式：走 datasource proxy、forward、limit=1000、Basic 认证
        assertEquals(2, mockServer.getRequestCount());
        RecordedRequest first = mockServer.takeRequest();
        assertTrue(first.getPath().contains("/api/datasources/proxy/1/loki/api/v1/query_range"));
        assertTrue(first.getPath().contains("direction=forward"));
        assertTrue(first.getPath().contains("limit=1000"));
        assertNotNull(first.getHeader("Authorization"));
        assertTrue(first.getHeader("Authorization").startsWith("Basic "));
        RecordedRequest second = mockServer.takeRequest();
        // 第二次游标应从第一批最大 ts + 1ms 开始
        assertTrue(second.getPath().contains("start=" + NOW_MS * 1_000_000));
        assertTrue(file.delete());
    }

    @Test
    void submitTaskShouldFailWhenLokiUnavailable() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        String taskId = service.submitTask(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        awaitStatus(taskId, LogDownloadTask.STATUS_FAILED, 15_000);

        LogDownloadTask task = service.getTask(taskId);
        assertEquals(LogDownloadTask.STATUS_FAILED, task.getStatus());
        assertNotNull(task.getError());
        assertNull(task.getFilePath());
    }

    @Test
    void tasksShouldQueueWhenPoolIsFull() throws Exception {
        // 每个任务一次慢响应，占住 2 个工作线程，第三个任务应排队
        for (int i = 0; i < 3; i++) {
            mockServer.enqueue(lokiResponseWithDelay(new Object[][]{{NOW_MS - 1000, "log-" + i}}, 2000));
        }
        String t1 = service.submitTask(request("test-env", "test-app", formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        String t2 = service.submitTask(request("test-env", "test-app", formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        String t3 = service.submitTask(request("test-env", "test-app", formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));

        awaitStatus(t1, LogDownloadTask.STATUS_RUNNING, 5_000);
        awaitStatus(t2, LogDownloadTask.STATUS_RUNNING, 5_000);
        assertEquals(LogDownloadTask.STATUS_QUEUED, service.getTask(t3).getStatus(), "并发满 2 时第三个任务应排队");

        awaitStatus(t3, LogDownloadTask.STATUS_SUCCESS, 30_000);
        assertEquals(LogDownloadTask.STATUS_SUCCESS, service.getTask(t1).getStatus());
        assertEquals(LogDownloadTask.STATUS_SUCCESS, service.getTask(t2).getStatus());
    }

    @Test
    void previewShouldReturnFirst100FormattedLines() throws Exception {
        Object[][] entries = new Object[200][];
        for (int i = 0; i < 200; i++) {
            entries[i] = new Object[]{NOW_MS - 100_000 + i * 100, "log-" + i};
        }
        mockServer.enqueue(lokiResponse(entries));

        List<String> lines = service.preview(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));

        assertEquals(100, lines.size());
        assertTrue(lines.get(0).endsWith("[test-app] log-0"));
        assertTrue(lines.get(99).endsWith("[test-app] log-99"));
    }

    @Test
    void previewShouldApplyIncludeKeywords() throws Exception {
        Object[][] entries = new Object[200][];
        for (int i = 0; i < 200; i++) {
            entries[i] = new Object[]{NOW_MS - 100_000 + i * 100,
                    i % 2 == 0 ? "ERROR log-" + i : "normal log-" + i};
        }
        mockServer.enqueue(lokiResponse(entries));

        List<String> lines = service.preview(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS), "ERROR", null));

        assertEquals(100, lines.size());
        assertTrue(lines.stream().allMatch(l -> l.contains("ERROR")));
    }

    // ==================== 任务列表 / 手动删除 ====================

    @Test
    void listTasksShouldReturnAllTasksSortedByCreateTimeDesc() throws Exception {
        // 两个慢任务提交后立即查询（不等待完成），验证按提交时间倒序
        for (int i = 0; i < 2; i++) {
            mockServer.enqueue(lokiResponseWithDelay(new Object[][]{{NOW_MS - 1000, "log-" + i}}, 1500));
        }
        String t1 = service.submitTask(request("test-env", "test-app", formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        Thread.sleep(10);
        String t2 = service.submitTask(request("test-env", "test-app", formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));

        List<LogDownloadTask> list = service.listTasks();
        assertEquals(2, list.size());
        assertEquals(t2, list.get(0).getTaskId(), "最新提交的任务应排在最前");
        assertEquals(t1, list.get(1).getTaskId());
    }

    @Test
    void deleteTaskShouldRemoveTaskAndFile() throws Exception {
        mockServer.enqueue(lokiResponse(new Object[][]{{NOW_MS - 1000, "hello"}}));
        String taskId = service.submitTask(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        awaitStatus(taskId, LogDownloadTask.STATUS_SUCCESS, 15_000);

        LogDownloadTask task = service.getTask(taskId);
        File file = new File(task.getFilePath());
        assertTrue(file.exists());
        assertTrue(service.listTasks().stream().anyMatch(t -> taskId.equals(t.getTaskId())));

        service.deleteTask(taskId);

        assertFalse(file.exists(), "删除任务时应同时删除文件");
        assertNull(service.getTask(taskId));
        assertFalse(service.listTasks().stream().anyMatch(t -> taskId.equals(t.getTaskId())));
    }

    @Test
    void deleteTaskShouldRejectUnknownTask() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteTask("no-such-task"));
    }

    @Test
    void finishedFilesShouldNotBeAutoDeleted() throws Exception {
        // 完成超过 30 分钟的文件也必须保留，由用户手动删除
        mockServer.enqueue(lokiResponse(new Object[][]{{NOW_MS - 1000, "hello"}}));
        String taskId = service.submitTask(request("test-env", "test-app",
                formatTime(NOW_MS - 600_000), formatTime(NOW_MS), null, null));
        awaitStatus(taskId, LogDownloadTask.STATUS_SUCCESS, 15_000);

        LogDownloadTask task = service.getTask(taskId);
        File file = new File(task.getFilePath());
        assertTrue(file.exists());

        // 模拟 31 分钟前完成
        task.setFinishTime(System.currentTimeMillis() - 31 * 60 * 1000L);

        assertTrue(file.exists(), "文件不应被自动清理");
        assertEquals(LogDownloadTask.STATUS_SUCCESS, task.getStatus());
        assertEquals(taskId, service.getTask(taskId).getTaskId());
        assertTrue(file.delete());
    }

    @Test
    void constructorShouldSweepOrphanFilesOlderThan7Days() throws Exception {
        File dir = tempDir.toFile();
        File oldFile = new File(dir, "old.log");
        assertTrue(oldFile.createNewFile());
        assertTrue(oldFile.setLastModified(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000));
        File freshFile = new File(dir, "fresh.log");
        assertTrue(freshFile.createNewFile());

        System.setProperty("log.download.dir", dir.getAbsolutePath());
        try {
            new LogDownloadServiceImp(dataSourceService, ruleService);
        } finally {
            System.clearProperty("log.download.dir");
        }

        assertFalse(oldFile.exists(), "7 天以上的孤儿文件应被启动清扫删除");
        assertTrue(freshFile.exists(), "7 天以内的文件不应被清理");
        assertTrue(freshFile.delete());
    }

    // ==================== helpers ====================

    private static String formatTime(long ms) {
        return REQ_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()));
    }

    private static LogDownloadRequest request(String env, String service, String start, String end,
                                               String includeKeywords, String excludeKeywords) {
        LogDownloadRequest r = new LogDownloadRequest();
        r.setEnv(env);
        r.setService(service);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setIncludeKeywords(includeKeywords);
        r.setExcludeKeywords(excludeKeywords);
        return r;
    }

    /** 构造 Loki query_range 响应体：{data:{result:[{values:[[ns,line],...]}]}} */
    private static Map<String, Object> lokiBody(Object[][] entries) {
        List<List<Object>> values = new ArrayList<>();
        if (entries != null) {
            for (Object[] e : entries) {
                values.add(Arrays.asList(String.valueOf(((Long) e[0]) * 1_000_000L), (String) e[1]));
            }
        }
        Map<String, Object> stream = new HashMap<>();
        stream.put("values", values);
        Map<String, Object> data = new HashMap<>();
        data.put("result", Collections.singletonList(stream));
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return body;
    }

    private static Map<String, Object> emptyBody() {
        Map<String, Object> data = new HashMap<>();
        data.put("result", Collections.emptyList());
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return body;
    }

    private static MockResponse lokiResponse(Object[][] entries) {
        return lokiResponseWithDelay(entries, 0);
    }

    private static MockResponse lokiResponseWithDelay(Object[][] entries, long delayMs) {
        MockResponse resp = new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json(lokiBody(entries)));
        if (delayMs > 0) {
            resp.setHeadersDelay(delayMs, TimeUnit.MILLISECONDS);
        }
        return resp;
    }

    private static String json(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void awaitStatus(String taskId, String expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            LogDownloadTask t = service.getTask(taskId);
            if (t != null && expected.equals(t.getStatus())) {
                return;
            }
            Thread.sleep(50);
        }
        LogDownloadTask t = service.getTask(taskId);
        fail("任务未在超时内达到状态 " + expected + "，当前: "
                + (t == null ? "null" : t.getStatus() + " / " + t.getError()));
    }
}
