package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.config.MonitorRules;
import com.szh.monitor.entity.LogCollectTimeInfo;
import com.szh.monitor.mapper.LogCollectTimeInfoMapper;
import com.szh.monitor.service.LogCollectTimeInfoService;
import com.szh.monitor.service.MsgSendLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class LogCollectTimeInfoServiceImp extends ServiceImpl<LogCollectTimeInfoMapper, LogCollectTimeInfo> implements LogCollectTimeInfoService {
    Logger logger = LoggerFactory.getLogger(LogCollectTimeInfoServiceImp.class);

    @Autowired
    @Lazy
    private GrafanaLogServiceImp grafanaLogServiceImp;

    @Override
    public void initLastTSMAP() {
        grafanaLogServiceImp.getGrafanaInfoMap().forEach((environmentName,grafanaInfo)->{
            for (MonitorRules monitor : grafanaInfo.getMonitors()) {
                LogCollectTimeInfo logCollectTimeInfo = getBaseMapper().findEnvironmentNameAndRuleName(environmentName,monitor.getName());
                if(logCollectTimeInfo!=null){
                    logger.info("从数据库中初始化日志采集起始时间戳 key:[{}] , lastTs[{}] 时间[{}]",environmentName+"_"+monitor.getName(),logCollectTimeInfo.getLastTs(),LocalDateTime.ofInstant(Instant.ofEpochMilli(logCollectTimeInfo.getLastTs()),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    grafanaLogServiceImp.initLastTsMap(environmentName+"_"+monitor.getName(),logCollectTimeInfo.getLastTs());
                }
            }
        });

    }

    @Override
    public void updateOrSave(String environmentName, String name, long maxTs) {
//        logger.debug("固化日志采集起始时间戳 key:[{}] , lastTs[{}] [{}]",environmentName+"_"+name,maxTs,LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        LogCollectTimeInfo logCollectTimeInfo = getBaseMapper().findEnvironmentNameAndRuleName(environmentName,name);
        if(logCollectTimeInfo==null){
            logCollectTimeInfo = new LogCollectTimeInfo();
            logCollectTimeInfo.setCreateTime(LocalDateTime.now());
            logCollectTimeInfo.setEnvironmentName(environmentName);
            logCollectTimeInfo.setRuleName(name);

        }
        logCollectTimeInfo.setLastTs(maxTs);
        logCollectTimeInfo.setLastTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs), ZoneId.systemDefault()));
        saveOrUpdate(logCollectTimeInfo);

    }
}
