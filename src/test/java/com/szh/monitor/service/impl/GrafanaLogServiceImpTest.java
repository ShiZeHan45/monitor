package com.szh.monitor.service.impl;

import com.szh.monitor.config.GrafanaConfig;
import com.szh.monitor.config.MonitorRules;
import com.szh.monitor.service.LogCollectTimeInfoService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrafanaLogServiceImpTest {

    @Mock
    private GrafanaConfig grafanaConfig;
    @Mock
    private SendDispatchService sendDispatchService;
    @Mock
    private LogCollectTimeInfoService logCollectTimeInfoService;

    private GrafanaLogServiceImp service;
    private GrafanaConfig.GrafanaInfo grafanaInfo;
    private MonitorRules monitorRule;
    private MockWebServer mockServer;
    private static final long NOW_MS = System.currentTimeMillis();

    /** Return a Loki nanosecond-epoch timestamp for a log N seconds ago. */
    /** Create a Map.Entry with epoch-millis key (used for lastTs checking). */
    private static Map.Entry<Long, String> entry(int secondsAgo, String log) {
        return new AbstractMap.SimpleEntry<>(NOW_MS - secondsAgo * 1000L, log);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        monitorRule = new MonitorRules();
        monitorRule.setName("test-app");
        monitorRule.setQueryExpr("{service=\"test\"}");
        monitorRule.setKeywords(Collections.singletonList("ERROR"));
        monitorRule.setExclusionKeywords(Collections.singletonList("ignore-this"));
        monitorRule.setContextLines(3);
        monitorRule.setEnabled(true);

        grafanaInfo = new GrafanaConfig.GrafanaInfo();
        grafanaInfo.setEnvironmentName("test-env");
        grafanaInfo.setUrl(String.format("http://localhost:%s", mockServer.getPort()));
        grafanaInfo.setDatasourceId("1");
        grafanaInfo.setUsername("user");
        grafanaInfo.setPassword("pass");
        grafanaInfo.setMonitors(Collections.singletonList(monitorRule));

        lenient().when(grafanaConfig.getList()).thenReturn(Collections.singletonList(grafanaInfo));

        service = new GrafanaLogServiceImp(grafanaConfig, sendDispatchService, logCollectTimeInfoService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    // ==================== Constructor tests ====================

    @Test
    void shouldPopulateGrafanaInfoMapOnConstruction() {
        Map<String, GrafanaConfig.GrafanaInfo> infoMap = service.getGrafanaInfoMap();
        assertEquals(1, infoMap.size());
        assertTrue(infoMap.containsKey("test-env"));
    }

    @Test
    void shouldInitLastTsMap() {
        service.initLastTsMap("test-env_test-app", 1000L);
        service.initLastTsMap("test-env_other-app", 2000L);
    }

    @Test
    void shouldHandleMultipleEnvironments() {
        reset(grafanaConfig);
        GrafanaConfig.GrafanaInfo env1 = createGrafanaInfo("env1", "http://host1:3000");
        GrafanaConfig.GrafanaInfo env2 = createGrafanaInfo("env2", "http://host2:3000");
        when(grafanaConfig.getList()).thenReturn(Arrays.asList(env1, env2));

        GrafanaLogServiceImp multiService = new GrafanaLogServiceImp(grafanaConfig, sendDispatchService, logCollectTimeInfoService);
        assertEquals(2, multiService.getGrafanaInfoMap().size());
    }

    // ==================== supplement() filtering tests ====================

    @Test
    void shouldSkipDisabledMonitor() {
        monitorRule.setEnabled(false);
        service.supplement();
        verifyNoInteractions(sendDispatchService);
    }

    @Test
    void shouldSkipWhenNotInActiveWeekDays() {
        int today = java.time.LocalDate.now().getDayOfWeek().getValue();
        List<Integer> excludeToday = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            if (i != today) excludeToday.add(i);
        }
        grafanaInfo.setWeek(excludeToday);
        service.supplement();
        verifyNoInteractions(sendDispatchService);
    }

    @Test
    void shouldSkipWhenBeforeStartTime() {
        grafanaInfo.setStartTime(LocalTime.of(23, 0));
        grafanaInfo.setEndTime(LocalTime.of(23, 59));
        service.supplement();
        verifyNoInteractions(sendDispatchService);
    }

    @Test
    void shouldSkipWhenAfterEndTime() {
        grafanaInfo.setStartTime(LocalTime.of(0, 0));
        grafanaInfo.setEndTime(LocalTime.of(0, 1));
        service.supplement();
        verifyNoInteractions(sendDispatchService);
    }

    // ==================== processMonitor - keyword matching ====================

    @Test
    void shouldDetectKeywordAndSendMessage() throws Exception {
        enqueueLokiResponse(
                entry(120, "some ERROR happened here"),
                entry(180, "normal log line")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService).sendSimpleMarkDownMsg(contains("ERROR"));
    }

    @Test
    void shouldNotSendWhenNoKeywordMatch() throws Exception {
        enqueueLokiResponse(
                entry(120, "normal log line"),
                entry(180, "another normal line")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldExcludeKeywordMatch() throws Exception {
        enqueueLokiResponse(
                entry(120, "ERROR but ignore-this should be excluded"),
                entry(180, "normal line")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldCaptureContextLines() throws Exception {
        monitorRule.setContextLines(3);
        monitorRule.setExclusionKeywords(Collections.emptyList());

        enqueueLokiResponse(
                entry(60, "ERROR at service layer"),
                entry(61, "context line 1"),
                entry(62, "context line 2"),
                entry(63, "context line 3")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture());
        assertTrue(captor.getValue().contains("context line 1"));
    }

    @Test
    void shouldTruncateLongContent() throws Exception {
        monitorRule.setExclusionKeywords(Collections.emptyList());
        StringBuilder sb = new StringBuilder("ERROR ");
        for (int i = 0; i < 200; i++) {
            sb.append("very-long-log-message-that-repeats-");
        }

        enqueueLokiResponse(entry(120, sb.toString()));

        invokeProcessMonitor(grafanaInfo, monitorRule);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture());
        assertTrue(captor.getValue().length() <= 1550);
        assertTrue(captor.getValue().contains("内容过长已截断"));
    }

    // ==================== processMonitor - timestamp handling ====================

    @Test
    void shouldSkipLogsBeforeLastTimestamp() throws Exception {
        service.initLastTsMap("test-env_test-app", NOW_MS + 999_000L);

        enqueueLokiResponse(
                entry(120, "ERROR old log"),
                entry(180, "ERROR old log too")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldProcessLogsAfterLastTimestamp() throws Exception {
        service.initLastTsMap("test-env_test-app", NOW_MS - 999_000L);

        enqueueLokiResponse(
                entry(120, "ERROR new log"),
                entry(180, "normal log")
        );

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldUpdateTimestampAfterProcessing() throws Exception {
        long expectedTs = NOW_MS - 60_000L;
        service.initLastTsMap("test-env_test-app", NOW_MS - 999_000L);

        enqueueLokiResponse(entry(60, "normal log"));

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(logCollectTimeInfoService).updateOrSave(eq("test-env"), eq("test-app"), anyLong());
    }

    // ==================== processMonitor - pagination ====================

    @Test
    void shouldStopPaginationWhenBatchCountLessThanLimit() throws Exception {
        List<Map.Entry<Long, String>> logs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            logs.add(entry(100 + i, "normal log " + i));
        }
        enqueueLokiResponse(logs);

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldContinuePaginationWhenBatchCountEqualsLimit() throws Exception {
        List<Map.Entry<Long, String>> batch1 = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            batch1.add(entry(200 + i, "log " + i));
        }
        List<Map.Entry<Long, String>> batch2 = new ArrayList<>();
        batch2.add(entry(100, "no error here"));

        enqueueLokiResponse(batch1);
        enqueueLokiResponse(batch2);

        invokeProcessMonitor(grafanaInfo, monitorRule);

        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    // ==================== processMonitor - error handling ====================

    @Test
    void shouldHandleNullBodyGracefully() throws Exception {
        enqueueEmptyResponse();

        assertDoesNotThrow(() -> invokeProcessMonitor(grafanaInfo, monitorRule));
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldHandleEmptyResultGracefully() throws Exception {
        enqueueLokiResponse(Collections.emptyList());

        assertDoesNotThrow(() -> invokeProcessMonitor(grafanaInfo, monitorRule));
        verify(sendDispatchService, never()).sendSimpleMarkDownMsg(anyString());
    }

    @Test
    void shouldCatchExceptionPerMonitorInSupplement() {
        monitorRule.setEnabled(true);
        grafanaInfo.setWeek(null);
        grafanaInfo.setStartTime(null);
        grafanaInfo.setEndTime(null);
        MonitorRules rule2 = new MonitorRules();
        rule2.setName("failing-rule");
        rule2.setQueryExpr("{service=\"fail\"}");
        rule2.setKeywords(Collections.singletonList("ERROR"));
        rule2.setEnabled(true);
        grafanaInfo.setMonitors(Arrays.asList(monitorRule, rule2));

        assertDoesNotThrow(() -> service.supplement());
    }

    // ==================== processMonitor - multiple streams ====================

    @Test
    @SuppressWarnings("unchecked")
    void shouldProcessMultipleStreams() throws Exception {
        monitorRule.setExclusionKeywords(Collections.emptyList());

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

        enqueueResponse(json(body));

        invokeProcessMonitor(grafanaInfo, monitorRule);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sendDispatchService).sendSimpleMarkDownMsg(captor.capture());
        assertTrue(captor.getValue().contains("ERROR stream1"));
        assertTrue(captor.getValue().contains("ERROR stream2"));
    }

    // ==================== Helper methods ====================

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
        enqueueResponse(json(buildLokiResponseBody(resultStreams)));
    }

    private void enqueueEmptyResponse() {
        enqueueResponse("{}");
    }

    private void enqueueResponse(String body) {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildLokiResponseBody(List<Map<String, Object>> streams) {
        Map<String, Object> data = new HashMap<>();
        data.put("result", streams);
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static String json(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeProcessMonitor(GrafanaConfig.GrafanaInfo info, MonitorRules rule) throws Exception {
        Method method = GrafanaLogServiceImp.class.getDeclaredMethod(
                "processMonitor", MonitorRules.class, WebClient.class, GrafanaConfig.GrafanaInfo.class);
        method.setAccessible(true);

        Field webClientField = GrafanaLogServiceImp.class.getDeclaredField("webClientMap");
        webClientField.setAccessible(true);
        @SuppressWarnings("unchecked")
        TreeMap<String, WebClient> clientMap = (TreeMap<String, WebClient>) webClientField.get(service);
        WebClient client = clientMap.get(info.getEnvironmentName());

        method.invoke(service, rule, client, info);
    }

    private GrafanaConfig.GrafanaInfo createGrafanaInfo(String name, String url) {
        GrafanaConfig.GrafanaInfo info = new GrafanaConfig.GrafanaInfo();
        info.setEnvironmentName(name);
        info.setUrl(url);
        info.setDatasourceId("1");
        info.setUsername("u");
        info.setPassword("p");
        info.setMonitors(Collections.emptyList());
        return info;
    }
}
