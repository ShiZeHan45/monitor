package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.mapper.SqlExecuteLogMapper;
import com.szh.monitor.service.SqlExecuteLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SqlExecuteLogServiceImp extends ServiceImpl<SqlExecuteLogMapper, SqlExecuteLog> implements SqlExecuteLogService {

    @Override
    @Transactional
    public void saveOrUpdate(String environmentName, String name) {
        Integer yyyyMMdd = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        SqlExecuteLog sqlExecuteLog = getBaseMapper().findEnvironmentNameAndFileName(environmentName,name);
        if(sqlExecuteLog!=null){
            sqlExecuteLog.setCount(sqlExecuteLog.getCount()+1);
        }else{
            sqlExecuteLog = new SqlExecuteLog();
            sqlExecuteLog.setEnvironmentName(environmentName);
            sqlExecuteLog.setSqlFileName(name);
            sqlExecuteLog.setCount(1);
            sqlExecuteLog.setExecuteDate(yyyyMMdd);
        }
        this.saveOrUpdate(sqlExecuteLog);
    }

    @Override
    public List<SqlExecuteLog> findEnvironmentName(String environmentName) {
        Integer yyyyMMdd = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return getBaseMapper().findEnvironmentName(environmentName,yyyyMMdd);
    }
}
