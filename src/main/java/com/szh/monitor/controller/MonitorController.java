package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.config.SQLConfig;
import com.szh.monitor.entity.LogCollectTimeInfo;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.entity.SqlExecuteLog;
import com.szh.monitor.entity.SqlExecuteRule;
import com.szh.monitor.mapper.LogCollectTimeInfoMapper;
import com.szh.monitor.mapper.MsgSendLogMapper;
import com.szh.monitor.mapper.SqlExecuteLogMapper;
import com.szh.monitor.mapper.SqlExecuteRuleMapper;
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
    private LogCollectTimeInfoMapper logCollectTimeInfoMapper;

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
}