package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.LogCollectTimeInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogCollectTimeInfoMapper extends BaseMapper<LogCollectTimeInfo> {
    @Select("SELECT * FROM log_collect_time_info WHERE environment_name=#{environmentName} AND rule_name = #{ruleName}")
    LogCollectTimeInfo findEnvironmentNameAndRuleName(String environmentName, String ruleName);

    @Select("SELECT environment_name as environmentName, SUM(total_collect_count) as totalCollectCount FROM log_collect_time_info WHERE total_collect_count IS NOT NULL GROUP BY environment_name ORDER BY environment_name")
    List<Map<String, Object>> getEnvironmentCollectStats();

    @Select("SELECT environment_name as environmentName, SUM(daily_collect_count) as dailyCollectCount FROM log_collect_time_info WHERE collect_date = #{collectDate} AND daily_collect_count IS NOT NULL GROUP BY environment_name ORDER BY environment_name")
    List<Map<String, Object>> getEnvironmentDailyCollectStats(java.time.LocalDate collectDate);
}
