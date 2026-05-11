package com.example.bigevent.mapper;

import com.example.bigevent.domain.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 关注关系Mapper
 * 处理关注、取消关注、查询粉丝/关注列表等数据库操作
 */
@Mapper
public interface FollowMapper {

    /**
     * 添加关注关系
     */
    @Insert("INSERT INTO follow(user_id, follow_user_id, create_time) VALUES(#{userId}, #{followUserId}, NOW())")
    void addFollow(@Param("userId") Integer userId, @Param("followUserId") Integer followUserId);

    /**
     * 删除关注关系（取消关注）
     */
    @Delete("DELETE FROM follow WHERE user_id = #{userId} AND follow_user_id = #{followUserId}")
    void deleteFollow(@Param("userId") Integer userId, @Param("followUserId") Integer followUserId);

    /**
     * 判断是否已关注
     * 返回大于0表示已关注
     */
    @Select("SELECT COUNT(*) FROM follow WHERE user_id = #{userId} AND follow_user_id = #{followUserId}")
    int isFollowed(@Param("userId") Integer userId, @Param("followUserId") Integer followUserId);

    /**
     * 统计某用户的粉丝数量
     */
    @Select("SELECT COUNT(*) FROM follow WHERE follow_user_id = #{userId}")
    long countFans(@Param("userId") Integer userId);

    /**
     * 统计某用户关注了多少人
     */
    @Select("SELECT COUNT(*) FROM follow WHERE user_id = #{userId}")
    long countFollowing(@Param("userId") Integer userId);

    /**
     * 查询某用户的粉丝列表
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic " +
            "FROM user u INNER JOIN follow f ON u.id = f.user_id " +
            "WHERE f.follow_user_id = #{userId}")
    List<User> findFans(@Param("userId") Integer userId);

    /**
     * 查询某用户关注的人列表
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic " +
            "FROM user u INNER JOIN follow f ON u.id = f.follow_user_id " +
            "WHERE f.user_id = #{userId}")
    List<User> findFollowing(@Param("userId") Integer userId);
}
