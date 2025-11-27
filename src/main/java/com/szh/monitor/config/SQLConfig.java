package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "watcher.sql")
public class SQLConfig {
    private boolean enable;
    private Resource sqlDir;
    private String sqlAbsoluteDir;
    private int checkLimit;
    private List<String> unLimitCheckFiles;
}
