package com.example.bigevent.mapper;

import com.example.bigevent.domain.ChatGroup;
import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.vo.ChatGroupVO;
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
     * 更新群聊信息
     */
    @Update("UPDATE chat_group SET name = #{name}, avatar = #{avatar}, update_time = NOW() WHERE id = #{id}")
    void updateGroup(ChatGroup group);

    /**
     * 统计群成员数量
     */
    @Select("SELECT COUNT(*) FROM chat_group_member WHERE group_id = #{groupId}")
    int countMembers(Integer groupId);

    /**
     * 快速判断用户是否在群中（SELECT 1 性能优于 COUNT(*)）
     */
    @Select("SELECT 1 FROM chat_group_member WHERE group_id = #{groupId} AND user_id = #{userId} LIMIT 1")
    Integer isMemberFast(@Param("groupId") Integer groupId, @Param("userId") Integer userId);

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
     * 更新群成员角色
     */
    @Update("UPDATE chat_group_member SET role = #{role} WHERE group_id = #{groupId} AND user_id = #{userId}")
    void updateMemberRole(@Param("groupId") Integer groupId,
                          @Param("userId") Integer userId,
                          @Param("role") Integer role);

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
     * 查询用户加入的所有群，并附带成员数量（一次查询，避免 N+1）
     */
    @Select("SELECT g.*, COUNT(m.id) as member_count FROM chat_group g " +
            "INNER JOIN chat_group_member m ON g.id = m.group_id " +
            "WHERE g.id IN (SELECT group_id FROM chat_group_member WHERE user_id = #{userId}) " +
            "GROUP BY g.id ORDER BY g.update_time DESC")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "name", property = "name"),
            @Result(column = "creator_id", property = "creatorId"),
            @Result(column = "avatar", property = "avatar"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "member_count", property = "memberCount")
    })
    List<ChatGroupVO> findGroupVOsByUserId(Integer userId);

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
