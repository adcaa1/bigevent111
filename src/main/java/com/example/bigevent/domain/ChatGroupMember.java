package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员实体
 */
@Data
public class ChatGroupMember {
    private Integer id;
    private Integer groupId;
    private Integer userId;
    private Integer role; // 0-成员 1-管理员 2-群主
    private LocalDateTime joinTime;
}
