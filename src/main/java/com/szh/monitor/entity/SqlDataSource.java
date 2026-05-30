package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sql_data_source")
public class SqlDataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("environment_name")
    private String environmentName;

    @TableField("jdbc_url")
    private String jdbcUrl;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("driver_class_name")
    private String driverClassName;

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

    @TableField("last_check_time")
    private LocalDateTime lastCheckTime;

    @TableField("is_online")
    private Integer isOnline;
}
