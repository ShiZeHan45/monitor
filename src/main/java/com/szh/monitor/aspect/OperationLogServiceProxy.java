package com.szh.monitor.aspect;

import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OperationLogServiceProxy {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Async
    public void logAsync(String ip, String userAgent, String operationType, String module, String detail) {
        try {
            // 对于首页访问，同一IP同一天只记录一条，更新时间即可
            if ("VISIT".equals(operationType) && "首页".equals(module)) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
                LocalDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay();

                OperationLog existingLog = operationLogMapper.selectTodayVisitLog(ip, startOfDay, endOfDay);

                if (existingLog != null) {
                    existingLog.setCreateTime(now);
                    operationLogMapper.updateById(existingLog);
                    return;
                }
            }

            // 其他情况正常记录
            OperationLog log = new OperationLog(ip, userAgent, operationType, module, null, detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            throw e;
        }
    }
}
