package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.GrafanaDataSource;
import com.szh.monitor.mapper.GrafanaDataSourceMapper;
import com.szh.monitor.service.GrafanaDataSourceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GrafanaDataSourceServiceImp extends ServiceImpl<GrafanaDataSourceMapper, GrafanaDataSource> implements GrafanaDataSourceService {

    @Override
    public List<GrafanaDataSource> listEnabled() {
        LambdaQueryWrapper<GrafanaDataSource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GrafanaDataSource::getEnabled, 1);
        return list(queryWrapper);
    }

    @Override
    public boolean save(GrafanaDataSource entity) {
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(GrafanaDataSource entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }
}