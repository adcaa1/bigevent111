package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊实体
 */
@Data
public class ChatGroup {
    private Integer id;
    private String name;
    private Integer creatorId;
    private String avatar;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
