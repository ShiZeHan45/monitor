package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_config")
public class SystemConfig {

    private Long id;

    private String configKey;

    private String configValue;

    private LocalDateTime updatedAt;
}
