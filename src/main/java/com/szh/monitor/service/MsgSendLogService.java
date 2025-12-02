package com.szh.monitor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.szh.monitor.entity.MsgSendLog;

import java.util.List;

public interface MsgSendLogService extends IService<MsgSendLog> {
    List<MsgSendLog> findSendStatusFalse();
}
