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
    public static Integer getCurrYYYYMMDD(){
        return Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }
    public static Integer getCurrHHMMSS(){
        return Integer.parseInt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
    }


    @Override
    @Transactional
    public void saveOrUpdate(String environmentName, String name) {
        SqlExecuteLog sqlExecuteLog = getBaseMapper().findEnvironmentNameAndFileName(environmentName,name,getCurrYYYYMMDD());
        if(sqlExecuteLog!=null){
            //如果执行成功 且存在失败次数，就清0并设置重置时间，以防失败重试一批文件时，又再次因为网络问题部分成功部分失败的情况
            if(sqlExecuteLog.getFailedCount()>0){
                sqlExecuteLog.setFailedCount(0);
                sqlExecuteLog.setFailedCountResetTime(getCurrHHMMSS());
            }
            sqlExecuteLog.setCount(sqlExecuteLog.getCount()+1);
        }else{
            sqlExecuteLog = new SqlExecuteLog();
            sqlExecuteLog.setEnvironmentName(environmentName);
            sqlExecuteLog.setSqlFileName(name);
            sqlExecuteLog.setCount(1);
            sqlExecuteLog.setFailedCount(0);
            sqlExecuteLog.setExecuteDate(getCurrYYYYMMDD());
        }
        this.saveOrUpdate(sqlExecuteLog);
    }

    @Override
    public List<SqlExecuteLog> findEnvironmentName(String environmentName) {
        return getBaseMapper().findEnvironmentName(environmentName,getCurrYYYYMMDD());
    }

    @Override
    public List<SqlExecuteLog> findEnvironmentNameAndFailedCountGt0(String environmentName) {
        return getBaseMapper().findEnvironmentNameAndFailedCountGt0(environmentName,getCurrYYYYMMDD());
    }

    @Override
    public int findMaxFailedCount(String environmentName) {
        Integer maxFailedCount = getBaseMapper().findMaxFailedCount(environmentName, getCurrYYYYMMDD());
        return maxFailedCount==null?0:maxFailedCount;
    }

    @Override
    public void resetFailedCount(String environmentName) {
        getBaseMapper().resetFailedCount(environmentName,getCurrYYYYMMDD(),getCurrHHMMSS());
    }

    @Override
    public void clear() {
        int clearCount = getBaseMapper().clear(getCurrYYYYMMDD());
        logger.info("删除执行记录条数{}",clearCount);
    }
}
