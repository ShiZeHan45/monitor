package com.szh.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "watcher.notify-webhook")
public class BaseConfig {
    private String wechatWebhook;
    private String logWechatWebhook;
}
