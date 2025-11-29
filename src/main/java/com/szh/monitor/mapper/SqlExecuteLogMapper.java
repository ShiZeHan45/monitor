package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.SqlExecuteLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SqlExecuteLogMapper extends BaseMapper<SqlExecuteLog> {
    @Select("SELECT * FROM sql_execute_log WHERE environment_name=#{environmentName} and sql_file_name=#{name} and execute_date=#{date}")
    SqlExecuteLog findEnvironmentNameAndFileName(String environmentName, String name,Integer date);
    @Select("SELECT * FROM sql_execute_log WHERE environment_name=#{environmentName} and execute_date=#{date}")
    List<SqlExecuteLog> findEnvironmentName(String environmentName,Integer date);
}
