package com.szh.monitor.service;

import java.util.List;
import java.util.Map;

public interface LogCollectTimeInfoService {
    void initLastTSMAP();

    void updateOrSave(String environmentName, String name, long maxTs);

    void updateOrSave(String environmentName, String name, long maxTs, long collectCount);

    List<Map<String, Object>> getEnvironmentCollectStats();
}
