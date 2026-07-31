package com.example.bigevent.domain.vo;

import lombok.Data;

/**
 * WebSocket 实时推送消息视图对象
 */
@Data
public class WsMessageVO {
    /**
     * 消息类型：private | group
     */
    private String type;

    /**
     * 消息ID
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
     * 会话ID：private:小:大 或 group:群ID
     */
    private String conversationId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间（格式化字符串）
     */
    private String createTime;

    /**
     * 客户端临时ID，用于确认
     */
    private String tempId;
}
