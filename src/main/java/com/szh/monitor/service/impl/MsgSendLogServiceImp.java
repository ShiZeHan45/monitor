package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.mapper.MsgSendLogMapper;
import com.szh.monitor.service.MsgSendLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MsgSendLogServiceImp extends ServiceImpl<MsgSendLogMapper, MsgSendLog> implements MsgSendLogService {
    @Override
    public List<MsgSendLog> findSendStatusFalse() {
        return getBaseMapper().findSendStatusFalse();
    }
}
