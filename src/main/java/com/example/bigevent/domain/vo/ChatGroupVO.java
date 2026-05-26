package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊视图对象
 */
@Data
public class ChatGroupVO {
    private Integer id;
    private String name;
    private Integer creatorId;
    private String avatar;
    private LocalDateTime createTime;
    private Integer memberCount;
    private List<ChatGroupMemberVO> members;
}
