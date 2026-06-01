package com.szh.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szh.monitor.annotation.OperationLog;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.service.GrafanaDataSourceService;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import com.szh.monitor.service.impl.GrafanaLogServiceImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grafana")
public class GrafanaController {

    private static final Logger logger = LoggerFactory.getLogger(GrafanaController.class);

    private final GrafanaDataSourceService dataSourceService;
    private final GrafanaMonitorRuleService ruleService;
    private final GrafanaLogServiceImp grafanaLogService;

    public GrafanaController(GrafanaDataSourceService dataSourceService, GrafanaMonitorRuleService ruleService, GrafanaLogServiceImp grafanaLogService) {
        this.dataSourceService = dataSourceService;
        this.ruleService = ruleService;
        this.grafanaLogService = grafanaLogService;
    }

    @GetMapping("/datasources")
    public ResponseEntity<List<GrafanaDataSource>> listDataSources() {
        return ResponseEntity.ok(dataSourceService.list());
    }

    @GetMapping("/datasources/{id}")
    public ResponseEntity<GrafanaDataSource> getDataSource(@PathVariable Long id) {
        GrafanaDataSource dataSource = dataSourceService.getById(id);
        if (dataSource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataSource);
    }

    @PostMapping("/datasources")
    @OperationLog(module = "Grafana数据源", operationType = "CREATE", description = "创建Grafana数据源")
    public ResponseEntity<Map<String, Object>> createDataSource(@RequestBody GrafanaDataSource dataSource) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dataSourceService.save(dataSource);
            if (success) {
                result.put("success", true);
                result.put("message", "创建成功");
                result.put("id", dataSource.getId());
                grafanaLogService.refreshConfig();
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
    @OperationLog(module = "Grafana数据源", operationType = "EDIT", description = "修改Grafana数据源")
    public ResponseEntity<Map<String, Object>> updateDataSource(@PathVariable Long id, @RequestBody GrafanaDataSource dataSource) {
        Map<String, Object> result = new HashMap<>();
        try {
            dataSource.setId(id);
            boolean success = dataSourceService.updateById(dataSource);
            if (success) {
                result.put("success", true);
                result.put("message", "更新成功");
                grafanaLogService.refreshConfig();
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
    @OperationLog(module = "Grafana数据源", operationType = "DELETE", description = "删除Grafana数据源")
    public ResponseEntity<Map<String, Object>> deleteDataSource(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ruleService.removeByDataSourceId(id);
            boolean success = dataSourceService.removeById(id);
            if (success) {
                result.put("success", true);
                result.put("message", "删除成功");
                grafanaLogService.refreshConfig();
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

    @GetMapping("/datasources/{id}/rules")
    public ResponseEntity<List<GrafanaMonitorRule>> listRulesByDataSource(@PathVariable Long id) {
        return ResponseEntity.ok(ruleService.listByDataSourceId(id));
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<GrafanaMonitorRule> getRule(@PathVariable Long id) {
        GrafanaMonitorRule rule = ruleService.getById(id);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rule);
    }

    @PostMapping("/rules")
    @OperationLog(module = "Grafana规则", operationType = "CREATE", description = "创建监控规则")
    public ResponseEntity<Map<String, Object>> createRule(@RequestBody GrafanaMonitorRule rule) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = ruleService.save(rule);
            if (success) {
                result.put("success", true);
                result.put("message", "创建成功");
                result.put("id", rule.getId());
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "创建失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("创建监控规则失败", e);
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PutMapping("/rules/{id}")
    @OperationLog(module = "Grafana规则", operationType = "EDIT", description = "修改监控规则")
    public ResponseEntity<Map<String, Object>> updateRule(@PathVariable Long id, @RequestBody GrafanaMonitorRule rule) {
        Map<String, Object> result = new HashMap<>();
        try {
            rule.setId(id);
            boolean success = ruleService.updateById(rule);
            if (success) {
                result.put("success", true);
                result.put("message", "更新成功");
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "更新失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("更新监控规则失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @DeleteMapping("/rules/{id}")
    @OperationLog(module = "Grafana规则", operationType = "DELETE", description = "删除监控规则")
    public ResponseEntity<Map<String, Object>> deleteRule(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = ruleService.removeById(id);
            if (success) {
                result.put("success", true);
                result.put("message", "删除成功");
                grafanaLogService.refreshConfig();
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "删除失败");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            logger.error("删除监控规则失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/refresh")
    @OperationLog(module = "Grafana配置", operationType = "EDIT", description = "刷新Grafana配置")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            grafanaLogService.refreshConfig();
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
