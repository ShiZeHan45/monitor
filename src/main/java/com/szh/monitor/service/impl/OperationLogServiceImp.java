package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.mapper.OperationLogMapper;
import com.szh.monitor.service.OperationLogService;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
            OperationLog log = new OperationLog(ip, userAgent, TYPE_VISIT, "首页", null, "访问首页");
            operationLogMapper.insert(log);
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
            String id = targetId != null ? targetId.toString() : null;
            OperationLog log = new OperationLog(ip, userAgent, operationType, module, id, detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("记录操作日志失败", e);
        }
    }

    @Override
    public Page<OperationLog> getLogs(int page, int size) {
        Page<OperationLog> pageInfo = new Page<>(page, size);
        return operationLogMapper.selectPage(pageInfo, null);
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
