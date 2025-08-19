package com.szh.monitor.service;

import com.szh.monitor.form.MsgForm;

import java.util.function.Consumer;

/**
 * 消息推送接口，可以实现企业微信机器人、邮件、短信等推送
 */
public interface SendService {
    void sendMsg(MsgForm msgForm, Consumer<StringBuilder> msg);
}
