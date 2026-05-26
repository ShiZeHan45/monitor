package com.szh.monitor.service.impl;

import com.szh.monitor.service.WatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DispatchLogService {

    private List<WatchService> watchServices;

    @Autowired
    public DispatchLogService(List<WatchService> watchServices) {
        this.watchServices = watchServices;
    }


    public void startWatching() {
        for (WatchService watchService : watchServices) {
            new Thread(watchService::watchFile, watchService.getClass() + "-watcher-thread").start();
        }
    }


}
