package com.example.bigevent.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * RabbitMQ 聊天消息事件
 * 用于 WebSocket 服务端向 MQ 发送消息
 */
@Data
public class ChatMessageEvent implements Serializable {

    private Integer senderId;
    private Integer receiverId;   // 单聊接收者
    private Integer groupId;      // 群聊群ID
    private String content;
    private String tempId;        // 客户端临时ID
    private Integer type;         // 0-单聊 1-群聊
}
