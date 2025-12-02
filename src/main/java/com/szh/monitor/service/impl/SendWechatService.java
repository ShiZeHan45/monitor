package com.szh.monitor.service.impl;

import com.szh.monitor.config.BaseConfig;
import com.szh.monitor.entity.MsgSendLog;
import com.szh.monitor.enums.MsgType;
import com.szh.monitor.form.MsgForm;
import com.szh.monitor.form.WechatMessage;
import com.szh.monitor.service.MsgSendLogService;
import com.szh.monitor.service.SendService;
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
        sendNewMsgAndStore(sendMessage.toString(),"text",baseConfig.getWechatWebhook());
    }

    @Scheduled(cron = "0 30 9 * * ?")
//    @Scheduled(cron = "0 0/5 * * * ?")
    @Transactional
    public void pushMsg(){
       List<MsgSendLog> msgSendLogs = sendLogService.findSendStatusFalse();
        for (MsgSendLog msgSendLog : msgSendLogs) {
            //5秒发一条
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            sendMsgAndStore(msgSendLog.getContent(),msgSendLog.getMsgType(),msgSendLog.getSendWebhook(),msgSendLog);
        }
    }

    private void sendNewMsgAndStore(String msg,String msgType,String webHook) {
        MsgSendLog msgSendLog = new MsgSendLog();
        msgSendLog.setCreateTime(LocalDateTime.now());
        msgSendLog.setMsgType(msgType);
        msgSendLog.setContent(msg);
        msgSendLog.setSendWebhook(webHook);
        sendMsgAndStore(msg, msgType, webHook, msgSendLog);
    }

    private void sendMsgAndStore(String msg, String msgType, String webHook, MsgSendLog msgSendLog) {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不推送短信
            logger.info("20点-8点不推送预警 推送内容固化,择机推送");
            msgSendLog.setSendStatus(false);
        }else{
            // 使用RestTemplate发送HTTP请求
            // 实际实现见定时任务类中的restTemplate
            WechatMessage wechatMessage = new WechatMessage();
            switch (msgType){
                case "text":
                    wechatMessage.setMsgtype("text");
                    wechatMessage.setText(new WechatMessage.Text(msg));
                case "markdown":
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
    public void sendSimpleMarkDownMsgByLog(String content) {
        String sendContent = "🚨"+content;
        sendNewMsgAndStore(sendContent,"markdown",baseConfig.getLogWechatWebhook());
    }


}
