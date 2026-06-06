package com.szh.monitor.controller;

import com.szh.monitor.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemConfigController {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigController.class);

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        return ResponseEntity.ok(systemConfigService.getAllConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            for (Map.Entry<String, String> entry : config.entrySet()) {
                systemConfigService.setConfigValue(entry.getKey(), entry.getValue());
            }
            systemConfigService.refreshCache();
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
