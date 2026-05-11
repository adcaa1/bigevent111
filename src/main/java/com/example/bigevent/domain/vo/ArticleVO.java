package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleVO {
    private Integer id;             // 文章ID
    private String title;           // 文章标题
    private String content;         // 文章内容
    private String coverImg;        // 封面图像
    private String state;           // 发布状态
    private Integer categoryId;     // 文章分类id
    private Integer createUser;     // 创建人ID
    private String nickname;        // 作者昵称
    private String userPic;         // 作者头像
    private LocalDateTime createTime;  // 创建时间
    private LocalDateTime updateTime;  // 更新时间
}
