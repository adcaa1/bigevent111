package com.example.bigevent.mapper;

import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.vo.ConversationVO;
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
    @Insert("INSERT INTO chat_message(sender_id, receiver_id, group_id, content, type, is_read, temp_id, create_time) " +
            "VALUES(#{senderId}, #{receiverId}, #{groupId}, #{content}, #{type}, #{isRead}, #{tempId}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ChatMessage message);

    /**
     * 查询单聊历史消息（全量，兼容旧调用）
     */
    @Select("SELECT * FROM chat_message " +
            "WHERE type = 0 AND ((sender_id = #{userId} AND receiver_id = #{friendId}) OR (sender_id = #{friendId} AND receiver_id = #{userId})) " +
            "ORDER BY create_time DESC")
    List<ChatMessage> findPrivateMessages(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 查询单聊历史消息（分页）
     */
    @Select("SELECT * FROM chat_message " +
            "WHERE type = 0 AND ((sender_id = #{userId} AND receiver_id = #{friendId}) OR (sender_id = #{friendId} AND receiver_id = #{userId})) " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ChatMessage> findPrivateMessagesPage(@Param("userId") Integer userId, @Param("friendId") Integer friendId,
                                              @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计单聊消息总数
     */
    @Select("SELECT COUNT(*) FROM chat_message " +
            "WHERE type = 0 AND ((sender_id = #{userId} AND receiver_id = #{friendId}) OR (sender_id = #{friendId} AND receiver_id = #{userId}))")
    long countPrivateMessages(@Param("userId") Integer userId, @Param("friendId") Integer friendId);

    /**
     * 查询群聊历史消息（全量，兼容旧调用）
     */
    @Select("SELECT * FROM chat_message WHERE type = 1 AND group_id = #{groupId} ORDER BY create_time DESC")
    List<ChatMessage> findGroupMessages(Integer groupId);

    /**
     * 查询群聊历史消息（分页）
     */
    @Select("SELECT * FROM chat_message WHERE type = 1 AND group_id = #{groupId} " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ChatMessage> findGroupMessagesPage(@Param("groupId") Integer groupId,
                                            @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计群聊消息总数
     */
    @Select("SELECT COUNT(*) FROM chat_message WHERE type = 1 AND group_id = #{groupId}")
    long countGroupMessages(Integer groupId);

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

    /**
     * 获取会话列表
     * 返回当前用户与每个对话对方的最新消息、对方信息及未读数
     */
    @Results({
            @Result(column = "userId", property = "userId"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "userPic", property = "userPic"),
            @Result(column = "lastMessage", property = "lastMessage"),
            @Result(column = "lastTime", property = "lastTime"),
            @Result(column = "unreadCount", property = "unreadCount")
    })
    @Select("SELECT " +
            "  t.other_user_id AS userId, u.nickname, u.user_pic AS userPic, " +
            "  m.content AS lastMessage, m.create_time AS lastTime, " +
            "  COALESCE(un.cnt, 0) AS unreadCount " +
            "FROM ( " +
            "  SELECT " +
            "    CASE " +
            "      WHEN sender_id = #{userId} THEN receiver_id " +
            "      ELSE sender_id " +
            "    END AS other_user_id, " +
            "    MAX(id) AS last_msg_id " +
            "  FROM chat_message " +
            "  WHERE type = 0 AND (sender_id = #{userId} OR receiver_id = #{userId}) " +
            "  GROUP BY other_user_id " +
            ") t " +
            "JOIN chat_message m ON m.id = t.last_msg_id " +
            "JOIN user u ON u.id = t.other_user_id AND (u.deleted IS NULL OR u.deleted = 0) " +
            "LEFT JOIN ( " +
            "  SELECT sender_id, COUNT(*) AS cnt " +
            "  FROM chat_message " +
            "  WHERE type = 0 AND receiver_id = #{userId} AND is_read = 0 " +
            "  GROUP BY sender_id " +
            ") un ON un.sender_id = t.other_user_id " +
            "ORDER BY m.create_time DESC")
    List<ConversationVO> findConversations(@Param("userId") Integer userId);

    /**
     * 根据发送者和 tempId 查询消息（用于重复消息时重新推送）
     */
    @Select("SELECT * FROM chat_message WHERE sender_id = #{senderId} AND temp_id = #{tempId} LIMIT 1")
    ChatMessage findBySenderAndTempId(@Param("senderId") Integer senderId, @Param("tempId") String tempId);
}
