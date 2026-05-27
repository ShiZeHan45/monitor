package com.szh.monitor.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MonitorRulesTest {

    @Test
    void shouldDefaultEnabledToTrue() {
        MonitorRules rule = new MonitorRules();
        assertTrue(rule.isEnabled());
    }

    @Test
    void shouldDefaultContextLinesTo5() {
        MonitorRules rule = new MonitorRules();
        assertEquals(5, rule.getContextLines());
    }

    @Test
    void shouldSetAndGetName() {
        MonitorRules rule = new MonitorRules();
        rule.setName("error-monitor");
        assertEquals("error-monitor", rule.getName());
    }

    @Test
    void shouldSetAndGetQueryExpr() {
        MonitorRules rule = new MonitorRules();
        rule.setQueryExpr("{service=\"boss-bcs\"}");
        assertEquals("{service=\"boss-bcs\"}", rule.getQueryExpr());
    }

    @Test
    void shouldSetAndGetKeywords() {
        MonitorRules rule = new MonitorRules();
        rule.setKeywords(Arrays.asList("ERROR", "Exception", "Failed"));
        assertEquals(3, rule.getKeywords().size());
        assertTrue(rule.getKeywords().contains("ERROR"));
    }

    @Test
    void shouldSetAndGetExclusionKeywords() {
        MonitorRules rule = new MonitorRules();
        rule.setExclusionKeywords(Arrays.asList("known-issue", "ignore-this"));
        assertEquals(2, rule.getExclusionKeywords().size());
    }

    @Test
    void shouldAllowNullExclusionKeywords() {
        MonitorRules rule = new MonitorRules();
        rule.setKeywords(Collections.singletonList("ERROR"));
        assertNull(rule.getExclusionKeywords());
    }

    @Test
    void shouldSetAndGetWebhook() {
        MonitorRules rule = new MonitorRules();
        rule.setWebhook("https://custom-webhook.example.com");
        assertEquals("https://custom-webhook.example.com", rule.getWebhook());
    }

    @Test
    void shouldSetAndGetContextLines() {
        MonitorRules rule = new MonitorRules();
        rule.setContextLines(20);
        assertEquals(20, rule.getContextLines());
    }

    @Test
    void shouldToggleEnabled() {
        MonitorRules rule = new MonitorRules();
        rule.setEnabled(false);
        assertFalse(rule.isEnabled());
        rule.setEnabled(true);
        assertTrue(rule.isEnabled());
    }

    @Test
    void shouldHandleEmptyKeywords() {
        MonitorRules rule = new MonitorRules();
        rule.setKeywords(Collections.emptyList());
        assertTrue(rule.getKeywords().isEmpty());
    }
}
