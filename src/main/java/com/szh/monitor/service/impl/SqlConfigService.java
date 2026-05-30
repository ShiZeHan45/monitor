package com.szh.monitor.service.impl;

import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SqlConfigService {
    private static final Logger logger = LoggerFactory.getLogger(SqlConfigService.class);

    @Autowired
    private SqlDataSourceService dataSourceService;

    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    @Autowired
    private GenericApplicationContext applicationContext;

    // 保存已创建的数据源，方便后续关闭
    private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            refreshConfig();
        } catch (Exception e) {
            logger.warn("初始化配置失败，可能是数据库表尚未创建: {}", e.getMessage());
        }
    }

    public void refreshConfig() {
        synchronized (this) {
            // 1. 关闭旧的数据源
            for (HikariDataSource ds : dataSourceMap.values()) {
                try {
                    ds.close();
                } catch (Exception e) {
                    logger.warn("关闭数据源失败", e);
                }
            }
            dataSourceMap.clear();

            // 2. 从数据库读取最新的启用的数据源
            List<SqlDataSource> dataSources = dataSourceService.listEnabled();
            for (SqlDataSource ds : dataSources) {
                try {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(ds.getJdbcUrl());
                    config.setUsername(ds.getUsername());
                    config.setPassword(ds.getPassword());
                    config.setDriverClassName(ds.getDriverClassName());

                    // 配置HikariCP自动重连
                    config.setInitializationFailTimeout(-1);
                    config.setConnectionTimeout(30000);
                    config.setValidationTimeout(5000);
                    config.setConnectionTestQuery("SELECT 1");
                    config.setMaxLifetime(120000);
                    config.setIdleTimeout(30000);
                    config.setMinimumIdle(0);
                    config.setMaximumPoolSize(1);
                    config.setKeepaliveTime(30000);

                    HikariDataSource dataSource = new HikariDataSource(config);
                    dataSourceMap.put(ds.getEnvironmentName(), dataSource);

                    String beanName = ds.getEnvironmentName() + "JdbcTemplate";
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                    
                    // 获取 BeanFactory 来注册 Bean
                    DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
                    
                    // 先注销旧的Bean
                    try {
                        if (beanFactory.containsBean(beanName)) {
                            beanFactory.destroyBean(beanName);
                        }
                    } catch (Exception e) {
                        // 没有旧Bean，忽略
                    }
                    
                    // 注册新的Bean
                    beanFactory.registerSingleton(beanName, jdbcTemplate);
                    
                    // 更新ExecuteJDBCContext
                    executeJDBCContext.addJdbcTemplate(ds.getEnvironmentName(), beanName);
                    
                    logger.info("✅ 数据源初始化成功: {}", ds.getEnvironmentName());
                } catch (Exception e) {
                    logger.error("❌ 数据源初始化失败: {} - {}", ds.getEnvironmentName(), e.getMessage(), e);
                }
            }
        }
    }
}
