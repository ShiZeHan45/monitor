package com.szh.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        LocalDateTime date = LocalDate.now().minusDays(14).atStartOfDay();
        int clear = getBaseMapper().clear(date);
        logger.info("删除信息推送记录条数{}",clear);
    }

    @Override
    public long countLogs(String date, String environment) {
        LambdaQueryWrapper<MsgSendLog> query = new LambdaQueryWrapper<>();
        if (date != null && !date.isEmpty()) {
            query.like(MsgSendLog::getCreateTime, date);
        }
        if (environment != null && !environment.isEmpty()) {
            query.eq(MsgSendLog::getEnvironmentName, environment);
        }
        return getBaseMapper().selectCount(query);
    }
}
