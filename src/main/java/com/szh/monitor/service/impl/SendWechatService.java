package com.szh.monitor.service.impl;

import com.szh.monitor.config.BaseConfig;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.enums.MsgType;
import com.szh.monitor.form.MsgForm;
import com.szh.monitor.form.WechatMessage;
import com.szh.monitor.service.MsgSendLogService;
import com.szh.monitor.service.SendService;
import com.szh.monitor.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
public class SendWechatService implements SendService {
    Logger logger = LoggerFactory.getLogger(SendWechatService.class);
    private final RestTemplate restTemplate;

    @Autowired
    private BaseConfig baseConfig;
    @Autowired
    private MsgSendLogService sendLogService;
    @Autowired
    private SystemConfigService systemConfigService;


    public SendWechatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    @Override
    public void sendMsg(MsgForm msgForm, Consumer<StringBuilder> msg) {
        StringBuilder sendMessage = new StringBuilder();
        if(MsgType.ERROR.equals(msgForm.getMsgType())){
            sendMessage.append("⚠️");
        }else{
            sendMessage.append("✅");
        }
        sendMessage.append(msgForm.getEnvironmentName()).append("-").append(msgForm.getTitle()).append("\n");
        msg.accept(sendMessage);
        // 优先使用 MsgForm 中的 webhook，没有则用全局默认
        String webhook = msgForm.getWebhook() != null && !msgForm.getWebhook().isEmpty()
                ? msgForm.getWebhook()
                : systemConfigService.getWechatWebhook();
        if (webhook == null || webhook.isEmpty()) {
            webhook = baseConfig.getWechatWebhook();
        }
        sendNewMsgAndStore(sendMessage.toString(),"text",webhook,msgForm.getEnvironmentName());
    }

    @Scheduled(cron = "0 30 9 * * ?")
    public void pushMsg(){
       List<MsgSendLog> msgSendLogs = sendLogService.findSendStatusFalse();
        for (MsgSendLog msgSendLog : msgSendLogs) {
            try {
                //5秒发一条
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String appendContent="20点~08点 产生的异常消息补推\n";
            if ("markdown".equals(msgSendLog.getMsgType())) {
                appendContent = "> "+appendContent;
            }
            String content = appendContent+msgSendLog.getContent();
            try {
                sendMsgAndStore(content,msgSendLog.getMsgType(),msgSendLog.getSendWebhook(),msgSendLog);
            } catch (Exception e) {
                logger.error("补推消息失败: id={}, webhook={}", msgSendLog.getId(), msgSendLog.getSendWebhook(), e);
            }
        }
    }

    private void sendNewMsgAndStore(String msg,String msgType,String webHook, String environmentName) {
        MsgSendLog msgSendLog = new MsgSendLog();
        msgSendLog.setCreateTime(LocalDateTime.now());
        msgSendLog.setMsgType(msgType);
        msgSendLog.setContent(msg);
        msgSendLog.setSendWebhook(webHook);
        msgSendLog.setEnvironmentName(environmentName);
        sendMsgAndStore(msg, msgType, webHook, msgSendLog);
    }

    public void sendMsgAndStore(String msg, String msgType, String webHook, MsgSendLog msgSendLog) {
        int hour = LocalDateTime.now().getHour();
        int quietStart = systemConfigService.getQuietStartHour();
        int quietEnd = systemConfigService.getQuietEndHour();
        if (hour >= quietStart || hour <= quietEnd) {
            // 20点-8点不推送短信
            logger.info("{}-{}点不推送预警 推送内容固化,择机推送", quietEnd, quietStart);
            msgSendLog.setSendStatus(false);
        }else{
            // 使用RestTemplate发送HTTP请求
            // 实际实现见定时任务类中的restTemplate
            WechatMessage wechatMessage = new WechatMessage();
            if ("text".equals(msgType)) {
                wechatMessage.setMsgtype("text");
                wechatMessage.setText(new WechatMessage.Text(msg));
            } else if ("markdown".equals(msgType)) {
                wechatMessage.setMsgtype("markdown");
                wechatMessage.setMarkdown(new WechatMessage.Text(msg));
            }
            restTemplate.postForEntity(
                    webHook,
                    wechatMessage,
                    String.class
            );
            msgSendLog.setSendDate(LocalDateTime.now());
            msgSendLog.setSendStatus(true);
        }
        sendLogService.saveOrUpdate(msgSendLog);
    }


    @Override
    public void sendSimpleMarkDownMsgByLog(String content, String environmentName) {
        String webhook = systemConfigService.getLogWechatWebhook();
        if (webhook == null || webhook.isEmpty()) {
            webhook = baseConfig.getLogWechatWebhook();
        }
        sendNewMsgAndStore(content,"markdown", webhook, environmentName);
    }

    @Override
    public void sendSimpleMarkDownMsgByLog(String content, String environmentName, String webhook) {
        if (webhook == null || webhook.isEmpty()) {
            webhook = systemConfigService.getLogWechatWebhook();
            if (webhook == null || webhook.isEmpty()) {
                webhook = baseConfig.getLogWechatWebhook();
            }
        }
        sendNewMsgAndStore(content, "markdown", webhook, environmentName);
    }


}
