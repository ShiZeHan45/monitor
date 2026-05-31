package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.GrafanaDataSource;

import java.util.List;

public interface GrafanaDataSourceService extends IService<GrafanaDataSource> {
    List<GrafanaDataSource> listEnabled();
    boolean updateOnlineStatus(Long dataSourceId, boolean isOnline);
    GrafanaDataSource getByEnvironmentName(String environmentName);
}
