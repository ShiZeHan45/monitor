package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.GrafanaMonitorRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface GrafanaMonitorRuleMapper extends BaseMapper<GrafanaMonitorRule> {
    List<GrafanaMonitorRule> selectByDataSourceId(Long dataSourceId);
}