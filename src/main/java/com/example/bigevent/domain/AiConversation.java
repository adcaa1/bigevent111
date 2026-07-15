package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体
 */
@Data
public class AiConversation {

    private Long id;

    private Integer userId;

    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
