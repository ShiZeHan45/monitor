package com.szh.monitor.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.szh.monitor.context.ExecuteJDBCContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "watcher.sql.datasource")
@Data
public class MultiDataSourceConfig {

    private String relation;
    private Map<String, DataSourceProperties> list = new HashMap<>();

    @Data
    public static class DataSourceProperties {
        private boolean enabled;
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;
        private Map<String, Object> hikari = new HashMap<>();  // ⭐ 支持 hikari.* 全量映射
    }

    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    public String getName(String code){
        String[] split = this.relation.split(",");
        for (String s : split) {
            String[] nameCode = s.split("-");
            if(nameCode[1].equals(code)){
                return nameCode[0];
            }
        }
        return "-";
    }

    @Bean("dynamicDataSources")
    public Map<String, DataSource> dynamicDataSources() {
        Map<String, DataSource> dataSourceMap = new HashMap<>();

        list.forEach((name, props) -> {
            if (!props.isEnabled()) return;

            HikariConfig config = new HikariConfig();

            config.setJdbcUrl(props.getJdbcUrl());
            config.setUsername(props.getUsername());
            config.setPassword(props.getPassword());
            config.setDriverClassName(props.getDriverClassName());

            // ⭐ 自动绑定 hikari.* 下面所有配置
            props.getHikari().forEach((k, v) -> {
                try {
                    // HikariConfig 自带 setter 调用
                    config.getClass()
                            .getMethod("set" + camel(k), v.getClass())
                            .invoke(config, v);
                } catch (Exception ignored) {
                    // 无对应 setter 的字段自动跳过
                }
            });

            HikariDataSource dataSource = new HikariDataSource(config);

            dataSourceMap.put(name, dataSource);
        });

        return dataSourceMap;
    }

    // 下划线/短横线转驼峰
    private static String camel(String key) {
        String[] arr = key.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    @Bean("dynamicJdbcTemplates")
    public Map<String, JdbcTemplate> dynamicJdbcTemplates(Map<String, DataSource> dynamicDataSources) {

        Map<String, JdbcTemplate> jdbcTemplateMap = new HashMap<>();

        dynamicDataSources.forEach((name, dataSource) -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            executeJDBCContext.addJdbcTemplate(getName(name), name + "JdbcTemplate");
            jdbcTemplateMap.put(name, jdbcTemplate);
        });

        return jdbcTemplateMap;
    }
}
