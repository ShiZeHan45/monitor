package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.SqlExecuteRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SqlExecuteRuleMapper extends BaseMapper<SqlExecuteRule> {
    @Select("SELECT * FROM sql_execute_rule WHERE environment_name=#{environmentName}")
    List<SqlExecuteRule> findByEnvironmentName(String environmentName);
}
