package com.szh.monitor.controller;

import com.szh.monitor.annotation.OperationLog;
import com.szh.monitor.form.LogDownloadRequest;
import com.szh.monitor.service.LogDownloadService;
import com.szh.monitor.vo.LogDownloadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志下载 Controller：服务列表查询、异步任务提交、进度轮询、文件下载、预览、任务列表与删除
 */
@RestController
@RequestMapping("/api/logs/download")
public class LogDownloadController {

    private static final Logger logger = LoggerFactory.getLogger(LogDownloadController.class);

    private final LogDownloadService logDownloadService;

    public LogDownloadController(LogDownloadService logDownloadService) {
        this.logDownloadService = logDownloadService;
    }

    /**
     * 查询指定环境下可下载的服务列表（来自该环境监控规则名）
     */
    @GetMapping("/services")
    public ResponseEntity<?> listServices(@RequestParam(required = false) String env) {
        if (env == null || env.trim().isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "env 不能为空");
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(logDownloadService.listServices(env.trim()));
    }

    /**
     * 提交日志下载任务（异步，返回 taskId 供轮询）
     */
    @PostMapping
    @OperationLog(module = "日志下载", operationType = "CREATE", description = "提交日志下载任务")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody LogDownloadRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskId = logDownloadService.submitTask(request);
            result.put("success", true);
            result.put("message", "任务已提交");
            result.put("taskId", taskId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            logger.error("提交日志下载任务失败", e);
            result.put("success", false);
            result.put("message", "提交失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 查询任务进度（前端 1-2s 轮询）
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getProgress(@RequestParam String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            LogDownloadTask task = logDownloadService.getTask(taskId);
            if (task == null) {
                result.put("success", false);
                result.put("message", "任务不存在");
                return ResponseEntity.status(404).body(result);
            }
            result.put("success", true);
            result.put("taskId", task.getTaskId());
            result.put("environmentName", task.getEnvironmentName());
            result.put("service", task.getService());
            result.put("status", task.getStatus());
            result.put("processedLines", task.getProcessedLines());
            result.put("fileName", task.getFileName());
            result.put("truncated", task.isTruncated());
            if (task.getCreateTime() > 0) {
                result.put("createTime", task.getCreateTime());
            }
            if (task.getFinishTime() > 0) {
                result.put("finishTime", task.getFinishTime());
            }
            if (task.getError() != null) {
                result.put("error", task.getError());
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("查询日志下载任务进度失败", e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 查询全部下载任务列表（按提交时间倒序）
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<LogDownloadTask>> listTasks() {
        return ResponseEntity.ok(logDownloadService.listTasks());
    }

    /**
     * 删除指定任务及其已生成的日志文件（手工清理）
     */
    @DeleteMapping("/tasks/{taskId}")
    @OperationLog(module = "日志下载", operationType = "DELETE", description = "删除日志下载任务及文件")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            logDownloadService.deleteTask(taskId);
            result.put("success", true);
            result.put("message", "任务已删除");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(404).body(result);
        } catch (Exception e) {
            logger.error("删除日志下载任务失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 下载已完成的日志文件（流式响应）
     */
    @GetMapping("/file")
    public ResponseEntity<?> downloadFile(@RequestParam String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            LogDownloadTask task = logDownloadService.getTask(taskId);
            if (task == null) {
                result.put("success", false);
                result.put("message", "任务不存在");
                return ResponseEntity.status(404).body(result);
            }
            if (!LogDownloadTask.STATUS_SUCCESS.equals(task.getStatus()) || task.getFilePath() == null) {
                result.put("success", false);
                result.put("message", "任务未完成，无法下载");
                return ResponseEntity.badRequest().body(result);
            }
            File file = new File(task.getFilePath());
            if (!file.exists() || !file.isFile()) {
                result.put("success", false);
                result.put("message", "日志文件不存在，请重新提交下载任务");
                return ResponseEntity.badRequest().body(result);
            }
            String fileName = task.getFileName() != null ? task.getFileName() : file.getName();
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentLength(file.length())
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            logger.error("下载日志文件失败", e);
            result.put("success", false);
            result.put("message", "下载失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 预览日志（按过滤条件拉取前 100 条格式化行，不入库不落盘）
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody LogDownloadRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> lines = logDownloadService.preview(request);
            result.put("success", true);
            result.put("count", lines.size());
            result.put("lines", lines);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            logger.error("预览日志失败", e);
            result.put("success", false);
            result.put("message", "预览失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
