package com.szh.monitor.form;

import com.szh.monitor.enums.MsgType;
import lombok.Data;

@Data
public class MsgForm {
    //消息类型
    private MsgType msgType;
    //标题
    private String title;
    //环境信息
    private String environmentName;

    public MsgForm(MsgType msgType,String title,String environmentName){
        this.msgType = msgType;
        this.title = title;
        this.environmentName = environmentName;
    }

    public static MsgForm builder(MsgType msgType,String title,String environmentName){
        return new MsgForm(msgType,title,environmentName);
    }

}
