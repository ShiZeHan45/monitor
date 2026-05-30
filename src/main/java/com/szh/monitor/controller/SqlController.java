package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import com.szh.monitor.service.impl.SqlConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private static final Logger logger = LoggerFactory.getLogger(SqlController.class);

    private final SqlDataSourceService dataSourceService;
    private final SqlConfigService sqlConfigService;

    public SqlController(SqlDataSourceService dataSourceService, SqlConfigService sqlConfigService) {
        this.dataSourceService = dataSourceService;
        this.sqlConfigService = sqlConfigService;
    }

    @GetMapping("/datasources")
    public ResponseEntity<List<SqlDataSource>> listDataSources() {
        return ResponseEntity.ok(dataSourceService.list());
    }

    @GetMapping("/datasources/{id}")
    public ResponseEntity<SqlDataSource> getDataSource(@PathVariable Long id) {
        SqlDataSource dataSource = dataSourceService.getById(id);
        if (dataSource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataSource);
    }

    @PostMapping("/datasources")
    public ResponseEntity<Map<String, Object>> createDataSource(@RequestBody SqlDataSource dataSource) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dataSourceService.save(dataSource);
            if (success) {
                result.put("success", true);
                result.put("message", "创建成功");
                result.put("id", dataSource.getId());
                sqlConfigService.refreshConfig();
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "创建失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("创建数据源失败", e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PutMapping("/datasources/{id}")
    public ResponseEntity<Map<String, Object>> updateDataSource(@PathVariable Long id, @RequestBody SqlDataSource dataSource) {
        Map<String, Object> result = new HashMap<>();
        try {
            dataSource.setId(id);
            boolean success = dataSourceService.updateById(dataSource);
            if (success) {
                result.put("success", true);
                result.put("message", "更新成功");
                sqlConfigService.refreshConfig();
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "更新失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("更新数据源失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @DeleteMapping("/datasources/{id}")
    public ResponseEntity<Map<String, Object>> deleteDataSource(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dataSourceService.removeById(id);
            if (success) {
                result.put("success", true);
                result.put("message", "删除成功");
                sqlConfigService.refreshConfig();
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "删除失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("删除数据源失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            sqlConfigService.refreshConfig();
            result.put("success", true);
            result.put("message", "配置刷新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("刷新配置失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
