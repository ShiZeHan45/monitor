package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("grafana_data_source")
public class GrafanaDataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("url")
    private String url;

    @TableField("environment_name")
    private String environmentName;

    @TableField("datasource_id")
    private String datasourceId;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("webhook")
    private String webhook;

    @TableField("week")
    private String week;

    @TableField("start_time")
    private String startTime;

    @TableField("end_time")
    private String endTime;

    @TableField("enabled")
    private Integer enabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}