package com.szh.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("msg_send_log")
public class MsgSendLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String content;

    private String sendWebhook;

    private String msgType;

    private LocalDateTime createTime;

    private LocalDateTime sendDate;

    private Boolean sendStatus;

}
