package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.LogCollectTimeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LogCollectTimeInfoMapper extends BaseMapper<LogCollectTimeInfo> {
    @Select("SELECT * FROM log_collect_time_info WHERE environment_name=#{environmentName} AND rule_name = #{ruleName}")
    LogCollectTimeInfo findEnvironmentNameAndRuleName(String environmentName, String ruleName);
}
