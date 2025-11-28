package com.szh.monitor.config;

import lombok.Data;

import java.util.List;

@Data
public class MonitorRules {
    private String name;
    private String queryExpr;
    private List<String> keywords;
    private List<String> exclusionKeywords;
    private int contextLines = 5;
    private String webhook;
    private boolean enabled = true;
}