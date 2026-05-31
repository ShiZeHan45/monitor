package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
