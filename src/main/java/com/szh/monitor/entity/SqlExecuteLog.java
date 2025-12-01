package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sql_execute_log")
public class SqlExecuteLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String environmentName;

    private String sqlFileName;

    private Integer executeDate;

    private Integer count;

    private Integer failedCount;

    private Integer failedCountResetTime;
}
