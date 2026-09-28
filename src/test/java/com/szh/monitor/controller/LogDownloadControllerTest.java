package com.szh.monitor.controller;

import com.szh.monitor.form.LogDownloadRequest;
import com.szh.monitor.service.LogDownloadService;
import com.szh.monitor.vo.LogDownloadTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LogDownloadController 单元测试（TDD：先写测试，跑红后再实现）
 */
class LogDownloadControllerTest {

    private LogDownloadService logDownloadService;
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        logDownloadService = mock(LogDownloadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LogDownloadController(logDownloadService)).build();
    }

    private LogDownloadTask sampleTask(String status) {
        LogDownloadTask task = new LogDownloadTask();
        task.setTaskId("task-1");
        task.setEnvironmentName("test-env");
        task.setService("order-service");
        task.setStartTimeMs(1790000000000L);
        task.setEndTimeMs(1790003600000L);
        task.setStatus(status);
        task.setProcessedLines(1100L);
        task.setFileName("order-service_20260928_110000.log");
        task.setCreateTime(1790000000000L);
        return task;
    }

    // ---------- GET /api/logs/download/services ----------

    @Test
    void listServicesShouldReturnServiceList() throws Exception {
        when(logDownloadService.listServices("test-env"))
                .thenReturn(Arrays.asList("order-service", "pay-service"));

        mockMvc.perform(get("/api/logs/download/services").param("env", "test-env"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0]").value("order-service"))
                .andExpect(jsonPath("$[1]").value("pay-service"));
    }

    @Test
    void listServicesShouldRejectBlankEnv() throws Exception {
        mockMvc.perform(get("/api/logs/download/services"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("env 不能为空"));
    }

    // ---------- POST /api/logs/download ----------

    @Test
    void submitTaskShouldReturnTaskId() throws Exception {
        when(logDownloadService.submitTask(any(LogDownloadRequest.class))).thenReturn("task-1");

        mockMvc.perform(post("/api/logs/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"env\":\"test-env\",\"service\":\"order-service\","
                                + "\"startTime\":\"2026-09-28 10:00:00\",\"endTime\":\"2026-09-28 11:00:00\","
                                + "\"includeKeywords\":\"ERROR\",\"excludeKeywords\":\"ignore\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.taskId").value("task-1"));

        verify(logDownloadService).submitTask(argThat(req ->
                "test-env".equals(req.getEnv())
                        && "order-service".equals(req.getService())
                        && "2026-09-28 10:00:00".equals(req.getStartTime())
                        && "2026-09-28 11:00:00".equals(req.getEndTime())
                        && "ERROR".equals(req.getIncludeKeywords())
                        && "ignore".equals(req.getExcludeKeywords())));
    }

    @Test
    void submitTaskShouldReturn400WhenServiceRejects() throws Exception {
        when(logDownloadService.submitTask(any(LogDownloadRequest.class)))
                .thenThrow(new IllegalArgumentException("服务不存在"));

        mockMvc.perform(post("/api/logs/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"env\":\"test-env\",\"service\":\"nope\","
                                + "\"startTime\":\"2026-09-28 10:00:00\",\"endTime\":\"2026-09-28 11:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("服务不存在"));
    }

    // ---------- GET /api/logs/download/progress ----------

    @Test
    void progressShouldReturnTaskState() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_SUCCESS);
        task.setFilePath("D:/some/file.log");
        task.setFinishTime(1790003700000L);
        when(logDownloadService.getTask("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/logs/download/progress").param("taskId", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.environmentName").value("test-env"))
                .andExpect(jsonPath("$.service").value("order-service"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.processedLines").value(1100))
                .andExpect(jsonPath("$.fileName").value("order-service_20260928_110000.log"))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void progressShouldReturnErrorForFailedTask() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_FAILED);
        task.setError("Loki 查询失败: 500");
        task.setFinishTime(1790003700000L);
        when(logDownloadService.getTask("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/logs/download/progress").param("taskId", "task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Loki 查询失败: 500"));
    }

    @Test
    void progressShouldReturn404WhenTaskUnknown() throws Exception {
        when(logDownloadService.getTask("nope")).thenReturn(null);

        mockMvc.perform(get("/api/logs/download/progress").param("taskId", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    // ---------- GET /api/logs/download/tasks ----------

    @Test
    void tasksShouldReturnTaskList() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_SUCCESS);
        task.setFilePath("D:/some/file.log");
        when(logDownloadService.listTasks()).thenReturn(Arrays.asList(task));

        mockMvc.perform(get("/api/logs/download/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].environmentName").value("test-env"))
                .andExpect(jsonPath("$[0].service").value("order-service"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].processedLines").value(1100))
                .andExpect(jsonPath("$[0].fileName").value("order-service_20260928_110000.log"));
    }

    // ---------- DELETE /api/logs/download/tasks/{taskId} ----------

    @Test
    void deleteTaskShouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/logs/download/tasks/task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(logDownloadService).deleteTask("task-1");
    }

    @Test
    void deleteTaskShouldReturn404WhenUnknown() throws Exception {
        doThrow(new IllegalArgumentException("任务不存在"))
                .when(logDownloadService).deleteTask("nope");

        mockMvc.perform(delete("/api/logs/download/tasks/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    // ---------- GET /api/logs/download/file ----------

    @Test
    void fileShouldStreamLogFile() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_SUCCESS);
        File file = tempDir.resolve("order-service_20260928_110000.log").toFile();
        Files.write(file.toPath(), "line1\nline2\n".getBytes(StandardCharsets.UTF_8));
        task.setFilePath(file.getAbsolutePath());
        when(logDownloadService.getTask("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/logs/download/file").param("taskId", "task-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition",
                        containsString("order-service_20260928_110000.log")))
                .andExpect(content().string("line1\nline2\n"));
    }

    @Test
    void fileShouldRejectWhenTaskNotFinished() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_RUNNING);
        when(logDownloadService.getTask("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/logs/download/file").param("taskId", "task-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("任务未完成，无法下载"));
    }

    @Test
    void fileShouldRejectWhenFileExpiredOrMissing() throws Exception {
        LogDownloadTask task = sampleTask(LogDownloadTask.STATUS_SUCCESS);
        task.setFilePath(tempDir.resolve("deleted.log").toString());
        when(logDownloadService.getTask("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/logs/download/file").param("taskId", "task-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("日志文件不存在，请重新提交下载任务"));
    }

    // ---------- POST /api/logs/download/preview ----------

    @Test
    void previewShouldReturnLines() throws Exception {
        when(logDownloadService.preview(any(LogDownloadRequest.class)))
                .thenReturn(Arrays.asList(
                        "2026-09-28 10:00:00.123 [order-service] line-a",
                        "2026-09-28 10:00:01.456 [order-service] line-b"));

        mockMvc.perform(post("/api/logs/download/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"env\":\"test-env\",\"service\":\"order-service\","
                                + "\"startTime\":\"2026-09-28 10:00:00\",\"endTime\":\"2026-09-28 11:00:00\","
                                + "\"includeKeywords\":\"ERROR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.lines[0]").value("2026-09-28 10:00:00.123 [order-service] line-a"))
                .andExpect(jsonPath("$.lines[1]").value("2026-09-28 10:00:01.456 [order-service] line-b"));

        verify(logDownloadService).preview(argThat(req ->
                "test-env".equals(req.getEnv())
                        && "order-service".equals(req.getService())
                        && "ERROR".equals(req.getIncludeKeywords())));
    }

    @Test
    void previewShouldReturn400WhenInvalid() throws Exception {
        when(logDownloadService.preview(any(LogDownloadRequest.class)))
                .thenThrow(new IllegalArgumentException("开始时间必须早于结束时间"));

        mockMvc.perform(post("/api/logs/download/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"env\":\"test-env\",\"service\":\"order-service\","
                                + "\"startTime\":\"2026-09-28 12:00:00\",\"endTime\":\"2026-09-28 11:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("开始时间必须早于结束时间"));
    }
}
