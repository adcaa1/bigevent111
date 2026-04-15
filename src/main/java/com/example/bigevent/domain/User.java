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
    @NonNull
    private String email;//邮箱
    private String userPic;//用户头像地址
    private LocalDateTime createTime;//创建时间
    private LocalDateTime updateTime;//更新时间
}
