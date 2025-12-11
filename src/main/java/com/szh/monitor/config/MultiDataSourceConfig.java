package com.szh.monitor.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.szh.monitor.context.ExecuteJDBCContext;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.HashMap;
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
        private Map<String, Object> hikari = new HashMap<>();  // 保留全量 Hikari 配置
    }

    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

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

            bindHikariProperties(config, props.getHikari());

            dataSourceMap.put(props.getEnvironmentName(), new HikariDataSource(config));
        });

        return dataSourceMap;
    }

    /**
     * 安全绑定 HikariConfig 属性（优化版）
     */
    private void bindHikariProperties(HikariConfig config, Map<String, Object> hikariProps) {

        for (Map.Entry<String, Object> entry : hikariProps.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String setterName = "set" + camel(key);

            try {
                // 找到方法（不限参数类型）
                Method setter = findCompatibleSetter(config.getClass(), setterName);

                if (setter != null) {
                    Class<?> paramType = setter.getParameterTypes()[0];
                    Object convertedValue = convertIfNeeded(value, paramType);
                    setter.invoke(config, convertedValue);
                }

            } catch (Exception e) {
                // 如果要调试可以打印日志，这里为保持安静忽略即可
                System.out.println("⚠ Hikari 参数未绑定: " + key + " = " + value);
            }
        }
    }

    /**
     * 支持 long/int/boolean/String 等自动转换
     */
    private Object convertIfNeeded(Object value, Class<?> targetType) {
        if (value == null) return null;

        if (targetType.isAssignableFrom(value.getClass())) return value;

        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }

        return value;  // 默认返回原值
    }

    /**
     * 查找兼容 setter（参数类型自动匹配）
     */
    private Method findCompatibleSetter(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return null;
    }

    /**
     * 下划线/短横线转驼峰
     */
    private static String camel(String key) {
        String[] arr = key.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    @Bean("dynamicJdbcTemplates")
    public Map<String, JdbcTemplate> dynamicJdbcTemplates(@Qualifier("dynamicDataSources") Map<String, DataSource> dynamicDataSources) {
        Map<String, JdbcTemplate> jdbcTemplateMap = new HashMap<>();

        dynamicDataSources.forEach((name, dataSource) -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            executeJDBCContext.addJdbcTemplate(name, name + "JdbcTemplate");
            jdbcTemplateMap.put(name, jdbcTemplate);
        });

        return jdbcTemplateMap;
    }
}
