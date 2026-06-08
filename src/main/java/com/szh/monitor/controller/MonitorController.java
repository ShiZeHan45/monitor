package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.annotation.OperationLog;
import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.context.ExecuteJDBCContext;
import com.szh.monitor.context.SpringContextUtil;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.entity.SqlExecuteRule;
import com.szh.monitor.mapper.GrafanaDataSourceMapper;
import com.szh.monitor.mapper.MsgSendLogMapper;
import com.szh.monitor.mapper.SqlDataSourceMapper;
import com.szh.monitor.mapper.SqlExecuteLogMapper;
import com.szh.monitor.mapper.SqlExecuteRuleMapper;
import com.szh.monitor.service.GrafanaMonitorRuleService;
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
    private ExecuteJDBCContext executeJDBCContext;

    @Autowired
    private SQLConfig sqlConfig;

    @Autowired
    private GrafanaDataSourceMapper grafanaDataSourceMapper;

    @Autowired
    private SqlDataSourceMapper sqlDataSourceMapper;

    @Autowired
    private GrafanaMonitorRuleService grafanaMonitorRuleService;

    @GetMapping("/stats/today")
    public ResponseEntity<Map<String, Object>> getTodayStats() {
        Map<String, Object> result = new HashMap<>();

        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));

        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString()));

        Map<String, Long> pushStats = todayLogs.stream()
                .collect(Collectors.groupingBy(
                        log -> {
                            if (log.getContent() != null && log.getContent().contains("SQL")) {
                                return "sql";
                            } else if (log.getContent() != null && log.getContent().contains("日志")) {
                                return "log";
                            }
                            return "other";
                        },
                        Collectors.counting()
                ));

        List<SqlExecuteLog> sqlLogs = sqlExecuteLogMapper.selectList(new LambdaQueryWrapper<SqlExecuteLog>()
                .eq(SqlExecuteLog::getExecuteDate, today));

        Map<String, Map<String, Object>> sqlStats = new HashMap<>();
        for (SqlExecuteLog log : sqlLogs) {
            String env = log.getEnvironmentName();
            sqlStats.computeIfAbsent(env, k -> new HashMap<>());
            Map<String, Object> envStats = sqlStats.get(env);
            int count = log.getCount() != null ? log.getCount() : 0;
            int failed = log.getFailedCount() != null ? log.getFailedCount() : 0;
            envStats.merge("totalCount", count, (a, b) -> (Integer) a + (Integer) b);
            envStats.merge("failedCount", failed, (a, b) -> (Integer) a + (Integer) b);
        }

        sqlStats.entrySet().removeIf(entry -> {
            Map<String, Object> stats = entry.getValue();
            Object count = stats.get("totalCount");
            if (count instanceof Integer) {
                return (Integer) count <= 0;
            }
            return true;
        });

        result.put("pushTotal", todayLogs.size());
        result.put("pushStats", pushStats);
        result.put("sqlStats", sqlStats);
        result.put("date", LocalDate.now().toString());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/environment")
    public ResponseEntity<List<Map<String, Object>>> getEnvironmentStats() {
        int today = Integer.parseInt(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));

        List<SqlExecuteLog> logs = sqlExecuteLogMapper.selectList(new LambdaQueryWrapper<SqlExecuteLog>()
                .eq(SqlExecuteLog::getExecuteDate, today));

        Map<String, Map<String, Object>> envMap = new HashMap<>();
        for (SqlExecuteLog log : logs) {
            String env = log.getEnvironmentName();
            envMap.computeIfAbsent(env, k -> new HashMap<>());
            Map<String, Object> stats = envMap.get(env);
            stats.put("environmentName", env);
            int count = log.getCount() != null ? log.getCount() : 0;
            int failed = log.getFailedCount() != null ? log.getFailedCount() : 0;
            stats.merge("executeCount", count, (a, b) -> (Integer) a + (Integer) b);
            stats.merge("failedCount", failed, (a, b) -> (Integer) a + (Integer) b);
        }

        List<Map<String, Object>> result = envMap.values().stream()
                .filter(stats -> {
                    Object count = stats.get("executeCount");
                    if (count instanceof Integer) {
                        return (Integer) count > 0;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/push-by-env")
    public ResponseEntity<List<Map<String, Object>>> getPushStatsByEnvironment() {
        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString()));

        Map<String, Map<String, Object>> envMap = new LinkedHashMap<>();

        List<String> allEnvs = todayLogs.stream()
                .map(MsgSendLog::getEnvironmentName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        for (String env : allEnvs) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("environmentName", env);
            stats.put("sqlPushCount", 0L);
            stats.put("logPushCount", 0L);
            envMap.put(env, stats);
        }

        for (MsgSendLog log : todayLogs) {
            String env = log.getEnvironmentName();
            if (env == null) continue;

            Map<String, Object> stats = envMap.get(env);
            if (stats == null) {
                stats = new HashMap<>();
                stats.put("environmentName", env);
                stats.put("sqlPushCount", 0L);
                stats.put("logPushCount", 0L);
                envMap.put(env, stats);
            }

            String content = log.getContent() != null ? log.getContent().toLowerCase() : "";
            if (content.contains("sql")) {
                stats.merge("sqlPushCount", 1L, (a, b) -> (Long) a + (Long) b);
            }
            if (content.contains("日志")) {
                stats.merge("logPushCount", 1L, (a, b) -> (Long) a + (Long) b);
            }
        }

        return ResponseEntity.ok(new ArrayList<>(envMap.values()));
    }

    @Autowired
    private com.szh.monitor.service.MsgSendLogService msgSendLogService;

    @GetMapping("/push-records")
    public ResponseEntity<IPage<MsgSendLog>> getPushRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String environment) {

        Page<MsgSendLog> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<MsgSendLog> query = new LambdaQueryWrapper<>();

        if (date != null && !date.isEmpty()) {
            query.like(MsgSendLog::getCreateTime, date);
        }

        if (environment != null && !environment.isEmpty()) {
            query.eq(MsgSendLog::getEnvironmentName, environment);
        }

        query.orderByDesc(MsgSendLog::getCreateTime);
        IPage<MsgSendLog> result = msgSendLogMapper.selectPage(pageRequest, query);
        
        long total = msgSendLogService.countLogs(date, environment);
        result.setTotal(total);
        result.setPages((total + size - 1) / size);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/sql-rules")
    public ResponseEntity<List<SqlExecuteRule>> getSqlRules() {
        List<SqlExecuteRule> rules = sqlExecuteRuleMapper.selectList(null);
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/sql-rules/{id}")
    public ResponseEntity<SqlExecuteRule> getSqlRule(@PathVariable Long id) {
        SqlExecuteRule rule = sqlExecuteRuleMapper.selectById(id);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rule);
    }

    @PostMapping("/sql-rules")
    @OperationLog(module = "SQL规则", operationType = "CREATE", description = "创建SQL规则")
    public ResponseEntity<SqlExecuteRule> createSqlRule(@RequestBody SqlExecuteRule rule) {
        rule.setId(null);
        sqlExecuteRuleMapper.insert(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/sql-rules/{id}")
    @OperationLog(module = "SQL规则", operationType = "EDIT", description = "修改SQL规则")
    public ResponseEntity<SqlExecuteRule> updateSqlRule(@PathVariable Long id, @RequestBody SqlExecuteRule rule) {
        SqlExecuteRule existing = sqlExecuteRuleMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        rule.setId(id);
        sqlExecuteRuleMapper.updateById(rule);
        return ResponseEntity.ok(rule);
    }

    @DeleteMapping("/sql-rules/{id}")
    @OperationLog(module = "SQL规则", operationType = "DELETE", description = "删除SQL规则")
    public ResponseEntity<Void> deleteSqlRule(@PathVariable Long id) {
        SqlExecuteRule existing = sqlExecuteRuleMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        sqlExecuteRuleMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/environments")
    public ResponseEntity<List<String>> getEnvironments() {
        List<LogCollectTimeInfo> infos = logCollectTimeInfoMapper.selectList(null);
        List<String> envNames = infos.stream()
                .map(LogCollectTimeInfo::getEnvironmentName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(envNames);
    }

    @GetMapping("/sql-rules/check-unique")
    public ResponseEntity<Map<String, Object>> checkRuleUnique(
            @RequestParam String environmentName,
            @RequestParam String sqlFileName,
            @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<SqlExecuteRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SqlExecuteRule::getEnvironmentName, environmentName)
               .eq(SqlExecuteRule::getSqlFileName, sqlFileName);

        if (excludeId != null) {
            wrapper.ne(SqlExecuteRule::getId, excludeId);
        }

        Long count = sqlExecuteRuleMapper.selectCount(wrapper);
        result.put("isUnique", count == 0);
        result.put("exists", count > 0);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/sql-files")
    public ResponseEntity<List<String>> getSqlFiles() {
        File directory = null;
        try {
            if (sqlConfig.getSqlAbsoluteDir() != null && !sqlConfig.getSqlAbsoluteDir().isEmpty()) {
                directory = new File(sqlConfig.getSqlAbsoluteDir());
            } else {
                directory = sqlConfig.getSqlDir().getFile();
            }
        } catch (IOException e) {
            logger.error("获取SQL目录失败", e);
            return ResponseEntity.internalServerError().build();
        }

        if (!directory.exists() || !directory.isDirectory()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> files = Arrays.stream(directory.listFiles())
                .filter(f -> f.isFile() && f.getName().endsWith(".sql"))
                .map(File::getName)
                .sorted()
                .collect(Collectors.toList());

        return ResponseEntity.ok(files);
    }

    @PostMapping("/sql-files/upload")
    @OperationLog(module = "SQL文件", operationType = "CREATE", description = "上传SQL文件")
    public ResponseEntity<Map<String, Object>> uploadSqlFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "请选择要上传的文件");
            return ResponseEntity.badRequest().body(result);
        }

        if (!file.getOriginalFilename().endsWith(".sql")) {
            result.put("success", false);
            result.put("message", "只允许上传SQL文件");
            return ResponseEntity.badRequest().body(result);
        }

        File directory = null;
        try {
            if (sqlConfig.getSqlAbsoluteDir() != null && !sqlConfig.getSqlAbsoluteDir().isEmpty()) {
                directory = new File(sqlConfig.getSqlAbsoluteDir());
            } else {
                directory = sqlConfig.getSqlDir().getFile();
            }
        } catch (IOException e) {
            logger.error("获取SQL目录失败", e);
            result.put("success", false);
            result.put("message", "获取SQL目录失败");
            return ResponseEntity.internalServerError().body(result);
        }

        if (!directory.exists()) {
            directory.mkdirs();
        }

        try {
            Path filePath = Paths.get(directory.getAbsolutePath(), file.getOriginalFilename());
            Files.copy(file.getInputStream(), filePath);
            result.put("success", true);
            result.put("message", "上传成功");
            result.put("filename", file.getOriginalFilename());
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("上传文件失败", e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @DeleteMapping("/sql-files/{filename}")
    @OperationLog(module = "SQL文件", operationType = "DELETE", description = "删除SQL文件")
    public ResponseEntity<Map<String, Object>> deleteSqlFile(@PathVariable String filename) {
        Map<String, Object> result = new HashMap<>();

        if (!filename.endsWith(".sql")) {
            result.put("success", false);
            result.put("message", "只允许删除SQL文件");
            return ResponseEntity.badRequest().body(result);
        }

        File directory = null;
        try {
            if (sqlConfig.getSqlAbsoluteDir() != null && !sqlConfig.getSqlAbsoluteDir().isEmpty()) {
                directory = new File(sqlConfig.getSqlAbsoluteDir());
            } else {
                directory = sqlConfig.getSqlDir().getFile();
            }
        } catch (IOException e) {
            logger.error("获取SQL目录失败", e);
            result.put("success", false);
            result.put("message", "获取SQL目录失败");
            return ResponseEntity.internalServerError().body(result);
        }

        File file = new File(directory, filename);
        if (!file.exists()) {
            result.put("success", false);
            result.put("message", "文件不存在");
            return ResponseEntity.notFound().build();
        }

        if (file.delete()) {
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "删除失败");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/sql-files/{filename}/content")
    public ResponseEntity<Map<String, Object>> getSqlFileContent(@PathVariable String filename) {
        Map<String, Object> result = new HashMap<>();

        if (!filename.endsWith(".sql")) {
            result.put("success", false);
            result.put("message", "只支持SQL文件");
            return ResponseEntity.badRequest().body(result);
        }

        File directory = null;
        try {
            if (sqlConfig.getSqlAbsoluteDir() != null && !sqlConfig.getSqlAbsoluteDir().isEmpty()) {
                directory = new File(sqlConfig.getSqlAbsoluteDir());
            } else {
                directory = sqlConfig.getSqlDir().getFile();
            }
        } catch (IOException e) {
            logger.error("获取SQL目录失败", e);
            result.put("success", false);
            result.put("message", "获取SQL目录失败");
            return ResponseEntity.internalServerError().body(result);
        }

        File file = new File(directory, filename);
        if (!file.exists()) {
            result.put("success", false);
            result.put("message", "文件不存在");
            return ResponseEntity.notFound().build();
        }

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            result.put("success", true);
            result.put("content", content);
            result.put("filename", filename);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("读取文件失败", e);
            result.put("success", false);
            result.put("message", "读取文件失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PutMapping("/sql-files/{filename}/content")
    @OperationLog(module = "SQL文件", operationType = "EDIT", description = "修改SQL文件内容")
    public ResponseEntity<Map<String, Object>> updateSqlFileContent(
            @PathVariable String filename,
            @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        if (!filename.endsWith(".sql")) {
            result.put("success", false);
            result.put("message", "只支持SQL文件");
            return ResponseEntity.badRequest().body(result);
        }

        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "内容不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        File directory = null;
        try {
            if (sqlConfig.getSqlAbsoluteDir() != null && !sqlConfig.getSqlAbsoluteDir().isEmpty()) {
                directory = new File(sqlConfig.getSqlAbsoluteDir());
            } else {
                directory = sqlConfig.getSqlDir().getFile();
            }
        } catch (IOException e) {
            logger.error("获取SQL目录失败", e);
            result.put("success", false);
            result.put("message", "获取SQL目录失败");
            return ResponseEntity.internalServerError().body(result);
        }

        File file = new File(directory, filename);
        if (!file.exists()) {
            result.put("success", false);
            result.put("message", "文件不存在");
            return ResponseEntity.notFound().build();
        }

        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("filename", filename);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            logger.error("写入文件失败", e);
            result.put("success", false);
            result.put("message", "写入文件失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/datasources")
    public ResponseEntity<List<String>> getDataSources() {
        Map<String, String> jdbcTemplates = executeJDBCContext.getJBDCTemplate();
        List<String> dataSources = new ArrayList<>(jdbcTemplates.keySet());
        Collections.sort(dataSources);
        return ResponseEntity.ok(dataSources);
    }

    @PostMapping("/sql-debug/execute")
    @OperationLog(module = "SQL调试", operationType = "EDIT", description = "执行SQL调试")
    public ResponseEntity<Map<String, Object>> executeSqlDebug(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        String environmentName = request.get("environment");
        String sql = request.get("sql");
        String filename = request.get("filename");

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
            logger.error("执行SQL调试失败: environment={}, filename={}, sql={}", environmentName, filename, sql, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "SQL执行失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/stats/dashboard")
    @OperationLog(module = "首页", operationType = "VISIT", description = "访问首页统计")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> result = new HashMap<>();

        List<MsgSendLog> todayLogs = msgSendLogMapper.selectList(
            new LambdaQueryWrapper<MsgSendLog>()
                .like(MsgSendLog::getCreateTime, LocalDate.now().toString())
        );
        result.put("todayPushCount", todayLogs.size());

        long sqlExceptionCount = 0;
        long logExceptionCount = 0;
        for (MsgSendLog log : todayLogs) {
            String content = log.getContent() != null ? log.getContent().toLowerCase() : "";
            if (content.contains("sql")) {
                sqlExceptionCount++;
            }
            if (content.contains("日志")) {
                logExceptionCount++;
            }
        }
        result.put("todaySqlExceptionCount", sqlExceptionCount);
        result.put("todayLogExceptionCount", logExceptionCount);

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
        
        result.put("onlineLogDataSourceCount", onlineGrafanaCount);
        result.put("totalLogDataSourceCount", grafanaDataSources.size());
        result.put("onlineSqlDataSourceCount", onlineSqlCount);
        result.put("totalSqlDataSourceCount", sqlDataSources.size());

        Long activeRuleCount = sqlExecuteRuleMapper.selectCount(null);
        result.put("activeRuleCount", activeRuleCount);

        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        List<MsgSendLog> last24hLogs = msgSendLogMapper.selectList(
            new LambdaQueryWrapper<MsgSendLog>()
                .ge(MsgSendLog::getCreateTime, yesterday)
        );
        result.put("last24hExceptionCount", last24hLogs.size());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats/log-collect")
    public ResponseEntity<List<Map<String, Object>>> getLogCollectStats() {
        List<Map<String, Object>> totalStats = grafanaMonitorRuleService.getEnvironmentCollectStats();
        List<Map<String, Object>> dailyStats = grafanaMonitorRuleService.getEnvironmentDailyCollectStats(java.time.LocalDate.now());

        Map<String, Map<String, Object>> mergedStats = new java.util.LinkedHashMap<>();

        for (Map<String, Object> stat : totalStats) {
            String envName = (String) stat.get("environmentName");
            Map<String, Object> merged = new java.util.HashMap<>();
            merged.put("environmentName", envName);
            merged.put("totalCollectCount", stat.get("totalCollectCount"));
            merged.put("dailyCollectCount", 0L);
            mergedStats.put(envName, merged);
        }

        for (Map<String, Object> stat : dailyStats) {
            String envName = (String) stat.get("environmentName");
            Map<String, Object> merged = mergedStats.get(envName);
            if (merged == null) {
                merged = new java.util.HashMap<>();
                merged.put("environmentName", envName);
                merged.put("totalCollectCount", 0L);
                mergedStats.put(envName, merged);
            }
            merged.put("dailyCollectCount", stat.get("dailyCollectCount"));
        }

        return ResponseEntity.ok(new java.util.ArrayList<>(mergedStats.values()));
    }

    @GetMapping("/stats/log-collect/daily")
    public ResponseEntity<List<Map<String, Object>>> getDailyLogCollectStats() {
        List<Map<String, Object>> stats = grafanaMonitorRuleService.getEnvironmentDailyCollectStats(java.time.LocalDate.now());
        return ResponseEntity.ok(stats);
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
}
