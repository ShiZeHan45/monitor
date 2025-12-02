package com.szh.monitor.form;

import lombok.Data;

@Data
public class WechatMessage {
    private String msgtype;
    private Text text;
    private Text markdown;

    @Data
    public static class Text {
        private String content;

        public Text(String content) {
            this.content = content;
        }
    }
}
