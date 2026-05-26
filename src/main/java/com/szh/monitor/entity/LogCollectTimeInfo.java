package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("log_collect_time_info")
public class LogCollectTimeInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String environmentName;

    private String ruleName;

    private Long lastTs;

    private LocalDateTime lastTime;

    private LocalDateTime createTime;

}
