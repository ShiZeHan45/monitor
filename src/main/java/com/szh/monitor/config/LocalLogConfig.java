package com.szh.monitor.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "watcher.log.local")
@Data
public class LocalLogConfig {
    private String errorLogPath;

    private List<String> keywords;

    private int contextLines;

    private int dedupWindowMinutes;

    private boolean enabled;

    private String name;
}
