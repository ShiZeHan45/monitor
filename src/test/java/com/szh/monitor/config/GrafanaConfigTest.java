package com.szh.monitor.config;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class GrafanaConfigTest {

    @Test
    void shouldCreateGrafanaInfoWithAllFields() {
        GrafanaConfig.GrafanaInfo info = new GrafanaConfig.GrafanaInfo();
        info.setUrl("http://grafana.example.com:3000");
        info.setEnvironmentName("production");
        info.setDatasourceId("loki-1");
        info.setUsername("admin");
        info.setPassword("secret");
        info.setWebhook("https://qyapi.weixin.qq.com/webhook/xxx");
        info.setWeek(Arrays.asList(1, 2, 3, 4, 5));
        info.setStartTime(LocalTime.of(9, 0));
        info.setEndTime(LocalTime.of(21, 0));

        MonitorRules rule = new MonitorRules();
        rule.setName("test-rule");
        info.setMonitors(Collections.singletonList(rule));

        assertEquals("http://grafana.example.com:3000", info.getUrl());
        assertEquals("production", info.getEnvironmentName());
        assertEquals("loki-1", info.getDatasourceId());
        assertEquals("admin", info.getUsername());
        assertEquals("secret", info.getPassword());
        assertEquals("https://qyapi.weixin.qq.com/webhook/xxx", info.getWebhook());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), info.getWeek());
        assertEquals(LocalTime.of(9, 0), info.getStartTime());
        assertEquals(LocalTime.of(21, 0), info.getEndTime());
        assertEquals(1, info.getMonitors().size());
    }

    @Test
    void shouldAllowNullWeekAndTime() {
        GrafanaConfig.GrafanaInfo info = new GrafanaConfig.GrafanaInfo();
        info.setEnvironmentName("always-on-env");

        assertNull(info.getWeek());
        assertNull(info.getStartTime());
        assertNull(info.getEndTime());
    }

    @Test
    void shouldSupportMultipleMonitors() {
        GrafanaConfig.GrafanaInfo info = new GrafanaConfig.GrafanaInfo();
        MonitorRules rule1 = new MonitorRules();
        rule1.setName("app-a");
        MonitorRules rule2 = new MonitorRules();
        rule2.setName("app-b");
        info.setMonitors(Arrays.asList(rule1, rule2));

        assertEquals(2, info.getMonitors().size());
    }

    @Test
    void shouldHandleEmptyMonitors() {
        GrafanaConfig.GrafanaInfo info = new GrafanaConfig.GrafanaInfo();
        info.setMonitors(Collections.emptyList());

        assertNotNull(info.getMonitors());
        assertTrue(info.getMonitors().isEmpty());
    }

    @Test
    void shouldHoldListOfGrafanaInfo() {
        GrafanaConfig config = new GrafanaConfig();
        GrafanaConfig.GrafanaInfo info1 = new GrafanaConfig.GrafanaInfo();
        info1.setEnvironmentName("env1");
        GrafanaConfig.GrafanaInfo info2 = new GrafanaConfig.GrafanaInfo();
        info2.setEnvironmentName("env2");
        config.setList(Arrays.asList(info1, info2));

        assertEquals(2, config.getList().size());
        assertEquals("env1", config.getList().get(0).getEnvironmentName());
        assertEquals("env2", config.getList().get(1).getEnvironmentName());
    }

    @Test
    void shouldHandleNullList() {
        GrafanaConfig config = new GrafanaConfig();
        assertNull(config.getList());
    }
}
