package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.SqlExecuteLog;

import java.util.List;

public interface SqlExecuteLogService extends IService<SqlExecuteLog> {
    void saveOrUpdate(String environmentName, String name);

    List<SqlExecuteLog> findEnvironmentName(String environmentName);

    List<SqlExecuteLog> findEnvironmentNameAndFailedCountGt0(String environmentName);

    int findMaxFailedCount(String environmentName);

    void resetFailedCount(String environmentName);
}
