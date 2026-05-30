package com.szh.monitor.scheduled;

import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(SqlDataSourceHealthChecker.class);
    private static final int PING_TIMEOUT = 3000;
    private static final Pattern JDBC_IP_PATTERN = Pattern.compile("://([^/:]+)");

    @Autowired
    private SqlDataSourceService dataSourceService;

    @Scheduled(fixedRate = 60_000)
    public void checkHealth() {
        List<SqlDataSource> dataSources = dataSourceService.listEnabled();
        for (SqlDataSource ds : dataSources) {
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(SqlDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        String jdbcUrl = ds.getJdbcUrl();

        try {
            String ip = extractIpFromJdbcUrl(jdbcUrl);
            if (ip == null) {
                logger.warn("数据源 [{}] 无法从JDBC URL提取IP地址: {}", environmentName, jdbcUrl);
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

    private String extractIpFromJdbcUrl(String jdbcUrl) {
        try {
            Matcher matcher = JDBC_IP_PATTERN.matcher(jdbcUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        } catch (Exception e) {
            logger.error("解析JDBC URL失败: {}", jdbcUrl, e);
            return null;
        }
    }
}
