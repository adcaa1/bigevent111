package com.example.bigevent.domain.vo;

import lombok.Data;

/**
 * 用户主页信息VO（返回给前端的用户主页数据）
 * 包含用户基本信息 + 粉丝/关注统计 + 文章列表
 */
@Data
public class UserProfileVO {
    private Integer id;             // 用户ID
    private String username;        // 用户名
    private String nickname;        // 昵称
    private String intro;           // 个人简介
    private String userPic;         // 头像地址
    private String email;           // 邮箱
    private Long fansCount;         // 粉丝数量
    private Long followCount;       // 关注数量
    private Boolean isFollowed;     // 当前登录用户是否已关注此人
}
