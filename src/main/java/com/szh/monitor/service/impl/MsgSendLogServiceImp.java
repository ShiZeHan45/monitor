package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.mapper.MsgSendLogMapper;
import com.szh.monitor.service.MsgSendLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MsgSendLogServiceImp extends ServiceImpl<MsgSendLogMapper, MsgSendLog> implements MsgSendLogService {
    Logger logger = LoggerFactory.getLogger(MsgSendLogServiceImp.class);
    @Override
    public List<MsgSendLog> findSendStatusFalse() {
        return getBaseMapper().findSendStatusFalse();
    }

    @Override
    public void clear() {
        //只保留近14天的推送记录
        LocalDateTime date = LocalDate.now().minusDays(14).atStartOfDay();
        int clear = getBaseMapper().clear(date);
        logger.info("删除信息推送记录条数{}",clear);
    }
}
