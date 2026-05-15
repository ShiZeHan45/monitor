package com.szh.monitor.scheduled;

import com.szh.monitor.service.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 执行器总调度
 */
@Component
public class ExecutorScheduler {
    @Autowired
    private List<ExecutorService> executorServices;

    @Autowired
    public ExecutorScheduler(List<ExecutorService> executorServices) {
        this.executorServices = executorServices;
    }

    @Async("executorSQL")
    @Scheduled(fixedRate=1_000)
    public void executor() {
        executorServices.forEach(ExecutorService::execute);
    }
}
