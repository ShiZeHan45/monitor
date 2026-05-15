package com.szh.monitor.scheduled;

import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.service.ExecutorService;
import com.szh.monitor.service.SqlExecuteRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 执行器总调度
 */
@Component
public class ExecutorScheduler {
    @Autowired
    private List<ExecutorService> executorServices;
    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    @Autowired
    private SqlExecuteRuleService sqlExecuteRuleService;
    @Autowired
    public ExecutorScheduler(List<ExecutorService> executorServices) {
        this.executorServices = executorServices;
    }

    @Async("executorSQL")
    @Scheduled(fixedRate=240_000)
    public void executor() {
        Set<String> environmentNameList = executeJDBCContext.getJBDCTemplate().keySet();
        for (String environmentName : environmentNameList) {
            executeJDBCContext.addSqlExecuteRule(environmentName,sqlExecuteRuleService.findByEnvironmentName(environmentName));
        }
        executorServices.forEach(ExecutorService::execute);
    }
}
