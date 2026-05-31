package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.SqlDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public interface SqlDataSourceService extends IService<SqlDataSource> {
    List<SqlDataSource> listEnabled();
    boolean updateOnlineStatus(Long dataSourceId, boolean isOnline);
    boolean containsBean(String beanName);
    <T> T getBean(String beanName, Class<T> requiredType);
    SqlDataSource getByEnvironmentName(String environmentName);
}
