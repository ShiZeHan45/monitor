package com.szh.monitor.controller;

import com.szh.monitor.entity.LogCollectTimeInfo;
import com.szh.monitor.service.LogCollectTimeInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/log-collect")
public class LogCollectTimeController {

    private static final Logger logger = LoggerFactory.getLogger(LogCollectTimeController.class);

    private final LogCollectTimeInfoService logCollectTimeInfoService;

    public LogCollectTimeController(LogCollectTimeInfoService logCollectTimeInfoService) {
        this.logCollectTimeInfoService = logCollectTimeInfoService;
    }

    @GetMapping("/times")
    public ResponseEntity<List<LogCollectTimeInfo>> listTimes() {
        return ResponseEntity.ok(logCollectTimeInfoService.getLastTsList());
    }

    @PutMapping("/times/{id}")
    public ResponseEntity<Map<String, Object>> updateTime(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String dateTimeStr = request.get("lastTime");
            if (dateTimeStr == null || dateTimeStr.isEmpty()) {
                result.put("success", false);
                result.put("message", "lastTime 不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logCollectTimeInfoService.updateLastTs(id, dateTime);
            result.put("success", true);
            result.put("message", "更新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新采集起始时间失败", e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
