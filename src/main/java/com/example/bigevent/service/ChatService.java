package com.example.bigevent.service;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.vo.ChatGroupMemberVO;
import com.example.bigevent.domain.vo.ChatGroupVO;
import com.example.bigevent.domain.vo.ChatMessageVO;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 保存单聊消息
     */
    ChatMessage savePrivateMessage(Integer senderId, Integer receiverId, String content);

    /**
     * 保存群聊消息
     */
    ChatMessage saveGroupMessage(Integer senderId, Integer groupId, String content);

    /**
     * 获取单聊历史记录
     */
    List<ChatMessageVO> getPrivateHistory(Integer userId, Integer friendId);

    /**
     * 获取群聊历史记录
     */
    List<ChatMessageVO> getGroupHistory(Integer groupId);

    /**
     * 标记与某用户的所有消息为已读
     */
    void markAsRead(Integer userId, Integer friendId);

    /**
     * 获取某用户发来的未读消息数
     */
    long countUnread(Integer userId, Integer friendId);

    /**
     * 获取总未读消息数
     */
    long countTotalUnread(Integer userId);

    /**
     * 创建群聊
     */
    ChatGroup createGroup(String name, Integer creatorId, List<Integer> memberIds);

    /**
     * 添加群成员
     */
    void addGroupMember(Integer groupId, Integer userId, Integer role);

    /**
     * 移除群成员
     */
    void removeGroupMember(Integer groupId, Integer userId, Integer operatorId);

    /**
     * 获取用户加入的群列表
     */
    List<ChatGroupVO> getUserGroups(Integer userId);

    /**
     * 获取群成员列表
     */
    List<ChatGroupMemberVO> getGroupMembers(Integer groupId);

    /**
     * 判断用户是否在群中
     */
    boolean isGroupMember(Integer groupId, Integer userId);

    /**
     * 根据ID获取群信息
     */
    ChatGroup getGroupById(Integer groupId);
}
