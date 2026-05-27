package com.szh.monitor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;

public interface OperationLogService {
    void saveLog(String operationType, String operationModule, String operationContent, String targetId, String ipAddress, String userAgent, String environmentName);
    
    IPage<OperationLog> getLogs(Page<OperationLog> page, String operationModule, String operationType, String environmentName);
}