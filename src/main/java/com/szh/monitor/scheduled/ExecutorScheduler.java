package com.szh.monitor.scheduled;

import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.service.ExecutorService;
import com.szh.monitor.service.SqlExecuteRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 执行器总调度
 */
@Component
public class ExecutorScheduler {
    Logger logger = LoggerFactory.getLogger(ExecutorScheduler.class);
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
    @Transactional("primaryTransactionManager")
    public void executor() {
        Set<String> environmentNameList = executeJDBCContext.getJBDCTemplate().keySet();
        for (String environmentName : environmentNameList) {
            executeJDBCContext.addSqlExecuteRule(environmentName,sqlExecuteRuleService.findByEnvironmentName(environmentName));
        }
        for (ExecutorService executorService : executorServices) {
            executeJDBCContext.getJBDCTemplate().keySet().forEach(env -> {
                try {
                    executorService.executeSingle(env);
                } catch (Exception e) {
                    logger.error("环境 {} 执行异常，已隔离处理: {}", env, e.getMessage());
                }
            });
        }
    }
}
