package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.SqlDataSource;

import java.util.List;

public interface SqlDataSourceService extends IService<SqlDataSource> {
    List<SqlDataSource> listEnabled();
    boolean updateOnlineStatus(Long dataSourceId, boolean isOnline);
}
