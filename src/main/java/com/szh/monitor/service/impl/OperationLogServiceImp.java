package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.mapper.OperationLogMapper;
import com.szh.monitor.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OperationLogServiceImp implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public void saveLog(String operationType, String operationModule, String operationContent, String targetId, String ipAddress, String userAgent, String environmentName) {
        OperationLog log = new OperationLog();
        log.setOperationType(operationType);
        log.setOperationModule(operationModule);
        log.setOperationContent(operationContent);
        log.setTargetId(targetId);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setBrowserInfo(parseBrowserInfo(userAgent));
        log.setEnvironmentName(environmentName);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    @Override
    public IPage<OperationLog> getLogs(Page<OperationLog> page, String operationModule, String operationType, String environmentName) {
        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<>();
        
        if (operationModule != null && !operationModule.isEmpty()) {
            query.eq(OperationLog::getOperationModule, operationModule);
        }
        if (operationType != null && !operationType.isEmpty()) {
            query.eq(OperationLog::getOperationType, operationType);
        }
        if (environmentName != null && !environmentName.isEmpty()) {
            query.eq(OperationLog::getEnvironmentName, environmentName);
        }
        
        query.orderByDesc(OperationLog::getCreateTime);
        return operationLogMapper.selectPage(page, query);
    }

    private String parseBrowserInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        
        String browser = "Unknown";
        
        if (userAgent.contains("Edg")) {
            browser = "Microsoft Edge";
        } else if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
            browser = "Google Chrome";
        } else if (userAgent.contains("Firefox")) {
            browser = "Mozilla Firefox";
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            browser = "Apple Safari";
        } else if (userAgent.contains("Opera") || userAgent.contains("OPR")) {
            browser = "Opera";
        }
        
        return browser;
    }
}