package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 历史聊天向量文档（写入 ES chat_history_vector）
 */
@Data
public class ChatHistoryChunk {

    private String id;

    private String userId;

    private String conversationId;

    private String content;

    private List<Float> embedding;

    private LocalDateTime createTime;
}
