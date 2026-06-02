package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("SELECT DISTINCT module FROM operation_log WHERE module IS NOT NULL ORDER BY module")
    List<String> selectDistinctModules();

    @Select("SELECT * FROM operation_log WHERE ip = #{ip} AND operation_type = 'VISIT' AND module = '首页' AND create_time >= #{startOfDay} AND create_time < #{endOfDay} LIMIT 1")
    OperationLog selectTodayVisitLog(String ip, LocalDateTime startOfDay, LocalDateTime endOfDay);
}
