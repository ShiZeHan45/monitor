package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sql_data_source")
public class SqlDataSource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String environmentName;

    private String jdbcUrl;

    private String username;

    private String password;

    private String driverClassName;

    private Boolean enabled;

    private Integer maximumPoolSize;

    private Integer minimumIdle;

    private Integer maxLifetime;

    private Integer idleTimeout;

    private Integer connectionTimeout;

    private Integer keepaliveTime;

    private String connectionTestQuery;

    private Integer validationTimeout;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}