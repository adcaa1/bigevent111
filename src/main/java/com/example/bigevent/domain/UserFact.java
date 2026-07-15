package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关键事实（长期业务记忆）
 */
@Data
public class UserFact {

    private Long id;

    private Integer userId;

    private String factKey;

    private String factValue;

    private LocalDateTime updateTime;
}
