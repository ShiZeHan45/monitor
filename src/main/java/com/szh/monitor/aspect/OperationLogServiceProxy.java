package com.szh.monitor.aspect;

import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class OperationLogServiceProxy {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Async
    public void logAsync(String ip, String userAgent, String operationType, String module, String detail) {
        try {
            OperationLog log = new OperationLog(ip, userAgent, operationType, module, null, detail);
            operationLogMapper.insert(log);
        } catch (Exception e) {
            throw e;
        }
    }
}
