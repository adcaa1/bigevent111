package com.example.bigevent.mapper;

import com.example.bigevent.domain.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 保存消息
     */
    @Insert("INSERT INTO chat_message(sender_id, receiver_id, group_id, content, type, is_read, create_time) " +
            "VALUES(#{senderId}, #{receiverId}, #{groupId}, #{content}, #{type}, #{isRead}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ChatMessage message);

    /**
     * 查询单聊历史消息（分页）
     * 查询双方互相发送的消息
     */
    @Select("SELECT * FROM chat_message " +
            "WHERE type = 0 AND ((sender_id = #{userId} AND receiver_id = #{friendId}) OR (sender_id = #{friendId} AND receiver_id = #{userId})) " +
            "ORDER BY create_time DESC")
    List<ChatMessage> findPrivateMessages(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 查询群聊历史消息
     */
    @Select("SELECT * FROM chat_message WHERE type = 1 AND group_id = #{groupId} ORDER BY create_time DESC")
    List<ChatMessage> findGroupMessages(Integer groupId);

    /**
     * 标记单聊消息为已读
     * 将 friendId 发给 userId 的未读消息标记为已读
     */
    @Update("UPDATE chat_message SET is_read = 1 " +
            "WHERE type = 0 AND sender_id = #{friendId} AND receiver_id = #{userId} AND is_read = 0")
    void markAsRead(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 获取某用户发来的未读消息数量
     */
    @Select("SELECT COUNT(*) FROM chat_message " +
            "WHERE type = 0 AND sender_id = #{friendId} AND receiver_id = #{userId} AND is_read = 0")
    long countUnread(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 获取总未读消息数
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE type = 0 AND receiver_id = #{userId} AND is_read = 0")
    long countTotalUnread(Integer userId);

    /**
     * 获取未读消息的发送者列表（用于展示对话列表红点）
     */
    @Select("SELECT sender_id, COUNT(*) as cnt FROM chat_message " +
            "WHERE type = 0 AND receiver_id = #{userId} AND is_read = 0 " +
            "GROUP BY sender_id")
    List<ChatMessage> findUnreadSenders(Integer userId);
}
