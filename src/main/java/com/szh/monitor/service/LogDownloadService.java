package com.szh.monitor.service;

import com.szh.monitor.form.LogDownloadRequest;
import com.szh.monitor.vo.LogDownloadTask;

import java.util.List;

/**
 * 日志下载服务：按服务+时间范围从 Loki 全量导出原始日志。
 */
public interface LogDownloadService {

    /** 查询环境下的服务列表（来自该环境监控规则名） */
    List<String> listServices(String environmentName);

    /** 提交异步下载任务，立即返回 taskId */
    String submitTask(LogDownloadRequest request);

    /** 查询任务；不存在返回 null */
    LogDownloadTask getTask(String taskId);

    /** 全部任务列表（按提交时间倒序） */
    List<LogDownloadTask> listTasks();

    /** 手动删除任务及其文件；任务不存在抛 IllegalArgumentException */
    void deleteTask(String taskId);

    /** 预览：拉取第一批日志，应用关键词过滤后返回前 100 条格式化行 */
    List<String> preview(LogDownloadRequest request);
}
