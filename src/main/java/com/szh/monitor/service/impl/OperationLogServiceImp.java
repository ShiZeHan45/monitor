package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.mapper.OperationLogMapper;
import com.szh.monitor.service.OperationLogService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OperationLogServiceImp implements OperationLogService {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogServiceImp.class);

    @Autowired
    private OperationLogMapper operationLogMapper;

    private static final String TYPE_VISIT = "VISIT";
    private static final String TYPE_CREATE = "CREATE";
    private static final String TYPE_EDIT = "EDIT";
    private static final String TYPE_DELETE = "DELETE";

    @Override
    public void logVisit(HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay();
            
            OperationLog existingLog = operationLogMapper.selectTodayVisitLog(ip, startOfDay, endOfDay);
            
            if (existingLog != null) {
                existingLog.setCreateTime(now);
                operationLogMapper.updateById(existingLog);
            } else {
                OperationLog log = new OperationLog(ip, userAgent, TYPE_VISIT, "首页", null, "访问首页");
                operationLogMapper.insert(log);
            }
        } catch (Exception e) {
            logger.error("记录访问日志失败", e);
        }
    }

    @Override
    public void logCreate(String module, Long targetId, String detail, HttpServletRequest request) {
        log(TYPE_CREATE, module, targetId, detail, request);
    }

    @Override
    public void logEdit(String module, Long targetId, String detail, HttpServletRequest request) {
        log(TYPE_EDIT, module, targetId, detail, request);
    }

    @Override
    public void logDelete(String module, Long targetId, String detail, HttpServletRequest request) {
        log(TYPE_DELETE, module, targetId, detail, request);
    }

    @Override
    public void log(String operationType, String module, Long targetId, String detail, HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            Integer id = targetId != null ? targetId.intValue() : null;
            OperationLog log = new OperationLog(ip, userAgent, operationType, module, id, detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("记录操作日志失败", e);
        }
    }

    @Override
    public Page<OperationLog> getLogs(int page, int size, String operationType, String module, String startDate, String endDate) {
        Page<OperationLog> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> wrapper = buildQueryWrapper(operationType, module, startDate, endDate);
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return operationLogMapper.selectPage(pageInfo, wrapper);
    }

    @Override
    public long countLogs(String operationType, String module, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = buildQueryWrapper(operationType, module, startDate, endDate);
        return operationLogMapper.selectCount(wrapper);
    }

    private LambdaQueryWrapper<OperationLog> buildQueryWrapper(String operationType, String module, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(OperationLog::getOperationType, operationType);
        }

        if (module != null && !module.isEmpty()) {
            wrapper.like(OperationLog::getModule, module);
        }

        if (startDate != null && !startDate.isEmpty()) {
            LocalDateTime start = LocalDateTime.parse(startDate + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            wrapper.ge(OperationLog::getCreateTime, start);
        }

        if (endDate != null && !endDate.isEmpty()) {
            LocalDateTime end = LocalDateTime.parse(endDate + " 23:59:59", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            wrapper.le(OperationLog::getCreateTime, end);
        }
        
        return wrapper;
    }

    @Override
    public List<String> getModules() {
        return operationLogMapper.selectDistinctModules();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
