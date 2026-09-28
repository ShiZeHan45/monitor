package com.szh.monitor.service.impl;

import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GrafanaLogServiceImp 单元测试（基于数据库配置驱动的实现）。
 * 通过 MockWebServer 模拟 Loki query_range 接口，经 refreshConfig()+supplement() 公共链路驱动。
 */
@ExtendWith(MockitoExtension.class)
class GrafanaLogServiceImpTest {

    @Mock
    private SendDispatchService sendDispatchService;
    @Mock
    private GrafanaDataSourceService dataSourceService;
    @Mock
    private GrafanaMonitorRuleService ruleService;

    private GrafanaLogServiceImp service;
    private GrafanaDataSource dataSource;
    private GrafanaMonitorRule rule;
    private MockWebServer mockServer;
    private static final long NOW_MS = System.currentTimeMillis();

    /** Create a log entry whose Loki ms-timestamp corresponds to N seconds ago. */
    private static Map.Entry<Long, String> entry(int secondsAgo, String log) {
        return new AbstractMap.SimpleEntry<>(NOW_MS - secondsAgo * 1000L, log);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        rule = new GrafanaMonitorRule();
        rule.setId(10L);
        rule.setDataSourceId(1L);
        rule.setName("test-app");
        rule.setQueryExpr("{service=\"test\"}");
        rule.setKeywords("[\"ERROR\"]");
        rule.setExclusionKeywords("[\"ignore-this\"]");
        rule.setContextLines(3);
        rule.setEnabled(1);
        rule.setLastTs(NOW_MS - 999_000L);

        dataSource = new GrafanaDataSource();
        dataSource.setId(1L);
        dataSource.setEnvironmentName("test-env");
        dataSource.setUrl(String.format("http://localhost:%s", mockServer.getPort()));
        dataSource.setDatasourceId("1");
        dataSource.setUsername("user");
        dataSource.setPassword("pass");
        dataSource.setWebhook("https://hook.test");
        dataSource.setIsOnline(1);

        lenient().when(dataSourceService.listEnabled()).thenReturn(Collections.singletonList(dataSource));
        lenient().when(ruleService.listEnabledByDataSourceId(1L)).thenReturn(Collections.singletonList(rule));
        lenient().when(ruleService.list()).thenReturn(Collections.singletonList(rule));
        lenient().when(dataSourceService.getByEnvironmentName("test-env")).thenReturn(dataSource);

        service = new GrafanaLogServiceImp(sendDispatchService, dataSourceService, ruleService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    /** Refresh config from mocked services, then run one collection cycle. */
    private void runCycle() {
        service.refreshConfig();
        service.supplement();
    }

    // ==================== refreshConfig ====================

    @Test
    void shouldPopulateDataSourceInfoMapOnRefresh() {
        service.refreshConfig();

        Map<String, GrafanaLogServiceImp.DataSourceInfo> infoMap = service.getDataSourceInfoMap();
        assertEquals(1, infoMap.size());
        assertTrue(infoMap.containsKey("test-env"));

        GrafanaLogServiceImp.DataSourceInfo info = infoMap.get("test-env");
        assertEquals(String.format("http://localhost:%s", mockServer.getPort()), info.getUrl());
        assertEquals(1, info.getMonitors().size());

        GrafanaLogServiceImp.MonitorRuleInfo mr = info.getMonitors().get(0);
        assertEquals("test-app", mr.getName());
        assertEquals("{service=\"test\"}", mr.getQueryExpr());
        assertEquals(Collections.singletonList("ERROR"), mr.getKeywords());
        assertEquals(Collections.singletonList("ignore-this"), mr.getExclusionKeywords());
        assertTrue(mr.isEnabled());
    }

    @Test
    void shouldHandleMultipleEnvironments() {
        GrafanaDataSource ds2 = new GrafanaDataSource();
        ds2.setId(2L);
        ds2.setEnvironmentName("env2");
        ds2.setUrl("http://host2:3000");
        ds2.setDatasourceId("2");
        ds2.setIsOnline(1);
        when(ruleService.listEnabledByDataSourceId(2L)).thenReturn(Collections.emptyList());
        when(dataSourceService.listEnabled()).thenReturn(Arrays.asList(dataSource, ds2));

        service.refreshConfig();
        assertEquals(2, service.getDataSourceInfoMap().size());
    }

    // ==================== supplement() guard clauses ====================

    @Test
    void shouldSkipDisabledMonitor() {
        rule.setEnabled(0);
        runCycle();
        verifyNoInteractions(sendDispatchService);
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    void shouldSkipWhenNotInActiveWeekDays() {
        int today = LocalDate.now().getDayOfWeek().getValue();
        List<Integer> excludeToday = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            if (i != today) excludeToday.add(i);
        }
        dataSource.setWeek(toJson(excludeToday));
        runCycle();
        verifyNoInteractions(sendDispatchService);
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    void shouldSkipWhenBeforeStartTime() {
        LocalTime start = LocalTime.now().plusMinutes(1);
        dataSource.setStartTime(start.toString());
        dataSource.setEndTime(start.plusHours(1).toString());
        runCycle();
        verifyNoInteractions(sendDispatchService);
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    void shouldSkipWhenAfterEndTime() {
        LocalTime end = LocalTime.now().minusMinutes(1);
        dataSource.setEndTime(end.toString());
        dataSource.setStartTime(end.minusHours(1).toString());
        runCycle();
        verifyNoInteractions(sendDispatchService);
        assertEquals(0, mockServer.getRequestCount());
    }

    @Test
    void shouldSkipWhenDataSourceOffline() {
        dataSource.setIsOnline(0);
        runCycle();
        verifyNoInteractions(sendDispatchService);
        assertEquals(0, mockServer.getRequestCount());
    }

    // ==================== keyword matching / push ====================

    @Test
    void shouldDetectKeywordAndSendMessage() {
        enqueueLokiResponse(
                entry(120, "some ERROR happened here"),
                entry(180, "normal log line")
        );
        runCycle();
        verify(sendDispatchService).sendSimpleMarkDownMsg(contains("ERROR"), eq("test-env"), eq("https://hook.test"));
    }

    @Test
    void shouldNotSendWhenNoKeywordMatch() {
        enqueueLokiResponse(
                entry(120, "normal log line"),
                entry(180, "another normal line")
        );
        runCycle();
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    @Test
    void shouldExcludeKeywordMatch() {
        enqueueLokiResponse(
                entry(120, "ERROR but ignore-this should be excluded"),
                entry(180, "normal line")
        );
        runCycle();
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    @Test
    void shouldCaptureContextLines() {
        enqueueLokiResponse(
                entry(60, "ERROR at service layer"),
                entry(61, "context line 1"),
                entry(62, "context line 2"),
                entry(63, "context line 3")
        );
        runCycle();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture(), eq("test-env"), eq("https://hook.test"));
        assertTrue(captor.getValue().contains("context line 1"));
    }

    @Test
    void shouldTruncateLongContent() {
        StringBuilder sb = new StringBuilder("ERROR ");
        for (int i = 0; i < 200; i++) {
            sb.append("very-long-log-message-that-repeats-");
        }
        enqueueLokiResponse(entry(120, sb.toString()));
        runCycle();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture(), eq("test-env"), eq("https://hook.test"));
        assertTrue(captor.getValue().length() <= 1550);
        assertTrue(captor.getValue().contains("内容过长已截断"));
    }

    // ==================== lastTs handling ====================

    @Test
    void shouldSkipLogsBeforeLastTimestamp() {
        rule.setLastTs(NOW_MS + 999_000L);
        enqueueLokiResponse(
                entry(120, "ERROR old log"),
                entry(180, "ERROR old log too")
        );
        runCycle();
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    @Test
    void shouldProcessLogsAfterLastTimestamp() {
        enqueueLokiResponse(entry(120, "ERROR new log"));
        runCycle();
        verify(sendDispatchService).sendSimpleMarkDownMsg(anyString(), eq("test-env"), eq("https://hook.test"));
    }

    @Test
    void shouldUpdateTimestampAfterProcessing() {
        enqueueLokiResponse(entry(60, "normal log"));
        runCycle();
        verify(ruleService).updateLastTs(eq(10L), eq("test-env"), eq(1L), eq(NOW_MS - 60_000L), anyLong());
    }

    // ==================== pagination ====================

    @Test
    void shouldStopPaginationWhenBatchCountLessThanLimit() {
        List<Map.Entry<Long, String>> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(entry(100 + i, "normal log " + i));
        }
        enqueueLokiResponse(logs);
        runCycle();
        assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    void shouldContinuePaginationWhenBatchCountEqualsLimit() {
        List<Map.Entry<Long, String>> batch1 = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            batch1.add(entry(200 + i, "log " + i));
        }
        List<Map.Entry<Long, String>> batch2 = new ArrayList<>();
        batch2.add(entry(100, "no error here"));

        enqueueLokiResponse(batch1);
        enqueueLokiResponse(batch2);
        runCycle();

        assertEquals(2, mockServer.getRequestCount());
    }

    // ==================== error handling ====================

    @Test
    void shouldHandleNullBodyGracefully() {
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));
        assertDoesNotThrow(this::runCycle);
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    @Test
    void shouldHandleEmptyResultGracefully() {
        enqueueLokiResponse(Collections.emptyList());
        assertDoesNotThrow(this::runCycle);
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    @Test
    void shouldCatchExceptionPerMonitorInSupplement() {
        GrafanaMonitorRule failingRule = new GrafanaMonitorRule();
        failingRule.setId(11L);
        failingRule.setDataSourceId(1L);
        failingRule.setName("failing-rule");
        failingRule.setQueryExpr("{service=\"fail\"}");
        failingRule.setKeywords("[\"ERROR\"]");
        failingRule.setEnabled(1);

        when(ruleService.listEnabledByDataSourceId(1L)).thenReturn(Arrays.asList(rule, failingRule));
        when(ruleService.list()).thenReturn(Collections.singletonList(rule));
        when(ruleService.getById(11L)).thenThrow(new RuntimeException("db down"));

        enqueueLokiResponse(entry(120, "normal log"));

        assertDoesNotThrow(() -> {
            service.refreshConfig();
            service.supplement();
        });
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString(), anyString(), anyString());
    }

    // ==================== multiple streams ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldProcessMultipleStreams() {
        Map<String, Object> stream1 = new HashMap<>();
        stream1.put("values", Arrays.asList(
                Arrays.asList(String.valueOf(NOW_MS * 1_000_000L), "ERROR stream1"),
                Arrays.asList(String.valueOf((NOW_MS + 1000L) * 1_000_000L), "stream1 normal")
        ));
        Map<String, Object> stream2 = new HashMap<>();
        stream2.put("values", Arrays.asList(
                Arrays.asList(String.valueOf((NOW_MS + 2000L) * 1_000_000L), "stream2 normal"),
                Arrays.asList(String.valueOf((NOW_MS + 3000L) * 1_000_000L), "ERROR stream2")
        ));
        List<Map<String, Object>> streams = Arrays.asList(stream1, stream2);
        Map<String, Object> data = new HashMap<>();
        data.put("result", streams);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(json(body)));

        runCycle();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture(), eq("test-env"), eq("https://hook.test"));
        assertTrue(captor.getValue().contains("ERROR stream1"));
        assertTrue(captor.getValue().contains("ERROR stream2"));
    }

    // ==================== helpers ====================

    private void enqueueLokiResponse(Map.Entry<Long, String>... entries) {
        enqueueLokiResponse(Arrays.asList(entries));
    }

    private void enqueueLokiResponse(List<Map.Entry<Long, String>> entries) {
        List<Map<String, Object>> resultStreams = new ArrayList<>();
        if (!entries.isEmpty()) {
            List<List<Object>> values = new ArrayList<>();
            for (Map.Entry<Long, String> e : entries) {
                values.add(Arrays.asList(String.valueOf(e.getKey() * 1_000_000L), e.getValue()));
            }
            Map<String, Object> stream = new HashMap<>();
            stream.put("values", values);
            resultStreams.add(stream);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("result", resultStreams);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(json(body)));
    }

    private static String json(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toJson(List<Integer> list) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
