package com.szh.monitor.vo;

import com.szh.monitor.enums.MsgType;
import lombok.Data;

@Data
public class MsgVO {
    private MsgType msgType;
    private String fileName;
    private String message;
    private String environmentName;

}
