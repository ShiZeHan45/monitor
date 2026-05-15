package com.szh.monitor.scheduled;

import com.szh.monitor.service.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 执行异常的SQL重新尝试执行
 */
@Component
public class ExecuteFailedRetry {
    @Autowired
    private List<ExecutorService> executorServices;
    @Async("retrySQL")
    @Scheduled(fixedRate=300_000)
    public void retry(){
        executorServices.forEach(ExecutorService::executeRetry);
    }
}
