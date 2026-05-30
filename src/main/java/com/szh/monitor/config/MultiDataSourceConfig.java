package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "watcher.sql.datasource")
@Data
public class MultiDataSourceConfig {

    private Map<String, DataSourceProperties> list = new HashMap<>();

    @Data
    public static class DataSourceProperties {
        private String environmentName;
        private boolean enabled;
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;
        private List<String> executeSql;
        private Map<String, Object> hikari = new HashMap<>();  // 保留全量 Hikari 配置
    }
}
