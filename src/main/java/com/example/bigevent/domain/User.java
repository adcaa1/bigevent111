package com.example.bigevent.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDateTime;

@Data
public class User {
    @NonNull
    private Integer id;//主键ID
    private String username;//用户名
    @JsonIgnore//让密码转为json时忽略，这样查询所有信息就显示不出来密码
    private String password;//密码
    @NonNull
    @Pattern(regexp = "^.{1,10}$")
    private String nickname;//昵称
    private String intro;//个人简介
    private Integer fansVisible;       // 粉丝列表是否公开 1-公开 0-私密
    private Integer followingVisible;  // 关注列表是否公开 1-公开 0-私密
    @NonNull
    private String email;//邮箱
    private String userPic;//用户头像地址
    private LocalDateTime createTime;//创建时间
    private LocalDateTime updateTime;//更新时间
    private Integer deleted;//0-正常 1-已注销
    private Integer departmentId;//所属部门ID
    private Integer role;//0-普通用户 1-管理员
    private Integer fansCount;//粉丝数冗余字段
    private Integer followCount;//关注数冗余字段
    private Integer articleCount;//已发布文章数冗余字段
}
