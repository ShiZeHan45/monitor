package com.szh.monitor.scheduled;

import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.service.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行器总调度
 */
@Component
public class ExecutorScheduler {
    @Autowired
    private SQLConfig SQLConfig;

    @Autowired
    private List<ExecutorService> executorServices;

    @Autowired
    public ExecutorScheduler(List<ExecutorService> executorServices) {
        this.executorServices = executorServices;
    }

    @Scheduled(cron = "${watcher.sql.schedule-cron}")
    public void executor() {
        if(!SQLConfig.isEnable()){
            return;
        }
        int hour = LocalDateTime.now().getHour();
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不执行调度
            return;
        }
        executorServices.forEach(ExecutorService::execute);
    }
}
