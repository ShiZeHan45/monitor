package com.szh.monitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.entity.SqlExecuteRule;
import com.szh.monitor.service.SqlDataSourceService;
import com.szh.monitor.service.SqlExecuteRuleService;
import com.szh.monitor.service.impl.SqlConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class SqlDataInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(SqlDataInitializer.class);

    @Resource
    private MultiDataSourceConfig multiDataSourceConfig;

    @Resource
    private SqlDataSourceService dataSourceService;

    @Resource
    private SqlExecuteRuleService ruleService;

    @Resource
    private SqlConfigService sqlConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(String... args) throws Exception {
        List<SqlDataSource> existingDataSources = dataSourceService.list();
        if (existingDataSources != null && !existingDataSources.isEmpty()) {
            logger.info("数据库中已存在 {} 个SQL数据源配置，跳过初始化", existingDataSources.size());
            return;
        }

        logger.info("数据库为空，开始从YML配置初始化SQL数据源...");

        Map<String, MultiDataSourceConfig.DataSourceProperties> dataSourceList = multiDataSourceConfig.getList();
        if (dataSourceList == null || dataSourceList.isEmpty()) {
            logger.info("YML配置中无SQL数据源配置");
            return;
        }

        for (Map.Entry<String, MultiDataSourceConfig.DataSourceProperties> entry : dataSourceList.entrySet()) {
            MultiDataSourceConfig.DataSourceProperties info = entry.getValue();
            try {
                SqlDataSource dataSource = new SqlDataSource();
                dataSource.setEnvironmentName(info.getEnvironmentName());
                dataSource.setJdbcUrl(info.getJdbcUrl());
                dataSource.setUsername(info.getUsername());
                dataSource.setPassword(info.getPassword());
                dataSource.setDriverClassName(info.getDriverClassName());
                // webhook, week, startTime, endTime 暂时留空，因为原YML配置中没有这些
                dataSource.setEnabled(info.isEnabled() ? 1 : 0);
                dataSource.setCreateTime(LocalDateTime.now());
                dataSource.setUpdateTime(LocalDateTime.now());

                dataSourceService.save(dataSource);
                logger.info("已导入SQL数据源: {}", info.getEnvironmentName());

                // 初始化规则
                if (info.getExecuteSql() != null && !info.getExecuteSql().isEmpty()) {
                    for (String sqlFileName : info.getExecuteSql()) {
                        SqlExecuteRule rule = new SqlExecuteRule();
                        rule.setEnvironmentName(info.getEnvironmentName());
                        rule.setSqlFileName(sqlFileName);
                        // 默认值
                        rule.setExecuteLimit(999);
                        rule.setExecuteStartTime("00:00:00");
                        rule.setExecuteEndTime("23:59:59");
                        rule.setExecuteFrequency(5);
                        
                        ruleService.save(rule);
                        logger.info("已导入规则: {} - {}", info.getEnvironmentName(), sqlFileName);
                    }
                }
            } catch (Exception e) {
                logger.error("导入SQL数据源 {} 失败: {}", info.getEnvironmentName(), e.getMessage());
            }
        }

        logger.info("YML配置初始化完成");

        // 导入完成后刷新数据源配置，创建JdbcTemplate Bean
        sqlConfigService.refreshConfig();
        logger.info("数据源配置刷新完成");
    }
}
