package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;

    private String operationModule;

    private String operationContent;

    private String targetId;

    private String ipAddress;

    private String browserInfo;

    private String userAgent;

    private LocalDateTime createTime;

    private String environmentName;
}