package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系实体类
 * 记录谁关注了谁
 */
@Data
public class Follow {
    private Integer id;
    private Integer userId;         // 关注者
    private Integer followUserId;   // 被关注者
    private LocalDateTime createTime;
}
