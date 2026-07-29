package com.example.bigevent.mapper;

import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.UserSquareVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface Usermapper {
    @Select("select * from user where username=#{username} ")
    public User findid(String username);

    /**
     * 查询所有用户（广场用）
     */
    @Select("select * from user")
    List<User> findAll();

    /**
     * 根据ID查询用户
     */
    @Select("select * from user where id = #{id}")
    User findById(Integer id);
    @Insert("insert into user(username,password,department_id,create_time,update_time)" +
            "values (#{username},#{password},#{departmentId},now(),now())")
    public int add(String username, String password, Integer departmentId);
     @Update("update user set nickname=#{nickname},email=#{email},intro=#{intro},fans_visible=#{fansVisible},following_visible=#{followingVisible},department_id=#{departmentId},update_time=now() where id=#{id}")
    void update(User user);
    @Update("update user set user_pic=#{txurl},update_time=now() where id=#{id}")
    void updatetx(String txurl,Integer id);
   @Update("update user set password=#{newpwd},update_time=now() where id=#{id}")
    void updatepwd(String newpwd,Integer id);
    /**
     * 统计指定部门下的用户数
     */
    @Select("select count(*) from user where department_id = #{departmentId} and deleted = 0")
    Long countByDepartmentId(Integer departmentId);

    /**
     * 查询所有用户名（布隆过滤器预热用）
     */
    @Select("select username from user")
    List<String> findAllUsernames();

    /**
     * 软删除 + 释放用户名
     * 把用户名改为 deleted_{id}_{原用户名}，这样原用户名就可以被其他人注册
     */
    @Update("update user set username=#{newUsername}, nickname='已注销用户', deleted=1, update_time=now() where id=#{id}")
    void softDeleteAndRename(@Param("id") Integer id, @Param("newUsername") String newUsername);

    /**
     * 随机推荐广场用户（排除自己 + 互相关注好友），直接读取冗余计数字段
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic, " +
            "u.fans_count AS fansCount, " +
            "u.follow_count AS followCount, " +
            "u.article_count AS articleCount " +
            "FROM user u " +
            "WHERE u.deleted = 0 " +
            "  AND u.id != #{currentUserId} " +
            "  AND u.id NOT IN (" +
            "      SELECT f1.follow_user_id " +
            "      FROM follow f1 " +
            "      JOIN follow f2 ON f1.user_id = f2.follow_user_id AND f1.follow_user_id = f2.user_id " +
            "      WHERE f1.user_id = #{currentUserId}" +
            "  ) " +
            "ORDER BY u.id " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<UserSquareVO> findRandomSquareUsers(@Param("currentUserId") Integer currentUserId, @Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 按已发布文章数量降序推荐广场用户（进入广场首屏用）
     */
    @Select("SELECT u.id, u.username, u.nickname, u.user_pic, " +
            "u.fans_count AS fansCount, " +
            "u.follow_count AS followCount, " +
            "u.article_count AS articleCount " +
            "FROM user u " +
            "WHERE u.deleted = 0 " +
            "  AND u.id != #{currentUserId} " +
            "  AND u.id NOT IN (" +
            "      SELECT f1.follow_user_id " +
            "      FROM follow f1 " +
            "      JOIN follow f2 ON f1.user_id = f2.follow_user_id AND f1.follow_user_id = f2.user_id " +
            "      WHERE f1.user_id = #{currentUserId}" +
            "  ) " +
            "ORDER BY u.article_count DESC, u.id ASC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<UserSquareVO> findSquareUsersByArticle(@Param("currentUserId") Integer currentUserId, @Param("limit") Integer limit, @Param("offset") Integer offset);

    /**
     * 统计广场可推荐用户总数（排除自己 + 互相关注好友）
     */
    @Select("SELECT COUNT(*) FROM user u " +
            "WHERE u.deleted = 0 " +
            "  AND u.id != #{currentUserId} " +
            "  AND u.id NOT IN (" +
            "      SELECT f1.follow_user_id " +
            "      FROM follow f1 " +
            "      JOIN follow f2 ON f1.user_id = f2.follow_user_id AND f1.follow_user_id = f2.user_id " +
            "      WHERE f1.user_id = #{currentUserId}" +
            "  )")
    Long countSquareUsers(@Param("currentUserId") Integer currentUserId);

    /**
     * 原子递增粉丝数
     */
    @Update("UPDATE user SET fans_count = fans_count + #{delta} WHERE id = #{userId}")
    int deltaFansCount(@Param("userId") Integer userId, @Param("delta") int delta);

    /**
     * 原子递增关注数
     */
    @Update("UPDATE user SET follow_count = follow_count + #{delta} WHERE id = #{userId}")
    int deltaFollowCount(@Param("userId") Integer userId, @Param("delta") int delta);

    /**
     * 原子递增已发布文章数
     */
    @Update("UPDATE user SET article_count = article_count + #{delta} WHERE id = #{userId}")
    int deltaArticleCount(@Param("userId") Integer userId, @Param("delta") int delta);

    /**
     * 全量校准所有用户的 fans_count / follow_count / article_count
     */
    @Update("UPDATE user u " +
            "LEFT JOIN (SELECT follow_user_id, COUNT(*) AS c FROM follow GROUP BY follow_user_id) f ON f.follow_user_id = u.id " +
            "LEFT JOIN (SELECT user_id, COUNT(*) AS c FROM follow GROUP BY user_id) fo ON fo.user_id = u.id " +
            "LEFT JOIN (SELECT create_user, COUNT(*) AS c FROM article WHERE state = '已发布' GROUP BY create_user) a ON a.create_user = u.id " +
            "SET u.fans_count = COALESCE(f.c, 0), " +
            "    u.follow_count = COALESCE(fo.c, 0), " +
            "    u.article_count = COALESCE(a.c, 0)")
    int syncAllUserCounts();
}
