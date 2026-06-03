package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;

import java.util.List;
import javax.servlet.http.HttpServletRequest;

public interface OperationLogService {

    void logVisit(HttpServletRequest request);

    void logCreate(String module, Long targetId, String detail, HttpServletRequest request);

    void logEdit(String module, Long targetId, String detail, HttpServletRequest request);

    void logDelete(String module, Long targetId, String detail, HttpServletRequest request);

    void log(String operationType, String module, Long targetId, String detail, HttpServletRequest request);

    Page<OperationLog> getLogs(int page, int size, String operationType, String module, String startDate, String endDate);

    long countLogs(String operationType, String module, String startDate, String endDate);

    List<String> getModules();
}
