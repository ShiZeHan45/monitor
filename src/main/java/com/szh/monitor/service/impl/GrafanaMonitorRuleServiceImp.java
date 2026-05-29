package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.mapper.GrafanaMonitorRuleMapper;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GrafanaMonitorRuleServiceImp extends ServiceImpl<GrafanaMonitorRuleMapper, GrafanaMonitorRule> implements GrafanaMonitorRuleService {

    @Override
    public List<GrafanaMonitorRule> listByDataSourceId(Long dataSourceId) {
        LambdaQueryWrapper<GrafanaMonitorRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GrafanaMonitorRule::getDataSourceId, dataSourceId);
        return list(queryWrapper);
    }

    @Override
    public List<GrafanaMonitorRule> listEnabledByDataSourceId(Long dataSourceId) {
        LambdaQueryWrapper<GrafanaMonitorRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GrafanaMonitorRule::getDataSourceId, dataSourceId);
        queryWrapper.eq(GrafanaMonitorRule::getEnabled, 1);
        return list(queryWrapper);
    }

    @Override
    public boolean removeByDataSourceId(Long dataSourceId) {
        LambdaQueryWrapper<GrafanaMonitorRule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GrafanaMonitorRule::getDataSourceId, dataSourceId);
        return remove(queryWrapper);
    }

    @Override
    public boolean save(GrafanaMonitorRule entity) {
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getContextLines() == null) {
            entity.setContextLines(5);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(GrafanaMonitorRule entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }
}