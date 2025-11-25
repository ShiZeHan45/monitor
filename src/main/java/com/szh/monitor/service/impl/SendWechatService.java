package com.szh.monitor.service.impl;

import com.szh.monitor.enums.MsgType;
import com.szh.monitor.form.MsgForm;
import com.szh.monitor.form.WechatMarkDownMessage;
import com.szh.monitor.form.WechatMessage;
import com.szh.monitor.service.SendService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Service
public class SendWechatService implements SendService {
    private final RestTemplate restTemplate;

    @Value("${app.wechat-webhook}")
    private String webhookUrl;

    @Value("${app.log-wechat-webhook}")
    private String logWebhookUrl;

    public SendWechatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getLogWebhookUrl() {
        return logWebhookUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    @Override
    public void sendMsg(MsgForm msgForm, Consumer<StringBuilder> msg) {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不推送短信
            return;
        }
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

        // 使用RestTemplate发送HTTP请求
        // 实际实现见定时任务类中的restTemplate
        restTemplate.postForEntity(
                getWebhookUrl(),
                wechatMessage,
                String.class
        );
    }


    @Override
    public void sendSimpleMarkDownMsgByLog(String content) {
        int hour = LocalDateTime.now().getHour();
        if (hour >= 20 || hour <= 8) {
            // 20点-8点不推送短信
            return;
        }
        WechatMarkDownMessage wechatMessage = new WechatMarkDownMessage();
        wechatMessage.setMarkdown(new WechatMarkDownMessage.Text(content));
        restTemplate.postForEntity(
                getLogWebhookUrl(),
                wechatMessage,
                String.class
        );
    }
}
