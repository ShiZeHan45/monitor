package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.context.SpringContextUtil;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.LogCollectTimeInfo;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.entity.SqlExecuteRule;
import com.szh.monitor.mapper.GrafanaDataSourceMapper;
import com.szh.monitor.mapper.LogCollectTimeInfoMapper;
import com.szh.monitor.mapper.MsgSendLogMapper;
import com.szh.monitor.mapper.SqlDataSourceMapper;
import com.szh.monitor.mapper.SqlExecuteLogMapper;
import com.szh.monitor.mapper.SqlExecuteRuleMapper;
import com.szh.monitor.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MonitorController {

    private static final Logger logger = LoggerFactory.getLogger(MonitorController.class);

    @Autowired
    private MsgSendLogMapper msgSendLogMapper;

    @Autowired
    private SqlExecuteLogMapper sqlExecuteLogMapper;

    @Autowired
    private SqlExecuteRuleMapper sqlExecuteRuleMapper;

    @Autowired
    private LogCollectTimeInfoMapper logCollectTimeInfoMapper;

    @Autowired
    private ExecuteJDBCContext executeJDBCContext;

    @Autowired
    private SQLConfig sqlConfig;

    @Autowired
    private GrafanaDataSourceMapper grafanaDataSourceMapper;

    @Autowired
    private SqlDataSourceMapper sqlDataSourceMapper;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/stats/today")
    public ResponseEntity<Map<String, Object>> getTodayStats(HttpServletRequest request) {
        operationLogService.logVisit(request);
        
        Map<String, Object> result = new HashMap<>();

        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));

        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString()));

        Map<String, Long> pushStats = todayLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getEnvironmentName(),
                        Collectors.counting()
                ));

        result.put("todayPushStats", pushStats);
        result.put("todayPushTotal", todayLogs.size());

        List<SqlExecuteLog> sqlLogs = sqlExecuteLogMapper.selectList(new LambdaQueryWrapper<SqlExecuteLog>()
                .eq(SqlExecuteLog::getDate, today));

        Map<String, Map<String, Object>> sqlStats = new HashMap<>();
        for (SqlExecuteLog log : sqlLogs) {
            String env = log.getEnvironmentName();
            sqlStats.computeIfAbsent(env, k -> new HashMap<>());
            sqlStats.get(env).merge("executeCount", 1, Integer::sum);
            if (log.getError() != null && !log.getError().isEmpty()) {
                sqlStats.get(env).merge("failedCount", 1, Integer::sum);
            }
        }

        result.put("sqlExecuteStats", sqlStats);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/push-by-env")
    public ResponseEntity<List<Map<String, Object>>> getPushStatsByEnv() {
        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString()));

        Map<String, Map<String, Object>> stats = new HashMap<>();

        for (MsgSendLog log : todayLogs) {
            String env = log.getEnvironmentName();
            stats.computeIfAbsent(env, k -> new HashMap<>());
            
            String content = (log.getContent() != null) ? log.getContent().toLowerCase() : "";
            if (content.contains("sql")) {
                stats.get(env).merge("sqlPushCount", 1, Integer::sum);
            }
            if (content.contains("日志")) {
                stats.get(env).merge("logPushCount", 1, Integer::sum);
            }
        }

        List<Map<String, Object>> result = stats.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("environmentName", entry.getKey());
                    item.put("sqlPushCount", entry.getValue().getOrDefault("sqlPushCount", 0));
                    item.put("logPushCount", entry.getValue().getOrDefault("logPushCount", 0));
                    return item;
                })
                .collect(Collectors.toList());

        Collections.sort(result, Comparator.comparing(m -> (String) m.get("environmentName")));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/environment")
    public ResponseEntity<List<Map<String, Object>>> getEnvironmentStats() {
        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));

        List<SqlExecuteLog> sqlLogs = sqlExecuteLogMapper.selectList(new LambdaQueryWrapper<SqlExecuteLog>()
                .eq(SqlExecuteLog::getDate, today));

        Map<String, Map<String, Object>> envMap = new HashMap<>();

        for (SqlExecuteLog log : sqlLogs) {
            String env = log.getEnvironmentName();
            envMap.computeIfAbsent(env, k -> new HashMap<>());
            envMap.get(env).merge("executeCount", 1, Integer::sum);
            if (log.getError() != null && !log.getError().isEmpty()) {
                envMap.get(env).merge("failedCount", 1, Integer::sum);
            }
        }

        List<Map<String, Object>> result = envMap.values().stream()
                .filter(stats -> {
                    Object count = stats.get("executeCount");
                    return count instanceof Integer && (Integer) count > 0;
                })
                .collect(Collectors.toList());

        Collections.sort(result, Comparator.comparing(m -> (String) m.get("environmentName")));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/push-records")
    public ResponseEntity<Map<String, Object>> getPushRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String environmentName) {

        Page<MsgSendLog> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<MsgSendLog> queryWrapper = new LambdaQueryWrapper<>();

        if (date != null && !date.isEmpty()) {
            queryWrapper.like(MsgSendLog::getCreateTime, date);
        }

        if (environmentName != null && !environmentName.isEmpty()) {
            queryWrapper.eq(MsgSendLog::getEnvironmentName, environmentName);
        }

        queryWrapper.orderByDesc(MsgSendLog::getCreateTime);

        IPage<MsgSendLog> result = msgSendLogMapper.selectPage(pageInfo, queryWrapper);

        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/sql-rules")
    public ResponseEntity<List<SqlExecuteRule>> getSqlRules() {
        List<SqlExecuteRule> rules = sqlExecuteRuleMapper.selectList(null);
        Collections.sort(rules, Comparator.comparing(SqlExecuteRule::getEnvironmentName));
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/sql-rules/{id}")
    public ResponseEntity<SqlExecuteRule> getSqlRule(@PathVariable int id) {
        SqlExecuteRule rule = sqlExecuteRuleMapper.selectById(id);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rule);
    }

    @PostMapping("/sql-rules")
    public ResponseEntity<Map<String, Object>> createSqlRule(@RequestBody SqlExecuteRule rule, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            sqlExecuteRuleMapper.insert(rule);
            operationLogService.logCreate("SQL规则", rule.getId(), "创建SQL规则: " + rule.getSqlFileName(), request);
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("data", rule);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("创建SQL规则失败", e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @PutMapping("/sql-rules/{id}")
    public ResponseEntity<Map<String, Object>> updateSqlRule(@PathVariable int id, @RequestBody SqlExecuteRule rule, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            SqlExecuteRule existing = sqlExecuteRuleMapper.selectById(id);
            if (existing == null) {
                result.put("success", false);
                result.put("message", "规则不存在");
                return ResponseEntity.ok(result);
            }
            
            rule.setId(id);
            sqlExecuteRuleMapper.updateById(rule);
            operationLogService.logEdit("SQL规则", id, "修改SQL规则: " + rule.getSqlFileName(), request);
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新SQL规则失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @DeleteMapping("/sql-rules/{id}")
    public ResponseEntity<Map<String, Object>> deleteSqlRule(@PathVariable int id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            SqlExecuteRule rule = sqlExecuteRuleMapper.selectById(id);
            if (rule == null) {
                result.put("success", false);
                result.put("message", "规则不存在");
                return ResponseEntity.ok(result);
            }
            
            sqlExecuteRuleMapper.deleteById(id);
            operationLogService.logDelete("SQL规则", id, "删除SQL规则: " + rule.getSqlFileName(), request);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("删除SQL规则失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/sql-files")
    public ResponseEntity<List<Map<String, Object>>> getSqlFiles() {
        List<Map<String, Object>> files = new ArrayList<>();
        File dir = new File(sqlConfig.getSqlFilePath());
        if (dir.exists() && dir.isDirectory()) {
            File[] fileList = dir.listFiles((d, name) -> name.endsWith(".sql"));
            if (fileList != null) {
                for (File file : fileList) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("size", file.length());
                    fileInfo.put("lastModified", file.lastModified());
                    files.add(fileInfo);
                }
            }
        }
        Collections.sort(files, Comparator.comparing(m -> (String) m.get("name")));
        return ResponseEntity.ok(files);
    }

    @GetMapping("/sql-files/{fileName}")
    public ResponseEntity<Map<String, Object>> getSqlFileContent(@PathVariable String fileName) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path path = Paths.get(sqlConfig.getSqlFilePath(), fileName);
            String content = Files.readString(path, StandardCharsets.UTF_8);
            result.put("success", true);
            result.put("content", content);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("读取SQL文件失败", e);
            result.put("success", false);
            result.put("message", "读取失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping("/sql-files/upload")
    public ResponseEntity<Map<String, Object>> uploadSqlFiles(@RequestParam("files") MultipartFile[] files, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.endsWith(".sql")) {
                failedFiles.add(fileName + " - 不是SQL文件");
                continue;
            }

            try {
                Path path = Paths.get(sqlConfig.getSqlFilePath(), fileName);
                Files.write(path, file.getBytes());
                successCount++;
                operationLogService.logCreate("SQL文件", null, "上传SQL文件: " + fileName, request);
            } catch (IOException e) {
                logger.error("上传SQL文件失败: {}", fileName, e);
                failedFiles.add(fileName + " - " + e.getMessage());
            }
        }

        result.put("success", true);
        result.put("message", String.format("上传完成，成功 %d 个，失败 %d 个", successCount, failedFiles.size()));
        if (!failedFiles.isEmpty()) {
            result.put("failedFiles", failedFiles);
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/sql-files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteSqlFile(@PathVariable String fileName, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Path path = Paths.get(sqlConfig.getSqlFilePath(), fileName);
            if (Files.exists(path)) {
                Files.delete(path);
                operationLogService.logDelete("SQL文件", null, "删除SQL文件: " + fileName, request);
                result.put("success", true);
                result.put("message", "删除成功");
            } else {
                result.put("success", false);
                result.put("message", "文件不存在");
            }
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("删除SQL文件失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/stats/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> result = new HashMap<>();

        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(
            new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString())
        );
        result.put("todayPushCount", todayLogs.size());

        int todaySqlExceptionCount = (int) todayLogs.stream()
            .filter(log -> log.getContent() != null && log.getContent().toLowerCase().contains("sql"))
            .count();
        result.put("todaySqlExceptionCount", todaySqlExceptionCount);

        int todayLogExceptionCount = (int) todayLogs.stream()
            .filter(log -> log.getContent() != null && log.getContent().contains("日志"))
            .count();
        result.put("todayLogExceptionCount", todayLogExceptionCount);

        List<GrafanaDataSource> grafanaDataSources = grafanaDataSourceMapper.selectList(null);
        List<SqlDataSource> sqlDataSources = sqlDataSourceMapper.selectList(null);
        
        int onlineGrafanaCount = (int) grafanaDataSources.stream()
            .filter(ds -> ds.getIsOnline() != null && ds.getIsOnline() == 1)
            .count();
        int onlineSqlCount = (int) sqlDataSources.stream()
            .filter(ds -> ds.getIsOnline() != null && ds.getIsOnline() == 1)
            .count();
        result.put("onlineDataSourceCount", onlineGrafanaCount + onlineSqlCount);
        result.put("totalDataSourceCount", grafanaDataSources.size() + sqlDataSources.size());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/datasource-status")
    public ResponseEntity<List<Map<String, Object>>> getDataSourceStatus() {
        List<Map<String, Object>> result = new ArrayList<>();

        List<GrafanaDataSource> grafanaDataSources = grafanaDataSourceMapper.selectList(null);
        for (GrafanaDataSource ds : grafanaDataSources) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", ds.getEnvironmentName());
            item.put("type", "Grafana");
            item.put("isOnline", ds.getIsOnline() != null && ds.getIsOnline() == 1);
            item.put("enabled", ds.getEnabled() != null && ds.getEnabled() == 1);
            item.put("lastCheckTime", ds.getLastCheckTime());
            result.add(item);
        }

        List<SqlDataSource> sqlDataSources = sqlDataSourceMapper.selectList(null);
        for (SqlDataSource ds : sqlDataSources) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", ds.getEnvironmentName());
            item.put("type", "SQL");
            item.put("isOnline", ds.getIsOnline() != null && ds.getIsOnline() == 1);
            item.put("enabled", ds.getEnabled() != null && ds.getEnabled() == 1);
            item.put("lastCheckTime", ds.getLastCheckTime());
            result.add(item);
        }

        result.sort((m1, m2) -> {
            String type1 = (String) m1.get("type");
            String type2 = (String) m2.get("type");
            Boolean online1 = (Boolean) m1.get("isOnline");
            Boolean online2 = (Boolean) m2.get("isOnline");
            String name1 = (String) m1.get("name");
            String name2 = (String) m2.get("name");

            int typeCompare = type1.compareTo(type2);
            if (typeCompare != 0) {
                return typeCompare;
            }

            int onlineCompare = Boolean.compare(online2, online1);
            if (onlineCompare != 0) {
                return onlineCompare;
            }

            return name1.compareTo(name2);
        });

        return ResponseEntity.ok(result);
    }

    @PostMapping("/sql-debug/execute")
    public ResponseEntity<Map<String, Object>> executeSqlDebug(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        String environmentName = request.get("environment");
        String sql = request.get("sql");

        if (environmentName == null || environmentName.trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "请选择数据源");
            return ResponseEntity.badRequest().body(result);
        }

        if (sql == null || sql.trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "SQL语句不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        Map<String, String> jdbcTemplates = executeJDBCContext.getJBDCTemplate();
        String jdbcTemplateName = jdbcTemplates.get(environmentName);

        if (jdbcTemplateName == null) {
            result.put("success", false);
            result.put("error", "数据源不存在: " + environmentName);
            return ResponseEntity.badRequest().body(result);
        }

        try {
            JdbcTemplate jdbcTemplate = SpringContextUtil.getBean(jdbcTemplateName, JdbcTemplate.class);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            result.put("success", true);
            result.put("columns", results.isEmpty() ? Collections.emptyList() : new ArrayList<>(results.get(0).keySet()));
            result.put("rows", results);
            result.put("rowCount", results.size());
            result.put("message", "查询成功，返回 " + results.size() + " 条记录");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("执行SQL调试失败: environment={}, sql={}", environmentName, sql, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "SQL执行失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}
