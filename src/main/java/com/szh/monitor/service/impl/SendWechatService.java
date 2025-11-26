package com.szh.monitor.service.impl;

import com.szh.monitor.config.BaseConfig;
import com.szh.monitor.enums.MsgType;
import com.szh.monitor.form.MsgForm;
import com.szh.monitor.form.WechatMarkDownMessage;
import com.szh.monitor.form.WechatMessage;
import com.szh.monitor.service.SendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
public class SendWechatService implements SendService {
    Logger logger = LoggerFactory.getLogger(SendWechatService.class);
    private final RestTemplate restTemplate;

    @Autowired
    private BaseConfig baseConfig;

    public SendWechatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    @Override
    public void sendMsg(MsgForm msgForm, Consumer<StringBuilder> msg) {
        int hour = LocalDateTime.now().getHour();

        StringBuilder sendMessage = new StringBuilder();
        if(MsgType.ERROR.equals(msgForm.getMsgType())){
            sendMessage.append("⚠️");
        }else{
            sendMessage.append("✅");
        }
        sendMessage.append(msgForm.getEnvironmentName()).append("-").append(msgForm.getTitle()).append("\n");
        msg.accept(sendMessage);

        WechatMessage wechatMessage = new WechatMessage();
        wechatMessage.setText(new WechatMessage.Text(sendMessage.toString()));
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不推送短信
            logger.info("20点-8点不推送预警 预警内容{}",sendMessage);
            return;
        }
        // 使用RestTemplate发送HTTP请求
        // 实际实现见定时任务类中的restTemplate
        restTemplate.postForEntity(
                baseConfig.getWechatWebhook(),
                wechatMessage,
                String.class
        );
    }


    @Override
    public void sendSimpleMarkDownMsgByLog(String content) {
        int hour = LocalDateTime.now().getHour();
        WechatMarkDownMessage wechatMessage = new WechatMarkDownMessage();
        wechatMessage.setMarkdown(new WechatMarkDownMessage.Text(content));
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不推送短信
            logger.info("20点-8点不推送预警 预警内容{}",content);
            return;
        }
        restTemplate.postForEntity(
                baseConfig.getLogWechatWebhook(),
                wechatMessage,
                String.class
        );
    }
}
