package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("remote_log_source")
public class RemoteLogSource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String environmentName;

    private String url;

    private String datasourceId;

    private String username;

    private String password;

    private String webhook;

    private String week;

    private LocalTime startTime;

    private LocalTime endTime;

    private String monitors;

    private Boolean enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}