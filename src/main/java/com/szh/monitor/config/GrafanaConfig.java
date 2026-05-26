package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "watcher.log.grafana")
@Data
public class GrafanaConfig {
    private List<GrafanaInfo> list;


    @Data
    public static class GrafanaInfo {
        private String url;
        private String environmentName;
        private String datasourceId;
        private String username;
        private String password;
        private String webhook;
        private List<Integer> week;
        @DateTimeFormat(pattern = "HH:mm")
        private LocalTime startTime;
        @DateTimeFormat(pattern = "HH:mm")
        private LocalTime endTime;
        private List<MonitorRules> monitors;

    }
}
