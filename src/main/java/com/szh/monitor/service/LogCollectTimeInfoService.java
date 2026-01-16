package com.szh.monitor.service;

public interface LogCollectTimeInfoService {
    void initLastTSMAP();

    void updateOrSave(String environmentName, String name, long maxTs);
}
