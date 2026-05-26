package com.szh.monitor.scheduled;

import com.szh.monitor.service.MsgSendLogService;
import com.szh.monitor.service.SqlExecuteLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutorLogClear {
    @Autowired
    private SqlExecuteLogService sqlExecuteLogService;
    @Autowired
    private MsgSendLogService msgSendLogService;

    @Scheduled(cron = "0 5 0 * * ?")//不想保留历史执行记录可以把这个定时器开起来
    public void clear() {
        sqlExecuteLogService.clear();
        msgSendLogService.clear();
    }
}
