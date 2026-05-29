package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("grafana_monitor_rule")
public class GrafanaMonitorRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("data_source_id")
    private Long dataSourceId;

    @TableField("name")
    private String name;

    @TableField("query_expr")
    private String queryExpr;

    @TableField("keywords")
    private String keywords;

    @TableField("exclusion_keywords")
    private String exclusionKeywords;

    @TableField("context_lines")
    private Integer contextLines;

    @TableField("webhook")
    private String webhook;

    @TableField("enabled")
    private Integer enabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}