package com.szh.monitor.service.impl;

import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.context.SpringContextUtil;
import com.szh.monitor.enums.MsgType;
import com.szh.monitor.form.MsgForm;
import com.szh.monitor.service.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL类型的检测
 */
@Service
public class SqlExecutorService implements ExecutorService {
    Logger logger = LoggerFactory.getLogger(SqlExecutorService.class);
    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    @Value("${app.sql-dir}")
    private Resource sqlDir;
    @Value("${app.sql-absolute-dir}")
    private String absoluteSqlDir;

    @Value("${app.wechat-webhook}")
    private String webhookUrl;

    public String getWebhookUrl() {
        return webhookUrl;
    }

    @Autowired
    private SendDispatchService sendDispatchService;

    public void executeSqlFiles(String environmentName, String jdbcTemplateName) {
        File directory = null;
        try {
            //初始化SQL文件夹
            if(StringUtils.hasText(absoluteSqlDir)){
                directory = new File(absoluteSqlDir);
            }else{
                directory = sqlDir.getFile();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!directory.exists() || !directory.isDirectory()) {
            throw new RuntimeException("SQL目录不存在: " + sqlDir);
        }
        //拉取文件夹下的SQL文件
        File[] sqlFiles = directory.listFiles((dir, name) -> name.endsWith(".sql"));
        if (sqlFiles == null) return;
        //获取SQL连接
        JdbcTemplate jdbcTemplate = SpringContextUtil.getBean(jdbcTemplateName, JdbcTemplate.class);
        logger.info("当前环境：{} 开始执行SQL文件：{}", environmentName, Arrays.stream(sqlFiles).map(File::getName).collect(Collectors.joining(",")));
        //全局异常标识，只要其中一个文件执行出错，则标记为true
        boolean exception = false;
        //记录执行成功的SQL文件和执行失败的SQL文件
        List<String> successSQLFileName = new ArrayList<>();
        List<String> failSQLFileName = new ArrayList<>();
        //遍历从SQL文件夹获取的文件
        for (File sqlFile : sqlFiles) {
            String sql = null;
            try {
                sql = new String(FileCopyUtils.copyToByteArray(sqlFile), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
                if (!results.isEmpty()) {
                    sendDispatchService.sendMsg(MsgForm.builder(MsgType.ERROR, getTitle(), environmentName),(StringBuilder appendMsg)->{
                        appendMsg.append("文件: ").append(sqlFile.getName()).append("\n\n");

                        // 添加表头
                        if (!results.isEmpty()) {
                            Map<String, Object> firstRow = results.get(0);
                            appendMsg.append(String.join("\t", firstRow.keySet())).append("\n");
                        }

                        // 添加数据行
                        for (Map<String, Object> row : results) {
                            appendMsg.append(String.join("\t", row.values().stream()
                                    .map(Object::toString)
                                    .toArray(String[]::new))
                            ).append("\n");
                        }
                    });
                }
                successSQLFileName.add(sqlFile.getName());
            } catch (Exception e) {
                failSQLFileName.add(sqlFile.getName());
                logger.error("执行SQL文件【{}】出错,跳过，执行下一个文件", sqlFile.getName(), e);
                exception = true;
            }
        }
        if (exception) {
            logger.info("执行成功的SQL文件 {}", successSQLFileName);
            logger.info("执行失败的SQL文件 {}", failSQLFileName);
            throw new RuntimeException(MessageFormat.format("环境{0}执行SQL出现异常", environmentName));
        }
        logger.info("全部SQL文件执行完成");
    }

    @Override
    public void execute() {
        final String[] currEnvironmentName = {null};
        try {
            executeJDBCContext.getJBDCTemplate().forEach((environmentName, jdbcTemplateName) -> {
                currEnvironmentName[0] = environmentName;
                executeSqlFiles(environmentName, jdbcTemplateName);
                //成功执行则清0错误计数
                executeJDBCContext.clearFailedCount(environmentName);
            });

        } catch (Exception e){
            //错误计数
            int failedCount = executeJDBCContext.addFailedCount(currEnvironmentName[0]);
            if(failedCount==3||failedCount==6||failedCount==12||failedCount==24){
                sendDispatchService.sendMsg(MsgForm.builder(MsgType.ERROR, "数据脚本执行异常", currEnvironmentName[0]),(StringBuilder appendMsg)->{
                    appendMsg.append(MessageFormat.format("执行失败{0}次 请检查网络环境",failedCount));
                });
            }
            logger.error(MessageFormat.format("当前环境：{0} SQL任务执行失败 累计失败次数{1}",currEnvironmentName,failedCount),e);
        }
    }

    @Override
    public String getTitle() {
        return "SQL预警通知";
    }
}
