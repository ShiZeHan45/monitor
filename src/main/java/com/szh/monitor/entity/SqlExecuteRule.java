package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sql_execute_rule")
public class SqlExecuteRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String environmentName;

    private String sqlFileName;

    private Integer executeLimit;

    private String executeStartTime;

    private String executeEndTime;

    private Integer executeFrequency;
}
