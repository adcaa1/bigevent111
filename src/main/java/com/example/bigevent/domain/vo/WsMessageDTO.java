package com.example.bigevent.domain.vo;

import lombok.Data;

/**
 * WebSocket 消息传输对象
 * 用于客户端与服务端之间的 JSON 消息交换
 */
@Data
public class WsMessageDTO {
    /**
     * 消息类型：private | group | ping | system
     */
    private String type;

    /**
     * 子类型（用于 system 消息）：error | onlineStatus
     */
    private String subType;

    /**
     * 消息ID（服务端推送时填充）
     */
    private Long messageId;

    /**
     * 发送者ID
     */
    private Integer senderId;

    /**
     * 接收者ID（单聊）
     */
    private Integer receiverId;

    /**
     * 群ID（群聊）
     */
    private Integer groupId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 系统消息文本
     */
    private String message;

    /**
     * 客户端临时ID，用于确认
     */
    private String tempId;

    /**
     * 创建时间
     */
    private String createTime;
}
