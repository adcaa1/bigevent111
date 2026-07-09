package com.example.bigevent.domain.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 广播消息传输对象
 * 用于跨服务器 WebSocket 消息广播
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisChatMessageDTO {

    /**
     * 目标用户ID
     */
    private Integer targetUserId;

    /**
     * 要推送的 WebSocket 消息 JSON
     */
    private String jsonMessage;
}
