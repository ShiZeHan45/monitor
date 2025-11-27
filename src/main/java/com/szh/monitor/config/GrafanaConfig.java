package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "watcher.log")
@Data
public class GrafanaConfig {
    private Grafana grafana;


    @Data
    public static class Grafana {
        private Primary primary;


        @Data
        public static class Primary {
            private String url;
            private String environmentName;
            private String datasourceId;
            private String username;
            private String password;
            private String webhook;
        }
    }
}
