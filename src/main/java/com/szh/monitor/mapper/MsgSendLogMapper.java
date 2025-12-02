package com.szh.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.szh.monitor.entity.MsgSendLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MsgSendLogMapper extends BaseMapper<MsgSendLog> {
    @Select("SELECT * FROM msg_send_log WHERE send_status=false order by create_time asc")
    List<MsgSendLog> findSendStatusFalse();
}
