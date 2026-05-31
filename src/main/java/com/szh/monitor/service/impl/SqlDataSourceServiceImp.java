package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.SqlDataSource;
import com.szh.monitor.mapper.SqlDataSourceMapper;
import com.szh.monitor.service.SqlDataSourceService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SqlDataSourceServiceImp extends ServiceImpl<SqlDataSourceMapper, SqlDataSource> implements SqlDataSourceService {

    private final ApplicationContext applicationContext;

    public SqlDataSourceServiceImp(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public List<SqlDataSource> listEnabled() {
        LambdaQueryWrapper<SqlDataSource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SqlDataSource::getEnabled, 1);
        return list(queryWrapper);
    }

    @Override
    public boolean updateOnlineStatus(Long dataSourceId, boolean isOnline) {
        SqlDataSource dataSource = getById(dataSourceId);
        if (dataSource == null) {
            return false;
        }
        dataSource.setIsOnline(isOnline ? 1 : 0);
        dataSource.setLastCheckTime(LocalDateTime.now());
        dataSource.setUpdateTime(LocalDateTime.now());
        return updateById(dataSource);
    }

    @Override
    public boolean containsBean(String beanName) {
        try {
            return applicationContext.containsBean(beanName);
        } catch (BeansException e) {
            return false;
        }
    }

    @Override
    public <T> T getBean(String beanName, Class<T> requiredType) {
        try {
            return applicationContext.getBean(beanName, requiredType);
        } catch (BeansException e) {
            return null;
        }
    }

    @Override
    public SqlDataSource getByEnvironmentName(String environmentName) {
        LambdaQueryWrapper<SqlDataSource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SqlDataSource::getEnvironmentName, environmentName);
        return getOne(queryWrapper);
    }

    @Override
    public boolean save(SqlDataSource entity) {
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getIsOnline() == null) {
            entity.setIsOnline(0);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(SqlDataSource entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }
}
