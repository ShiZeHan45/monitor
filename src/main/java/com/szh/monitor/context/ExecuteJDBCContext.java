package com.szh.monitor.context;

import com.szh.monitor.config.BaseConfig;
import com.szh.monitor.service.impl.SqlExecutorService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExecuteJDBCContext {
    @Autowired
    private BaseConfig baseConfig;

    Logger logger = LoggerFactory.getLogger(SqlExecutorService.class);
    //缓存各环境的jdbcTemplate
    private Map<String, String> jdbcTemplateMap = new HashMap<>();

    //数据库连接获取失败次数
    private Map<String, Integer> failedToObtainConnectionCount = new HashMap<>();

    //各环境无需再次执行的SQL文件
    private Map<String, List<FileCountInfo>> executeFileCountInfo = new HashMap<>();
    //缓存各环境执行失败的文件集合
    private Map<String, List<String>> failedFilesMap = new HashMap<>();

    public ExecuteJDBCContext() {
    }


    /**
     * 判断SQL文件是否可以执行
     * @param environmentName
     * @param sqlFileName
     * @return
     */
    public boolean executeAble(String environmentName, String sqlFileName){
        int currDate = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        if(!CollectionUtils.isEmpty(baseConfig.getUnLimitCheckFiles())){
            logger.info("{} 该SQL文件执行次数无上限",sqlFileName);
            return baseConfig.getUnLimitCheckFiles().stream().anyMatch(x -> x.equals(sqlFileName));
        }
        if (StringUtils.hasText(sqlFileName)) {
            List<FileCountInfo> fileCountInfos = executeFileCountInfo.getOrDefault(environmentName, new ArrayList<>());
            boolean executeAble = fileCountInfos.stream().anyMatch(x -> x.getDate().equals(currDate) && x.getFileName().equals(sqlFileName)&& x.getCount() < baseConfig.getCheckLimit());
            logger.info("{} 该SQL文件执行次数没超上限{}",sqlFileName,baseConfig.getCheckLimit());
            return executeAble;
        }
        return true;
    }

    /**
     * 执行SQL计数
     * @param environmentName
     * @param sqlFileName
     */
    public void executeFileCount(String environmentName, String sqlFileName) {
        int currDate = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        if (StringUtils.hasText(sqlFileName)) {
            List<FileCountInfo> fileCountInfos = executeFileCountInfo.getOrDefault(environmentName, new ArrayList<>());
            FileCountInfo fileCountInfo = fileCountInfos.stream().filter(x -> x.getDate().equals(currDate)
                    && x.getFileName().equals(sqlFileName)).findFirst().orElse(new FileCountInfo(currDate, sqlFileName, 0));
            fileCountInfo.setCount(fileCountInfo.getCount() + 1);
            logger.info("{}  该SQL文件累计执行成功了{}次",sqlFileName,fileCountInfo.getCount());
            //移除掉历史数据
            fileCountInfos.removeIf(x->x.getDate()<currDate);
        }
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
        if (CollectionUtils.isEmpty(files)) {
            failedFilesMap.put(environmentName, failedFiles);
        } else {
            List<String> newFiles = failedFiles.stream().filter(x -> StringUtils.hasText(x) && !files.contains(x)).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(newFiles)) {
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
        logger.info("{}-jdbcTemplate 已载入", environmentName);
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

    @Data
    public static class FileCountInfo {
        private Integer date;
        private String fileName;
        private Integer count;

        public FileCountInfo() {
        }

        public FileCountInfo(Integer date, String fileName, Integer count) {
            this.date = date;
            this.fileName = fileName;
            this.count = count;
        }
    }

}
