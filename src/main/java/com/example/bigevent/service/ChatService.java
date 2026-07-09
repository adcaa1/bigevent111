package com.example.bigevent.service;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.vo.ChatGroupMemberVO;
import com.example.bigevent.domain.vo.ChatGroupVO;
import com.example.bigevent.domain.vo.ChatMessageVO;
import com.example.bigevent.domain.vo.ConversationVO;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 保存单聊消息
     */
    ChatMessage savePrivateMessage(Integer senderId, Integer receiverId, String content, String tempId);

    /**
     * 保存群聊消息
     */
    ChatMessage saveGroupMessage(Integer senderId, Integer groupId, String content, String tempId);

    /**
     * 获取单聊历史记录
     */
    List<ChatMessageVO> getPrivateHistory(Integer userId, Integer friendId);

    /**
     * 分页获取单聊历史记录
     * @param userId 当前用户
     * @param friendId 聊天对象
     * @param page 页码（从1开始）
     * @param pageSize 每页条数
     * @return 消息列表（按时间升序，最新的在最后）
     */
    List<ChatMessageVO> getPrivateHistoryPage(Integer userId, Integer friendId, int page, int pageSize);

    /**
     * 统计单聊消息总数
     */
    long countPrivateMessages(Integer userId, Integer friendId);

    /**
     * 获取群聊历史记录
     */
    List<ChatMessageVO> getGroupHistory(Integer groupId);

    /**
     * 分页获取群聊历史记录
     */
    List<ChatMessageVO> getGroupHistoryPage(Integer groupId, int page, int pageSize);

    /**
     * 统计群聊消息总数
     */
    long countGroupMessages(Integer groupId);

    /**
     * 获取会话列表
     */
    List<ConversationVO> getConversations(Integer userId);

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

    /**
     * 根据发送者和 tempId 查询消息（幂等重推用）
     */
    ChatMessage getMessageBySenderAndTempId(Integer senderId, String tempId);
}
