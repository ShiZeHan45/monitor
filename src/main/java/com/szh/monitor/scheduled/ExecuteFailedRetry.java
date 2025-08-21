package com.szh.monitor.scheduled;

import com.szh.monitor.service.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行异常的SQL重新尝试执行
 */
@Component
public class ExecuteFailedRetry {
    @Autowired
    private List<ExecutorService> executorServices;

    @Scheduled(cron = "${app.schedule-retry-cron}")
    public void retry(){
        int hour = LocalDateTime.now().getHour();
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不执行调度
            return;
        }
        executorServices.forEach(ExecutorService::executeRetry);
    }
}
