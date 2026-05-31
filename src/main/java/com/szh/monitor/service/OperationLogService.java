package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szh.monitor.entity.OperationLog;
import javax.servlet.http.HttpServletRequest;

public interface OperationLogService {

    void logVisit(HttpServletRequest request);

    void logCreate(String module, Integer targetId, String detail, HttpServletRequest request);

    void logEdit(String module, Integer targetId, String detail, HttpServletRequest request);

    void logDelete(String module, Integer targetId, String detail, HttpServletRequest request);

    void log(String operationType, String module, Integer targetId, String detail, HttpServletRequest request);

    Page<OperationLog> getLogs(int page, int size);
}
