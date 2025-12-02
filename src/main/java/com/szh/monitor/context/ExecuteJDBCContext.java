package com.szh.monitor.context;

import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.service.SqlExecuteLogService;
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
    private SQLConfig SQLConfig;
    @Autowired
    private SqlExecuteLogService sqlExecuteLogService;

    Logger logger = LoggerFactory.getLogger(SqlExecutorService.class);
    //缓存各环境的jdbcTemplate
    private Map<String, String> jdbcTemplateMap = new HashMap<>();

    //各环境无需再次执行的SQL文件
    private Map<String, List<FileCountInfo>> executeFileCountInfo = new HashMap<>();

    public ExecuteJDBCContext() {
    }


    /**
     * 判断SQL文件是否可以执行
     * @param environmentName
     * @param sqlFileName
     * @return
     */
    public boolean executeAble(String environmentName, String sqlFileName){
//        List<FileCountInfo> fileCountInfos = executeFileCountInfo.getOrDefault(environmentName, null);
        List<SqlExecuteLog> sqlExecuteLogs = sqlExecuteLogService.findEnvironmentName(environmentName);
        int currDate = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        if(!CollectionUtils.isEmpty(SQLConfig.getUnLimitCheckFiles())){
            //如果匹配上了，就直接响应可以执行，否则还要再过一道拦截
            if(SQLConfig.getUnLimitCheckFiles().stream().anyMatch(x -> x.equals(sqlFileName))){
                logger.debug("{} 该SQL文件执行次数无上限",sqlFileName);
                return true;
            }
        }
        if (StringUtils.hasText(sqlFileName)&&!CollectionUtils.isEmpty(sqlExecuteLogs)) {
            SqlExecuteLog fileCountInfo = sqlExecuteLogs.stream().filter(x -> x.getExecuteDate().equals(currDate) && x.getSqlFileName().equals(sqlFileName)).findFirst().orElse(null);
            if(fileCountInfo==null){
                //首次执行 匹配不上都为可执行
                return true;
            }
            logger.debug("{} 该SQL文件执行次数{} 阈值为{}",sqlFileName,fileCountInfo.getCount(),SQLConfig.getCheckLimit());
            return fileCountInfo.getCount()< SQLConfig.getCheckLimit();
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
                    && x.getFileName().equals(sqlFileName)).findFirst().orElse(new FileCountInfo(currDate, sqlFileName, 0,fileCountInfos));
            fileCountInfo.setCount(fileCountInfo.getCount() + 1);
            logger.debug("{}  该SQL文件累计执行成功了{}次",sqlFileName,fileCountInfo.getCount());
            //移除掉历史数据
            fileCountInfos.removeIf(x->x.getDate()<currDate);
            executeFileCountInfo.put(environmentName,fileCountInfos);
        }
    }


    /**
     * 清除某个环境的缓存
     * @param environmentName 环境名称
     */
    public void clearFailedCount(String environmentName) {
        sqlExecuteLogService.resetFailedCount(environmentName);
//        //充值执行失败次数计数
//        failedToObtainConnectionCount.remove(environmentName);
//        //清除执行失败文件缓存
//        failedFilesMap.remove(environmentName);
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
        List<SqlExecuteLog> failedInfos = sqlExecuteLogService.findEnvironmentNameAndFailedCountGt0(environmentName);
        List<SqlExecuteLog> sqlExecuteLogs=new ArrayList<>();
        if (CollectionUtils.isEmpty(failedInfos)) {
            for (String failedFile : failedFiles) {
                SqlExecuteLog sqlExecuteLog = new SqlExecuteLog();
                sqlExecuteLog.setEnvironmentName(environmentName);
                sqlExecuteLog.setSqlFileName(failedFile);
                sqlExecuteLog.setFailedCount(1);
                sqlExecuteLogs.add(sqlExecuteLog);
            }
        } else {
            for (String failedFile : failedFiles) {
                SqlExecuteLog sqlExecuteLog = failedInfos.stream().filter(x -> x.getSqlFileName().equals(failedFile)).findAny().orElse(null);
                if(sqlExecuteLog==null){
                    sqlExecuteLog = new SqlExecuteLog();
                    sqlExecuteLog.setEnvironmentName(environmentName);
                    sqlExecuteLog.setSqlFileName(failedFile);
                    sqlExecuteLog.setFailedCount(1);
                }else{
                    sqlExecuteLog.setFailedCount((sqlExecuteLog.getFailedCount()==null?0:sqlExecuteLog.getFailedCount())+1);
                }
            }
        }
        sqlExecuteLogService.saveOrUpdateBatch(sqlExecuteLogs);
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
        return sqlExecuteLogService.findMaxFailedCount(environmentName);
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
        List<SqlExecuteLog> failedInfos = sqlExecuteLogService.findEnvironmentNameAndFailedCountGt0(environmentName);
        if (CollectionUtils.isEmpty(failedInfos)) {
            return null;
        }
        return failedInfos.stream().map(SqlExecuteLog::getSqlFileName).collect(Collectors.toList());
    }

    @Data
    public static class FileCountInfo {
        private Integer date;
        private String fileName;
        private Integer count;

        public FileCountInfo() {
        }

        public FileCountInfo(Integer date, String fileName, Integer count,List<FileCountInfo> fileCountInfos) {
            this.date = date;
            this.fileName = fileName;
            this.count = count;
            fileCountInfos.add(this);
        }
    }

}
