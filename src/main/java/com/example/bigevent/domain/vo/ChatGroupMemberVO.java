package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员视图对象
 */
@Data
public class ChatGroupMemberVO {
    private Integer id;
    private Integer groupId;
    private Integer userId;
    private String username;
    private String nickname;
    private String userPic;
    private Integer role; // 0-成员 1-管理员 2-群主
    private LocalDateTime joinTime;
}
