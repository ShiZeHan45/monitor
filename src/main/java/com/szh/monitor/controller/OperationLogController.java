package com.szh.monitor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;
import com.szh.monitor.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Page<OperationLog> logs = operationLogService.getLogs(page, size, operationType, module, startDate, endDate);
        Map<String, Object> result = new HashMap<>();
        result.put("records", logs.getRecords());
        result.put("total", logs.getTotal());
        result.put("current", logs.getCurrent());
        result.put("size", logs.getSize());
        result.put("pages", logs.getPages());
        return ResponseEntity.ok(result);
    }
}
