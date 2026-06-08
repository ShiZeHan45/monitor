package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.GrafanaMonitorRule;
import com.szh.monitor.mapper.GrafanaMonitorRuleMapper;
import com.szh.monitor.service.GrafanaMonitorRuleService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (entity.getTotalCollectCount() == null) {
            entity.setTotalCollectCount(0L);
        }
        if (entity.getDailyCollectCount() == null) {
            entity.setDailyCollectCount(0L);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(GrafanaMonitorRule entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public List<Map<String, Object>> getEnvironmentCollectStats() {
        List<GrafanaMonitorRule> all = list();
        Map<String, Map<String, Object>> envMap = new HashMap<>();
        for (GrafanaMonitorRule rule : all) {
            if (rule.getTotalCollectCount() == null) continue;
            String envName = getEnvironmentNameByRule(rule);
            if (envName == null) continue;
            envMap.computeIfAbsent(envName, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("environmentName", envName);
                m.put("totalCollectCount", 0L);
                return m;
            });
            Map<String, Object> m = envMap.get(envName);
            m.put("totalCollectCount", (Long) m.get("totalCollectCount") + rule.getTotalCollectCount());
        }
        return envMap.values().stream()
                .sorted((a, b) -> ((String) a.get("environmentName")).compareTo((String) b.get("environmentName")))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getEnvironmentDailyCollectStats(LocalDate date) {
        List<GrafanaMonitorRule> all = list(new LambdaQueryWrapper<GrafanaMonitorRule>()
                .eq(GrafanaMonitorRule::getCollectDate, date));
        Map<String, Map<String, Object>> envMap = new HashMap<>();
        for (GrafanaMonitorRule rule : all) {
            if (rule.getDailyCollectCount() == null) continue;
            String envName = getEnvironmentNameByRule(rule);
            if (envName == null) continue;
            envMap.computeIfAbsent(envName, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("environmentName", envName);
                m.put("dailyCollectCount", 0L);
                return m;
            });
            Map<String, Object> m = envMap.get(envName);
            m.put("dailyCollectCount", (Long) m.get("dailyCollectCount") + rule.getDailyCollectCount());
        }
        return envMap.values().stream()
                .sorted((a, b) -> ((String) a.get("environmentName")).compareTo((String) b.get("environmentName")))
                .collect(Collectors.toList());
    }

    @Override
    public void updateLastTs(Long ruleId, String environmentName, Long dataSourceId, long lastTs, long collectCount) {
        GrafanaMonitorRule rule = getById(ruleId);
        if (rule == null) return;
        LocalDateTime lastTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastTs), ZoneId.systemDefault());
        LocalDate today = LocalDate.now();

        rule.setLastTs(lastTs);
        rule.setLastTime(lastTime);
        if (rule.getTotalCollectCount() == null) rule.setTotalCollectCount(0L);
        rule.setTotalCollectCount(rule.getTotalCollectCount() + collectCount);
        if (rule.getDailyCollectCount() == null || !today.equals(rule.getCollectDate())) {
            rule.setDailyCollectCount(collectCount);
            rule.setCollectDate(today);
        } else {
            rule.setDailyCollectCount(rule.getDailyCollectCount() + collectCount);
        }
        updateById(rule);
    }

    @Override
    public void updateLastTs(Long ruleId, LocalDateTime dateTime) {
        GrafanaMonitorRule rule = getById(ruleId);
        if (rule == null) return;
        long ts = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        rule.setLastTs(ts);
        rule.setLastTime(dateTime);
        updateById(rule);
        // 刷新 Grafana 内存中的 lastTsMap
        grafanaLogServiceImp.initLastTsMapForRule(rule.getDataSourceId(), ruleId, ts);
    }

    @Lazy
    @javax.annotation.Resource
    private GrafanaLogServiceImp grafanaLogServiceImp;

    private String getEnvironmentNameByRule(GrafanaMonitorRule rule) {
        try {
            GrafanaDataSourceServiceImp dsService = SpringContextUtil.getBean(GrafanaDataSourceServiceImp.class);
            com.szh.monitor.entity.GrafanaDataSource ds = dsService.getById(rule.getDataSourceId());
            return ds != null ? ds.getEnvironmentName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}