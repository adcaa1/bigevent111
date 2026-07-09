package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体（单聊+群聊合一）
 */
@Data
public class ChatMessage {
    private Long id;
    private Integer senderId;
    private Integer receiverId; // 单聊接收者
    private Integer groupId;    // 群聊群ID
    private String content;
    private Integer type;       // 0-单聊 1-群聊
    private Integer isRead;     // 0-未读 1-已读
    private String tempId;      // 客户端临时ID（用于幂等去重）
    private LocalDateTime createTime;
}
