package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.SystemConfig;
import com.szh.monitor.mapper.SystemConfigMapper;
import com.szh.monitor.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemConfigServiceImp extends ServiceImpl<SystemConfigMapper, SystemConfig> implements SystemConfigService {
    Logger logger = LoggerFactory.getLogger(SystemConfigServiceImp.class);

    private final Map<String, String> configCache = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            refreshCache();
        } catch (Exception e) {
            logger.warn("初始化系统配置失败，可能是数据库表尚未创建: {}", e.getMessage());
        }
    }

    @Override
    public void refreshCache() {
        synchronized (configCache) {
            configCache.clear();
            try {
                List<SystemConfig> configs = list();
                for (SystemConfig config : configs) {
                    configCache.put(config.getConfigKey(), config.getConfigValue());
                }
                logger.info("系统配置已加载: {}", configCache.keySet());
            } catch (Exception e) {
                logger.warn("加载系统配置失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getConfigValue(String key) {
        return configCache.get(key);
    }

    @Override
    public boolean setConfigValue(String key, String value) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        SystemConfig config = getOne(wrapper);
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
        } else {
            config.setConfigValue(value);
        }
        return saveOrUpdate(config);
    }

    @Override
    public Map<String, String> getAllConfig() {
        return new HashMap<>(configCache);
    }

    @Override
    public int getQuietStartHour() {
        String val = configCache.get("quiet_start");
        if (val == null) return 20;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 20; }
    }

    @Override
    public int getQuietEndHour() {
        String val = configCache.get("quiet_end");
        if (val == null) return 8;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 8; }
    }

    @Override
    public String getWechatWebhook() {
        return configCache.get("wechat_webhook");
    }

    @Override
    public String getLogWechatWebhook() {
        return configCache.get("log_wechat_webhook");
    }
}
