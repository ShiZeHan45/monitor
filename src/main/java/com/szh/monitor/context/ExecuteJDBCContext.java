package com.szh.monitor.context;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
public class ExecuteJDBCContext {
    private Map<String, String> jdbcTemplateMap= new HashMap<>();

    //数据库连接获取失败次数
    private Map<String, Integer> failedToObtainConnectionCount = new HashMap<>();

    public ExecuteJDBCContext() {
    }

    public void clearFailedCount(String environmentName){
        failedToObtainConnectionCount.remove(environmentName);
    }
    public int addFailedCount(String environmentName){
        if(!StringUtils.hasText(environmentName)){
            return 0;
        }
        failedToObtainConnectionCount.put(environmentName,failedToObtainConnectionCount.getOrDefault(environmentName,0)+1);
        return failedToObtainConnectionCount.get(environmentName);
    }


    public void addJdbcTemplate(String environmentName, String jdbcTemplateName) {
        this.jdbcTemplateMap.put(environmentName, jdbcTemplateName);
    }

    public  Map<String, String> getJBDCTemplate() {
        return this.jdbcTemplateMap;
    }

}
