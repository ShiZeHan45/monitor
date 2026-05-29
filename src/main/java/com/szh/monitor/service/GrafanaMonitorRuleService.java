package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.GrafanaMonitorRule;

import java.util.List;

public interface GrafanaMonitorRuleService extends IService<GrafanaMonitorRule> {
    List<GrafanaMonitorRule> listByDataSourceId(Long dataSourceId);
    List<GrafanaMonitorRule> listEnabledByDataSourceId(Long dataSourceId);
    boolean removeByDataSourceId(Long dataSourceId);
}