package com.szh.monitor.context;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExecuteJDBCContext {
    //缓存各环境的jdbcTemplate
    private Map<String, String> jdbcTemplateMap = new HashMap<>();

    //数据库连接获取失败次数
    private Map<String, Integer> failedToObtainConnectionCount = new HashMap<>();
    //缓存各环境执行失败的文件集合
    private Map<String, List<String>> failedFilesMap = new HashMap<>();

    public ExecuteJDBCContext() {
    }

    /**
     * 清除某个环境的缓存
     * @param environmentName 环境名称
     */
    public void clearFailedCount(String environmentName) {
        //充值执行失败次数计数
        failedToObtainConnectionCount.remove(environmentName);
        //清除执行失败文件缓存
        failedFilesMap.remove(environmentName);
    }

    /**
     * 根据环境增量缓存执行失败的SQL文件名
     * @param environmentName 环境名称
     * @param failedFiles 失败文件名
     */
    public void addFailFiles(String environmentName, List<String> failedFiles) {
        if (CollectionUtils.isEmpty(failedFiles)) {
            return;
        }
        List<String> files = failedFilesMap.get(environmentName);
        if (!CollectionUtils.isEmpty(files)) {
            failedFilesMap.put(environmentName, failedFiles);
        }else{
            List<String> newFiles = failedFiles.stream().filter(x -> !files.contains(x)).collect(Collectors.toList());
            if(!CollectionUtils.isEmpty(newFiles)){
                files.addAll(newFiles);
                failedFilesMap.put(environmentName, files);
            }
        }
    }

    /**
     * 根据环境名称计数执行失败次数  每次+1
     * 增量缓存执行失败的SQL文件名
     * @param environmentName 环境名称
     * @param failFiles 执行失败SQL文件集合
     * @return 计数后失败次数
     */
    public int addFailedCount(String environmentName, List<String> failFiles) {
        if (!StringUtils.hasText(environmentName)) {
            return 0;
        }
        addFailFiles(environmentName, failFiles);
        failedToObtainConnectionCount.put(environmentName, failedToObtainConnectionCount.getOrDefault(environmentName, 0) + 1);
        return failedToObtainConnectionCount.get(environmentName);
    }

    /**
     * 新增一个jdbcTemplate
     * @param environmentName 环境名称
     * @param jdbcTemplateName jdbcTemplate bean name
     */

    public void addJdbcTemplate(String environmentName, String jdbcTemplateName) {
        this.jdbcTemplateMap.put(environmentName, jdbcTemplateName);
    }

    public Map<String, String> getJBDCTemplate() {
        return this.jdbcTemplateMap;
    }

    public List<String> getFailFiles(String environmentName) {
        if (CollectionUtils.isEmpty(this.failedFilesMap)) {
            return null;
        }
        return this.failedFilesMap.get(environmentName);
    }

}
