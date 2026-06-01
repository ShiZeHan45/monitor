package com.szh.monitor.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.annotation.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private OperationLogServiceProxy operationLogServiceProxy;

    @Around("@annotation(com.szh.monitor.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OperationLog operationLog = getAnnotation(joinPoint);
        String module = operationLog.module();
        String operationType = operationLog.operationType();
        String description = operationLog.description();

        HttpServletRequest request = getRequest();
        String ip = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "";

        long startTime = System.currentTimeMillis();
        Object result = null;
        boolean success = true;
        String errorMsg = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            String detail = buildDetail(joinPoint, operationType, description, success, errorMsg, costTime, result);

            try {
                if (operationLogServiceProxy != null) {
                    operationLogServiceProxy.logAsync(ip, userAgent, operationType, module, detail);
                }
            } catch (Exception e) {
                logger.error("记录操作日志失败，不影响业务: {}", e.getMessage());
            }
        }
    }

    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(OperationLog.class);
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String buildDetail(ProceedingJoinPoint joinPoint, String operationType, String description, boolean success, String errorMsg, long costTime, Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append(description);

        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    try {
                        String argStr = toJsonString(args[i]);
                        sb.append(" | 操作内容: ").append(argStr);
                        break;
                    } catch (Exception e) {
                        sb.append(" | 操作内容: ").append(args[i].toString());
                    }
                }
            }
        }

        if (!success) {
            sb.append(" | 失败: ").append(errorMsg);
        }
        sb.append(" | 耗时: ").append(costTime).append("ms");

        return sb.toString();
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
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
            String[] ips = ip.split(",");
            for (String i : ips) {
                i = i.trim();
                if (!"unknown".equalsIgnoreCase(i) && !i.isEmpty()) {
                    ip = i;
                    break;
                }
            }
        }
        return ip;
    }
}
