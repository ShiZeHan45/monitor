package com.szh.monitor.scheduled;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.service.SqlDataSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class SqlDataSourceHealthChecker {
    private static final Logger logger = LoggerFactory.getLogger(SqlDataSourceHealthChecker.class);

    @Autowired
    private SqlDataSourceService dataSourceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            String beanName = ds.getEnvironmentName() + "JdbcTemplate";

            if (!dataSourceService.containsBean(beanName)) {
                logger.warn("数据源 [{}] 的JdbcTemplate Bean不存在", environmentName);
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            JdbcTemplate jdbcTemplate = dataSourceService.getBean(beanName, JdbcTemplate.class);
            if (jdbcTemplate == null) {
                dataSourceService.updateOnlineStatus(ds.getId(), false);
                return;
            }

            jdbcTemplate.execute("SELECT 1");
            dataSourceService.updateOnlineStatus(ds.getId(), true);

        } catch (Exception e) {
            dataSourceService.updateOnlineStatus(ds.getId(), false);
        }
    }
}
