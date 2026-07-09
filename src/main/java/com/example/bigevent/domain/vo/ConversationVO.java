package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表VO
 * 展示每个对话对方的信息、最后一条消息预览和未读数
 */
@Data
public class ConversationVO {
    private Integer userId;         // 对方用户ID
    private String nickname;        // 对方昵称
    private String userPic;         // 对方头像
    private String lastMessage;     // 最后一条消息内容（预览）
    private LocalDateTime lastTime; // 最后一条消息时间
    private Long unreadCount;       // 未读消息数
}