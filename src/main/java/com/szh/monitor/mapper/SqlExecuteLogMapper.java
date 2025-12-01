package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.SqlExecuteLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SqlExecuteLogMapper extends BaseMapper<SqlExecuteLog> {
    @Select("SELECT * FROM sql_execute_log WHERE environment_name=#{environmentName} and sql_file_name=#{name} and execute_date=#{date}")
    SqlExecuteLog findEnvironmentNameAndFileName(String environmentName, String name,Integer date);
    @Select("SELECT * FROM sql_execute_log WHERE environment_name=#{environmentName} and execute_date=#{date}")
    List<SqlExecuteLog> findEnvironmentName(String environmentName,Integer date);
    @Select("SELECT * FROM sql_execute_log WHERE environment_name=#{environmentName} and execute_date=#{date} and failed_count>0")
    List<SqlExecuteLog> findEnvironmentNameAndFailedCountGt0(String environmentName, Integer date);


    @Select("SELECT failed_count FROM sql_execute_log WHERE environment_name=#{environmentName} and execute_date=#{date} and failed_count>0 order by failed_count desc limit 1")
    Integer findMaxFailedCount(String environmentName, Integer date);
    @Update("UPDATE sql_execute_log SET failed_count=0,failed_count_reset_time=#{currTime} where environment_name=#{environmentName} and execute_date=#{date} and failed_count>0")
    void resetFailedCount(String environmentName, Integer date,Integer currTime);

    @Delete("delete from sql_execute_log where execute_date<#{date}")
    int clear(Integer date);
}
