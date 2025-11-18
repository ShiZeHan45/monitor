package com.szh.monitor.service.impl;

import com.szh.monitor.form.MsgForm;
import com.szh.monitor.service.SendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 推送总调度
 */
@Service
public class SendDispatchService {
    private List<SendService> sendServices;

    @Autowired
    public SendDispatchService(List<SendService> sendServices) {
        this.sendServices = sendServices;
    }

    public void sendMsg(MsgForm msgForm, Consumer<StringBuilder> appendMsg){
        for (SendService sendService : sendServices) {
            sendService.sendMsg(msgForm,appendMsg);
        }
    }

    public void sendSimpleMarkDownMsg(String content){
        for (SendService sendService : sendServices) {
            sendService.sendSimpleMarkDownMsgByLog(content);
        }
    }
}
