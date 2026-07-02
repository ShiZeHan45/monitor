package com.szh.monitor.scheduled;

import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

@Component
public class SqlDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(SqlDataSourceHealthChecker.class);

    @Autowired
    private SqlDataSourceService dataSourceService;

    @Scheduled(fixedRate = 300_000)
    public void checkHealth() {
        List<SqlDataSource> dataSources = dataSourceService.listEnabled();
        for (SqlDataSource ds : dataSources) {
            checkDataSourceHealth(ds);
        }
    }

    private void checkDataSourceHealth(SqlDataSource ds) {
        String environmentName = ds.getEnvironmentName();
        try {
            Class.forName(ds.getDriverClassName());
            try (Connection conn = DriverManager.getConnection(
                    ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
            dataSourceService.updateOnlineStatus(ds.getId(), true);
        } catch (Exception e) {
            logger.error("数据源 [{}] 健康检查异常: {}", environmentName, e.getMessage());
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }
}
