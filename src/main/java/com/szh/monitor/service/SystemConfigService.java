package com.szh.monitor.service;

import java.util.Map;

public interface SystemConfigService {

    String getConfigValue(String key);

    boolean setConfigValue(String key, String value);

    Map<String, String> getAllConfig();

    void refreshCache();

    int getQuietStartHour();

    int getQuietEndHour();

    String getWechatWebhook();

    String getLogWechatWebhook();
}
