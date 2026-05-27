package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.entity.*;
import com.szh.monitor.mapper.*;
import com.szh.monitor.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private SQLConfig sqlConfig;

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

        return ResponseEntity.ok(new ArrayList<>(envMap.values()));
    }

    @GetMapping("/push-records")
    public ResponseEntity<IPage<MsgSendLog>> getPushRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String date) {

        Page<MsgSendLog> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<MsgSendLog> query = new LambdaQueryWrapper<>();

        if (date != null && !date.isEmpty()) {
            query.like(MsgSendLog::getCreateTime, date);
        }

        query.orderByDesc(MsgSendLog::getCreateTime);
        IPage<MsgSendLog> result = msgSendLogMapper.selectPage(pageRequest, query);

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
    public ResponseEntity<SqlExecuteRule> createSqlRule(@RequestBody SqlExecuteRule rule) {
        rule.setId(null);
        sqlExecuteRuleMapper.insert(rule);
        return ResponseEntity.ok(rule);
    }

    @PutMapping("/sql-rules/{id}")
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
    public ResponseEntity<Void> deleteSqlRule(@PathVariable Long id) {
        SqlExecuteRule existing = sqlExecuteRuleMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        sqlExecuteRuleMapper.deleteById(id);
        return ResponseEntity.ok().build();
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

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private LogCollectTimeInfoMapper logCollectTimeInfoMapper;

    @Autowired
    private SqlDataSourceMapper sqlDataSourceMapper;

    @Autowired
    private RemoteLogSourceMapper remoteLogSourceMapper;

    @GetMapping("/operation-logs")
    public ResponseEntity<IPage<OperationLog>> getOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationModule,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String environmentName) {
        
        Page<OperationLog> pageRequest = new Page<>(page, size);
        IPage<OperationLog> result = operationLogService.getLogs(pageRequest, operationModule, operationType, environmentName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/operation-logs/track")
    public ResponseEntity<Void> trackVisit(
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String pageName) {
        
        operationLogService.saveLog("访问", "页面访问", "访问页面: " + pageName, null, ipAddress, userAgent, null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/environments")
    public ResponseEntity<List<String>> getEnvironments() {
        List<LogCollectTimeInfo> infos = logCollectTimeInfoMapper.selectList(null);
        List<String> environments = infos.stream()
                .map(LogCollectTimeInfo::getEnvironmentName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(environments);
    }

    @GetMapping("/sql-data-sources")
    public ResponseEntity<IPage<SqlDataSource>> getSqlDataSources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String environmentName) {
        
        Page<SqlDataSource> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<SqlDataSource> query = new LambdaQueryWrapper<>();
        if (environmentName != null && !environmentName.isEmpty()) {
            query.eq(SqlDataSource::getEnvironmentName, environmentName);
        }
        query.orderByDesc(SqlDataSource::getCreateTime);
        IPage<SqlDataSource> result = sqlDataSourceMapper.selectPage(pageRequest, query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sql-data-sources/{id}")
    public ResponseEntity<SqlDataSource> getSqlDataSource(@PathVariable Long id) {
        SqlDataSource dataSource = sqlDataSourceMapper.selectById(id);
        if (dataSource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataSource);
    }

    @PostMapping("/sql-data-sources")
    public ResponseEntity<Map<String, Object>> createSqlDataSource(@RequestBody SqlDataSource dataSource,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        LambdaQueryWrapper<SqlDataSource> query = new LambdaQueryWrapper<>();
        query.eq(SqlDataSource::getEnvironmentName, dataSource.getEnvironmentName());
        if (sqlDataSourceMapper.selectCount(query) > 0) {
            result.put("success", false);
            result.put("message", "该环境已存在数据源配置");
            return ResponseEntity.badRequest().body(result);
        }
        
        dataSource.setId(null);
        dataSource.setCreateTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        sqlDataSourceMapper.insert(dataSource);
        
        operationLogService.saveLog("新增", "SQL数据源", "新增SQL数据源: " + dataSource.getEnvironmentName(), 
                dataSource.getId().toString(), ipAddress, userAgent, dataSource.getEnvironmentName());
        
        result.put("success", true);
        result.put("message", "新增成功");
        result.put("data", dataSource);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/sql-data-sources/{id}")
    public ResponseEntity<Map<String, Object>> updateSqlDataSource(@PathVariable Long id, 
            @RequestBody SqlDataSource dataSource,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        SqlDataSource existing = sqlDataSourceMapper.selectById(id);
        if (existing == null) {
            result.put("success", false);
            result.put("message", "数据源不存在");
            return ResponseEntity.notFound().build();
        }
        
        String oldEnv = existing.getEnvironmentName();
        dataSource.setId(id);
        dataSource.setCreateTime(existing.getCreateTime());
        dataSource.setUpdateTime(LocalDateTime.now());
        sqlDataSourceMapper.updateById(dataSource);
        
        operationLogService.saveLog("编辑", "SQL数据源", "修改SQL数据源: " + oldEnv + " -> " + dataSource.getEnvironmentName(), 
                id.toString(), ipAddress, userAgent, dataSource.getEnvironmentName());
        
        result.put("success", true);
        result.put("message", "更新成功");
        result.put("data", dataSource);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/sql-data-sources/{id}")
    public ResponseEntity<Map<String, Object>> deleteSqlDataSource(@PathVariable Long id,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        SqlDataSource existing = sqlDataSourceMapper.selectById(id);
        if (existing == null) {
            result.put("success", false);
            result.put("message", "数据源不存在");
            return ResponseEntity.notFound().build();
        }
        
        String envName = existing.getEnvironmentName();
        sqlDataSourceMapper.deleteById(id);
        
        operationLogService.saveLog("删除", "SQL数据源", "删除SQL数据源: " + envName, 
                id.toString(), ipAddress, userAgent, envName);
        
        result.put("success", true);
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/remote-log-sources")
    public ResponseEntity<IPage<RemoteLogSource>> getRemoteLogSources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String environmentName) {
        
        Page<RemoteLogSource> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<RemoteLogSource> query = new LambdaQueryWrapper<>();
        if (environmentName != null && !environmentName.isEmpty()) {
            query.eq(RemoteLogSource::getEnvironmentName, environmentName);
        }
        query.orderByDesc(RemoteLogSource::getCreateTime);
        IPage<RemoteLogSource> result = remoteLogSourceMapper.selectPage(pageRequest, query);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/remote-log-sources/{id}")
    public ResponseEntity<RemoteLogSource> getRemoteLogSource(@PathVariable Long id) {
        RemoteLogSource source = remoteLogSourceMapper.selectById(id);
        if (source == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(source);
    }

    @PostMapping("/remote-log-sources")
    public ResponseEntity<Map<String, Object>> createRemoteLogSource(@RequestBody RemoteLogSource source,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        LambdaQueryWrapper<RemoteLogSource> query = new LambdaQueryWrapper<>();
        query.eq(RemoteLogSource::getEnvironmentName, source.getEnvironmentName());
        if (remoteLogSourceMapper.selectCount(query) > 0) {
            result.put("success", false);
            result.put("message", "该环境已存在日志采集源配置");
            return ResponseEntity.badRequest().body(result);
        }
        
        source.setId(null);
        source.setCreateTime(LocalDateTime.now());
        source.setUpdateTime(LocalDateTime.now());
        remoteLogSourceMapper.insert(source);
        
        operationLogService.saveLog("新增", "远程日志采集源", "新增远程日志采集源: " + source.getEnvironmentName(), 
                source.getId().toString(), ipAddress, userAgent, source.getEnvironmentName());
        
        result.put("success", true);
        result.put("message", "新增成功");
        result.put("data", source);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/remote-log-sources/{id}")
    public ResponseEntity<Map<String, Object>> updateRemoteLogSource(@PathVariable Long id, 
            @RequestBody RemoteLogSource source,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        RemoteLogSource existing = remoteLogSourceMapper.selectById(id);
        if (existing == null) {
            result.put("success", false);
            result.put("message", "采集源不存在");
            return ResponseEntity.notFound().build();
        }
        
        String oldEnv = existing.getEnvironmentName();
        source.setId(id);
        source.setCreateTime(existing.getCreateTime());
        source.setUpdateTime(LocalDateTime.now());
        remoteLogSourceMapper.updateById(source);
        
        operationLogService.saveLog("编辑", "远程日志采集源", "修改远程日志采集源: " + oldEnv + " -> " + source.getEnvironmentName(), 
                id.toString(), ipAddress, userAgent, source.getEnvironmentName());
        
        result.put("success", true);
        result.put("message", "更新成功");
        result.put("data", source);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/remote-log-sources/{id}")
    public ResponseEntity<Map<String, Object>> deleteRemoteLogSource(@PathVariable Long id,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(required = false) String ipAddress) {
        
        Map<String, Object> result = new HashMap<>();
        
        RemoteLogSource existing = remoteLogSourceMapper.selectById(id);
        if (existing == null) {
            result.put("success", false);
            result.put("message", "采集源不存在");
            return ResponseEntity.notFound().build();
        }
        
        String envName = existing.getEnvironmentName();
        remoteLogSourceMapper.deleteById(id);
        
        operationLogService.saveLog("删除", "远程日志采集源", "删除远程日志采集源: " + envName, 
                id.toString(), ipAddress, userAgent, envName);
        
        result.put("success", true);
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sql-rules/check-unique")
    public ResponseEntity<Map<String, Object>> checkRuleUnique(
            @RequestParam String environmentName,
            @RequestParam String sqlFileName,
            @RequestParam(required = false) Long excludeId) {
        
        Map<String, Object> result = new HashMap<>();
        
        LambdaQueryWrapper<SqlExecuteRule> query = new LambdaQueryWrapper<>();
        query.eq(SqlExecuteRule::getEnvironmentName, environmentName)
             .eq(SqlExecuteRule::getSqlFileName, sqlFileName);
        
        if (excludeId != null) {
            query.ne(SqlExecuteRule::getId, excludeId);
        }
        
        long count = sqlExecuteRuleMapper.selectCount(query);
        result.put("unique", count == 0);
        result.put("message", count == 0 ? "可以使用" : "环境名称+SQL文件名已存在");
        
        return ResponseEntity.ok(result);
    }

    @Override
    public String toString() {
        return "MonitorController{}";
    }
}