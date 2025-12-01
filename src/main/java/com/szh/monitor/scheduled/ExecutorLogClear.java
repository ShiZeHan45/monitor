package com.szh.monitor.scheduled;

import com.szh.monitor.service.SqlExecuteLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutorLogClear {
    @Autowired
    private SqlExecuteLogService sqlExecuteLogService;

    @Scheduled(cron = "0 5 0 * * ?")
    public void clear() {
        sqlExecuteLogService.clear();
    }
}
