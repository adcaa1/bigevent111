package com.example.bigevent.mapper;

import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.UserSquareVO;
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
     * 统计某用户的粉丝数量（不含已注销用户）
     */
    @Select("SELECT COUNT(*) FROM follow f " +
            "INNER JOIN user u ON f.user_id = u.id " +
            "WHERE f.follow_user_id = #{userId} AND u.deleted = 0")
    long countFans(@Param("userId") Integer userId);

    /**
     * 统计某用户关注了多少人（不含已注销用户）
     */
    @Select("SELECT COUNT(*) FROM follow f " +
            "INNER JOIN user u ON f.follow_user_id = u.id " +
            "WHERE f.user_id = #{userId} AND u.deleted = 0")
    long countFollowing(@Param("userId") Integer userId);

    /**
     * 查询某用户的粉丝列表（不含已注销用户）
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic " +
            "FROM user u INNER JOIN follow f ON u.id = f.user_id " +
            "WHERE f.follow_user_id = #{userId} AND u.deleted = 0")
    List<User> findFans(@Param("userId") Integer userId);

    /**
     * 查询某用户关注的人列表（不含已注销用户）
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic " +
            "FROM user u INNER JOIN follow f ON u.id = f.follow_user_id " +
            "WHERE f.user_id = #{userId} AND u.deleted = 0")
    List<User> findFollowing(@Param("userId") Integer userId);

    /**
     * 查询与指定用户互相关注的好友列表（含粉丝数/关注数）
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic, " +
            "COALESCE(fc.fans_count, 0) AS fansCount, " +
            "COALESCE(foc.follow_count, 0) AS followCount " +
            "FROM follow f1 " +
            "INNER JOIN follow f2 ON f1.user_id = f2.follow_user_id AND f1.follow_user_id = f2.user_id " +
            "INNER JOIN user u ON u.id = f1.follow_user_id " +
            "LEFT JOIN (" +
            "    SELECT follow_user_id, COUNT(*) AS fans_count " +
            "    FROM follow f JOIN user u2 ON f.user_id = u2.id AND u2.deleted = 0 " +
            "    GROUP BY follow_user_id" +
            ") fc ON fc.follow_user_id = u.id " +
            "LEFT JOIN (" +
            "    SELECT user_id, COUNT(*) AS follow_count " +
            "    FROM follow f JOIN user u2 ON f.follow_user_id = u2.id AND u2.deleted = 0 " +
            "    GROUP BY user_id" +
            ") foc ON foc.user_id = u.id " +
            "WHERE f1.user_id = #{userId} AND u.deleted = 0 " +
            "LIMIT #{limit}")
    List<UserSquareVO> findMutualFriends(@Param("userId") Integer userId, @Param("limit") Integer limit);

    /**
     * 批量判断当前用户是否关注了目标用户列表
     */
    @Select("<script>" +
            "SELECT follow_user_id FROM follow " +
            "WHERE user_id = #{userId} AND follow_user_id IN " +
            "<foreach collection='targetUserIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Integer> batchIsFollowed(@Param("userId") Integer userId, @Param("targetUserIds") List<Integer> targetUserIds);
}
