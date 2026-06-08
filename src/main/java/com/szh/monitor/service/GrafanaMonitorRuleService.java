package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.GrafanaMonitorRule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface GrafanaMonitorRuleService extends IService<GrafanaMonitorRule> {
    List<GrafanaMonitorRule> listByDataSourceId(Long dataSourceId);
    List<GrafanaMonitorRule> listEnabledByDataSourceId(Long dataSourceId);
    boolean removeByDataSourceId(Long dataSourceId);

    List<Map<String, Object>> getEnvironmentCollectStats();
    List<Map<String, Object>> getEnvironmentDailyCollectStats(LocalDate date);

    void updateLastTs(Long ruleId, String environmentName, Long dataSourceId, long lastTs, long collectCount);
    void updateLastTs(Long ruleId, LocalDateTime dateTime);
}