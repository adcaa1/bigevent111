package com.example.bigevent.mapper;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatGroupMember;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 群聊 Mapper
 */
@Mapper
public interface ChatGroupMapper {

    /**
     * 创建群聊
     */
    @Insert("INSERT INTO chat_group(name, creator_id, avatar, create_time, update_time) " +
            "VALUES(#{name}, #{creatorId}, #{avatar}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertGroup(ChatGroup group);

    /**
     * 根据ID查询群聊
     */
    @Select("SELECT * FROM chat_group WHERE id = #{id}")
    ChatGroup findById(Integer id);

    /**
     * 添加群成员
     */
    @Insert("INSERT INTO chat_group_member(group_id, user_id, role, join_time) " +
            "VALUES(#{groupId}, #{userId}, #{role}, NOW())")
    void insertMember(ChatGroupMember member);

    /**
     * 删除群成员
     */
    @Delete("DELETE FROM chat_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    void deleteMember(@Param("groupId") Integer groupId, @Param("userId") Integer userId);

    /**
     * 查询群成员列表
     */
    @Select("SELECT * FROM chat_group_member WHERE group_id = #{groupId}")
    List<ChatGroupMember> findMembersByGroupId(Integer groupId);

    /**
     * 查询用户加入的所有群
     */
    @Select("SELECT g.* FROM chat_group g " +
            "INNER JOIN chat_group_member m ON g.id = m.group_id " +
            "WHERE m.user_id = #{userId} ORDER BY g.update_time DESC")
    List<ChatGroup> findGroupsByUserId(Integer userId);

    /**
     * 查询用户在群中的角色
     */
    @Select("SELECT role FROM chat_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    Integer findUserRole(@Param("groupId") Integer groupId, @Param("userId") Integer userId);

    /**
     * 判断用户是否在群中
     */
    @Select("SELECT COUNT(*) FROM chat_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    int isMember(@Param("groupId") Integer groupId, @Param("userId") Integer userId);

    /**
     * 删除群聊
     */
    @Delete("DELETE FROM chat_group WHERE id = #{id}")
    void deleteGroup(Integer id);

    /**
     * 删除群的所有成员
     */
    @Delete("DELETE FROM chat_group_member WHERE group_id = #{groupId}")
    void deleteAllMembers(Integer groupId);
}
