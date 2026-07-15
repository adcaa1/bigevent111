package com.example.bigevent.mapper;

import com.example.bigevent.domain.AiConversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * AI 会话数据访问层。
 */
@Mapper
public interface AiConversationMapper {

    /**
     * 插入一条会话记录，并回填自增 ID。
     *
     * @param conversation 会话实体
     * @return 影响行数
     */
    @Insert("INSERT INTO ai_conversation(user_id, title, create_time, update_time) " +
            "VALUES(#{userId}, #{title}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiConversation conversation);

    /**
     * 查询某用户的全部会话，按 update_time 倒序排列。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @Select("SELECT id, user_id, title, create_time, update_time FROM ai_conversation " +
            "WHERE user_id = #{userId} ORDER BY update_time DESC")
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    List<AiConversation> listByUserId(Integer userId);

    /**
     * 根据 ID 查询会话。
     *
     * @param id 会话 ID
     * @return 会话实体
     */
    @Select("SELECT id, user_id, title, create_time, update_time FROM ai_conversation WHERE id = #{id}")
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    AiConversation findById(Long id);

    /**
     * 更新会话标题。
     *
     * @param id    会话 ID
     * @param title 新标题
     * @return 影响行数
     */
    @Update("UPDATE ai_conversation SET title = #{title}, update_time = NOW() WHERE id = #{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /**
     * 根据 ID 删除会话。
     *
     * @param id 会话 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM ai_conversation WHERE id = #{id}")
    int deleteById(Long id);
}
