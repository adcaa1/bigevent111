package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息视图对象
 */
@Data
public class ChatMessageVO {
    private Long id;
    private Integer senderId;
    private String senderNickname;
    private String senderAvatar;
    private Integer receiverId;
    private Integer groupId;
    private String conversationId;
    private String groupName;
    private String content;
    private Integer type;       // 0-单聊 1-群聊
    private Integer isRead;
    private LocalDateTime createTime;
}
