package com.szh.monitor.service;

import com.szh.monitor.entity.LogCollectTimeInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LogCollectTimeInfoService {
    void initLastTSMAP();

    void updateOrSave(String environmentName, String name, long maxTs);

    void updateOrSave(String environmentName, String name, long maxTs, long collectCount);

    List<Map<String, Object>> getEnvironmentCollectStats();

    List<Map<String, Object>> getEnvironmentDailyCollectStats();

    void updateLastTs(Long id, LocalDateTime dateTime);

    List<LogCollectTimeInfo> getLastTsList();
}
