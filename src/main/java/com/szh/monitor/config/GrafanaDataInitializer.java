package com.szh.monitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import com.szh.monitor.service.impl.GrafanaLogServiceImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class GrafanaDataInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaDataInitializer.class);

    @Resource
    private GrafanaConfig grafanaConfig;

    @Resource
    private GrafanaDataSourceService dataSourceService;

    @Resource
    private GrafanaMonitorRuleService ruleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private GrafanaLogServiceImp grafanaLogService;

    @Override
    public void run(String... args) throws Exception {
        List<GrafanaDataSource> existingDataSources = dataSourceService.list();
        if (existingDataSources != null && !existingDataSources.isEmpty()) {
            logger.info("数据库中已存在 {} 个数据源配置，跳过初始化", existingDataSources.size());
            return;
        }

        logger.info("数据库为空，开始从YML配置初始化数据源...");

        List<GrafanaConfig.GrafanaInfo> grafanaList = grafanaConfig.getList();
        if (grafanaList == null || grafanaList.isEmpty()) {
            logger.info("YML配置中无Grafana数据源配置");
            return;
        }

        for (GrafanaConfig.GrafanaInfo info : grafanaList) {
            try {
                GrafanaDataSource dataSource = new GrafanaDataSource();
                dataSource.setEnvironmentName(info.getEnvironmentName());
                dataSource.setUrl(info.getUrl());
                dataSource.setDatasourceId(info.getDatasourceId());
                dataSource.setUsername(info.getUsername());
                dataSource.setPassword(info.getPassword());
                dataSource.setWebhook(info.getWebhook());
                dataSource.setWeek(objectMapper.writeValueAsString(info.getWeek()));
                dataSource.setStartTime(info.getStartTime() != null ? info.getStartTime().toString() : null);
                dataSource.setEndTime(info.getEndTime() != null ? info.getEndTime().toString() : null);
                dataSource.setEnabled(1);
                dataSource.setCreateTime(LocalDateTime.now());
                dataSource.setUpdateTime(LocalDateTime.now());

                dataSourceService.save(dataSource);
                logger.info("已导入数据源: {}", info.getEnvironmentName());

                if (info.getMonitors() != null && !info.getMonitors().isEmpty()) {
                    for (MonitorRules rule : info.getMonitors()) {
                        GrafanaMonitorRule monitorRule = new GrafanaMonitorRule();
                        monitorRule.setDataSourceId(dataSource.getId());
                        monitorRule.setName(rule.getName());
                        monitorRule.setQueryExpr(rule.getQueryExpr());
                        monitorRule.setKeywords(objectMapper.writeValueAsString(rule.getKeywords()));
                        monitorRule.setExclusionKeywords(objectMapper.writeValueAsString(rule.getExclusionKeywords()));
                        monitorRule.setContextLines(rule.getContextLines());
                        monitorRule.setWebhook(rule.getWebhook());
                        monitorRule.setEnabled(rule.isEnabled() ? 1 : 0);
                        monitorRule.setCreateTime(LocalDateTime.now());
                        monitorRule.setUpdateTime(LocalDateTime.now());

                        ruleService.save(monitorRule);
                        logger.info("  已导入规则: {}", rule.getName());
                    }
                }
            } catch (Exception e) {
                logger.error("导入数据源 {} 失败: {}", info.getEnvironmentName(), e.getMessage());
            }
        }

        logger.info("YML配置初始化完成");

        // 导入完成后刷新Grafana配置
        grafanaLogService.refreshConfig();
        logger.info("Grafana配置刷新完成");
    }
}
