package com.example.bigevent.domain.vo;

import lombok.Data;

/**
 * 广场用户卡片VO
 */
@Data
public class UserSquareVO {
    private Integer id;             // 用户ID
    private String username;        // 用户名
    private String nickname;        // 昵称
    private String userPic;         // 头像地址
    private Long fansCount;         // 粉丝数量
    private Long followCount;       // 关注数量
    private Boolean isFollowed;     // 当前登录用户是否已关注此人
}
