package com.szh.monitor.scheduled;

import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.service.GrafanaDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.List;

@Component
public class GrafanaDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(GrafanaDataSourceHealthChecker.class);
    private static final int PING_TIMEOUT = 3000;

    @Autowired
    private GrafanaDataSourceService dataSourceService;

    @Scheduled(fixedRate = 60_000)
    public void checkHealth() {
        List<GrafanaDataSource> dataSources = dataSourceService.listEnabled();
        for (GrafanaDataSource ds : dataSources) {
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(GrafanaDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        String url = ds.getUrl();

        try {
            String ip = extractIp(url);
            if (ip == null) {
                logger.warn("数据源 [{}] 无法从URL提取IP地址: {}", environmentName, url);
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            boolean isReachable = InetAddress.getByName(ip).isReachable(PING_TIMEOUT);

            dataSourceService.updateOnlineStatus(ds.getId(), isReachable);

            if (isReachable) {
                logger.info("数据源 [{}] 在线 (IP: {})", environmentName, ip);
            } else {
                logger.warn("数据源 [{}] 离线 (IP: {})", environmentName, ip);
            }

        } catch (Exception e) {
            logger.error("数据源 [{}] 健康检查异常: {}", environmentName, e.getMessage());
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }

    private String extractIp(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getHost();
        } catch (Exception e) {
            logger.error("解析URL失败: {}", urlStr, e);
            return null;
        }
    }
}
