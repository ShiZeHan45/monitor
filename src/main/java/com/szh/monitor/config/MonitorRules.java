package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "watcher.log.grafana.monitors")
public class MonitorRules {
    private List<Rule> list;

    @Data
    public static class Rule {
        private String name;
        private String queryExpr;
        private List<String> keywords;
        private List<String> exclusionKeywords;
        private int contextLines = 5;
        private String webhook;
        private boolean enabled = true;
    }
}