package com.szh.monitor.scheduled;

import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Component
public class SqlDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(SqlDataSourceHealthChecker.class);

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
        try {
            String beanName = ds.getEnvironmentName() + "JdbcTemplate";

            if (!dataSourceService.containsBean(beanName)) {
                logger.warn("数据源 [{}] 的 JdbcTemplate Bean 不存在", environmentName);
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            JdbcTemplate jdbcTemplate = dataSourceService.getBean(beanName, JdbcTemplate.class);

            if (jdbcTemplate == null) {
                logger.warn("数据源 [{}] 的 JdbcTemplate Bean 为 null", environmentName);
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                logger.warn("数据源 [{}] 的 DataSource 为 null", environmentName);
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            jdbcTemplate.execute("SELECT 1");
            dataSourceService.updateOnlineStatus(ds.getId(), true);
            logger.info("数据源 [{}] 在线", environmentName);

        } catch (Exception e) {
            logger.error("数据源 [{}] 健康检查异常: {}", environmentName, e.getMessage());
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }
}
