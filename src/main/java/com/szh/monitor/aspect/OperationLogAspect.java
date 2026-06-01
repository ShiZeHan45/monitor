package com.szh.monitor.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.annotation.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private OperationLogServiceProxy operationLogServiceProxy;

    @Autowired
    private ApplicationContext applicationContext;

    private static final Map<String, Map<String, String>> FIELD_NAME_MAPPING = new HashMap<>();

    static {
        Map<String, String> sqlRuleFields = new HashMap<>();
        sqlRuleFields.put("environmentName", "环境名称");
        sqlRuleFields.put("sqlFileName", "SQL文件名");
        sqlRuleFields.put("executeLimit", "执行次数限制");
        sqlRuleFields.put("executeStartTime", "开始执行时间");
        sqlRuleFields.put("executeEndTime", "结束执行时间");
        sqlRuleFields.put("executeFrequency", "执行频率");
        FIELD_NAME_MAPPING.put("SqlExecuteRule", sqlRuleFields);

        Map<String, String> sqlDataSourceFields = new HashMap<>();
        sqlDataSourceFields.put("environmentName", "环境名称");
        sqlDataSourceFields.put("jdbcUrl", "JDBC连接地址");
        sqlDataSourceFields.put("username", "用户名");
        sqlDataSourceFields.put("password", "密码");
        sqlDataSourceFields.put("driverClassName", "驱动类");
        sqlDataSourceFields.put("webhook", "推送Webhook");
        sqlDataSourceFields.put("week", "执行周");
        sqlDataSourceFields.put("startTime", "开始时间");
        sqlDataSourceFields.put("endTime", "结束时间");
        sqlDataSourceFields.put("enabled", "启用状态");
        sqlDataSourceFields.put("isOnline", "在线状态");
        FIELD_NAME_MAPPING.put("SqlDataSource", sqlDataSourceFields);

        Map<String, String> grafanaDataSourceFields = new HashMap<>();
        grafanaDataSourceFields.put("environmentName", "环境名称");
        grafanaDataSourceFields.put("url", "API地址");
        grafanaDataSourceFields.put("datasourceId", "数据源ID");
        grafanaDataSourceFields.put("username", "用户名");
        grafanaDataSourceFields.put("password", "密码");
        grafanaDataSourceFields.put("webhook", "推送Webhook");
        grafanaDataSourceFields.put("week", "执行周");
        grafanaDataSourceFields.put("startTime", "开始时间");
        grafanaDataSourceFields.put("endTime", "结束时间");
        grafanaDataSourceFields.put("enabled", "启用状态");
        grafanaDataSourceFields.put("isOnline", "在线状态");
        FIELD_NAME_MAPPING.put("GrafanaDataSource", grafanaDataSourceFields);

        Map<String, String> grafanaRuleFields = new HashMap<>();
        grafanaRuleFields.put("dataSourceId", "数据源ID");
        grafanaRuleFields.put("name", "规则名称");
        grafanaRuleFields.put("queryExpr", "查询表达式");
        grafanaRuleFields.put("keywords", "关键词");
        grafanaRuleFields.put("exclusionKeywords", "排除关键词");
        grafanaRuleFields.put("contextLines", "上下文行数");
        grafanaRuleFields.put("webhook", "推送Webhook");
        grafanaRuleFields.put("enabled", "启用状态");
        FIELD_NAME_MAPPING.put("GrafanaMonitorRule", grafanaRuleFields);
    }

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

        Object oldEntity = null;
        Object newEntity = null;
        Long targetId = null;

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = signature.getName();

            if (methodName.startsWith("update")) {
                Object[] args = joinPoint.getArgs();
                for (Object arg : args) {
                    if (arg instanceof Long) {
                        targetId = (Long) arg;
                    } else if (!isSimpleType(arg)) {
                        newEntity = arg;
                        Class<?> entityClass = arg.getClass();
                        try {
                            Field idField = entityClass.getDeclaredField("id");
                            idField.setAccessible(true);
                            Object idValue = idField.get(arg);
                            if (idValue instanceof Long) {
                                targetId = (Long) idValue;
                            } else if (idValue instanceof Integer) {
                                targetId = ((Integer) idValue).longValue();
                            }
                        } catch (Exception e) {
                        }
                    }
                }

                if (targetId != null && newEntity != null) {
                    oldEntity = getOldEntity(newEntity.getClass(), targetId);
                }
            } else if (methodName.startsWith("create")) {
                Object[] args = joinPoint.getArgs();
                for (Object arg : args) {
                    if (!isSimpleType(arg)) {
                        newEntity = arg;
                    }
                }
            }

            result = joinPoint.proceed();

            return result;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            String detail = buildDetail(operationType, description, success, errorMsg, costTime, oldEntity, newEntity);

            try {
                if (operationLogServiceProxy != null) {
                    operationLogServiceProxy.logAsync(ip, userAgent, operationType, module, detail);
                }
            } catch (Exception e) {
                logger.error("记录操作日志失败，不影响业务: {}", e.getMessage());
            }
        }
    }

    private Object getOldEntity(Class<?> entityClass, Long id) {
        try {
            String mapperName = entityClass.getSimpleName() + "Mapper";
            BaseMapper<?> mapper = (BaseMapper<?>) applicationContext.getBean(mapperName);
            if (mapper != null) {
                return mapper.selectById(id);
            }
        } catch (Exception e) {
            logger.debug("获取旧数据失败: {}", e.getMessage());
        }
        return null;
    }

    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(OperationLog.class);
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String buildDetail(String operationType, String description, boolean success, String errorMsg, long costTime, Object oldEntity, Object newEntity) {
        StringBuilder sb = new StringBuilder();
        sb.append(description);

        if ("EDIT".equals(operationType) && newEntity != null) {
            sb.append(" | ").append(buildChangeDetail(oldEntity, newEntity));
        } else if ("CREATE".equals(operationType) && newEntity != null) {
            sb.append(" | ").append(buildCreateDetail(newEntity));
        }

        if (!success) {
            sb.append(" | 失败: ").append(errorMsg);
        }
        sb.append(" | 耗时: ").append(costTime).append("ms");

        return sb.toString();
    }

    private String buildCreateDetail(Object entity) {
        StringBuilder sb = new StringBuilder();

        String entityName = entity.getClass().getSimpleName();
        String environmentName = getEnvironmentName(entity);

        if (environmentName != null) {
            sb.append(environmentName);
        }

        Map<String, String> fieldMapping = FIELD_NAME_MAPPING.get(entityName);

        sb.append("，新增记录");

        if (fieldMapping != null) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                Object value = getFieldValue(entity, entry.getKey());
                if (value != null && !value.toString().isEmpty()) {
                    String displayValue = formatValue(value);
                    values.add(entry.getValue() + "=" + displayValue);
                }
            }
            if (!values.isEmpty()) {
                sb.append(" (").append(String.join(", ", values)).append(")");
            }
        }

        return sb.toString();
    }

    private String buildChangeDetail(Object oldEntity, Object newEntity) {
        StringBuilder sb = new StringBuilder();

        String entityName = newEntity.getClass().getSimpleName();
        String environmentName = getEnvironmentName(newEntity);

        if (environmentName != null) {
            sb.append(environmentName);
        }

        Map<String, String> fieldMapping = FIELD_NAME_MAPPING.get(entityName);
        if (fieldMapping == null) {
            try {
                sb.append("，操作内容: ").append(objectMapper.writeValueAsString(newEntity));
            } catch (Exception e) {
                sb.append("，操作内容: ").append(newEntity.toString());
            }
            return sb.toString();
        }

        List<String> changes = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
            Object oldValue = oldEntity != null ? getFieldValue(oldEntity, entry.getKey()) : null;
            Object newValue = getFieldValue(newEntity, entry.getKey());

            String oldStr = formatValue(oldValue);
            String newStr = formatValue(newValue);

            if (!oldStr.equals(newStr)) {
                changes.add("【" + entry.getValue() + "】修改前 " + oldStr + " 修改后 " + newStr);
            }
        }

        if (!changes.isEmpty()) {
            sb.append("，").append(String.join("；", changes));
        } else {
            sb.append("，无变更");
        }

        return sb.toString();
    }

    private String getEnvironmentName(Object entity) {
        try {
            Field envField = entity.getClass().getDeclaredField("environmentName");
            envField.setAccessible(true);
            Object value = envField.get(entity);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
        }
        return null;
    }

    private Object getFieldValue(Object entity, String fieldName) {
        if (entity == null) return null;
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "是" : "否";
        }
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            if (intValue == 1) {
                return "是";
            } else if (intValue == 0) {
                return "否";
            }
        }
        String strValue = value.toString();
        if (strValue.length() > 50) {
            return strValue.substring(0, 50) + "...";
        }
        return strValue;
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean;
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
