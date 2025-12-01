package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.mapper.SqlExecuteLogMapper;
import com.szh.monitor.service.SqlExecuteLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SqlExecuteLogServiceImp extends ServiceImpl<SqlExecuteLogMapper, SqlExecuteLog> implements SqlExecuteLogService {
    Logger logger = LoggerFactory.getLogger(SqlExecuteLogServiceImp.class);
    private static final Integer currYYYYMMDD = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    private static final Integer currHHMMSS = Integer.parseInt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));

    @Override
    @Transactional
    public void saveOrUpdate(String environmentName, String name) {
        SqlExecuteLog sqlExecuteLog = getBaseMapper().findEnvironmentNameAndFileName(environmentName,name,currYYYYMMDD);
        if(sqlExecuteLog!=null){
            sqlExecuteLog.setCount(sqlExecuteLog.getCount()+1);
        }else{
            sqlExecuteLog = new SqlExecuteLog();
            sqlExecuteLog.setEnvironmentName(environmentName);
            sqlExecuteLog.setSqlFileName(name);
            sqlExecuteLog.setCount(1);
            sqlExecuteLog.setExecuteDate(currYYYYMMDD);
        }
        this.saveOrUpdate(sqlExecuteLog);
    }

    @Override
    public List<SqlExecuteLog> findEnvironmentName(String environmentName) {
        return getBaseMapper().findEnvironmentName(environmentName,currYYYYMMDD);
    }

    @Override
    public List<SqlExecuteLog> findEnvironmentNameAndFailedCountGt0(String environmentName) {
        return getBaseMapper().findEnvironmentNameAndFailedCountGt0(environmentName,currYYYYMMDD);
    }

    @Override
    public int findMaxFailedCount(String environmentName) {
        Integer maxFailedCount = getBaseMapper().findMaxFailedCount(environmentName, currYYYYMMDD);
        return maxFailedCount==null?0:maxFailedCount;
    }

    @Override
    public void resetFailedCount(String environmentName) {
        getBaseMapper().resetFailedCount(environmentName,currYYYYMMDD,currHHMMSS);
    }

    @Override
    public void clear() {
        int clearCount = getBaseMapper().clear(currYYYYMMDD);
        logger.info("删除执行记录条数{}",clearCount);
    }
}
