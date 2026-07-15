package com.example.bigevent.mapper;

import com.example.bigevent.domain.UserFact;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户关键事实数据访问层。
 */
@Mapper
public interface UserFactMapper {

    /**
     * 查询某用户的全部事实。
     *
     * @param userId 用户 ID
     * @return 事实列表
     */
    @Select("SELECT id, user_id, fact_key, fact_value, update_time FROM user_fact WHERE user_id = #{userId}")
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "factKey", column = "fact_key"),
            @Result(property = "factValue", column = "fact_value"),
            @Result(property = "updateTime", column = "update_time")
    })
    List<UserFact> listByUserId(Integer userId);

    /**
     * 根据用户 ID 和事实键查询单条事实。
     *
     * @param userId  用户 ID
     * @param factKey 事实键
     * @return 事实实体
     */
    @Select("SELECT id, user_id, fact_key, fact_value, update_time FROM user_fact " +
            "WHERE user_id = #{userId} AND fact_key = #{factKey}")
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "factKey", column = "fact_key"),
            @Result(property = "factValue", column = "fact_value"),
            @Result(property = "updateTime", column = "update_time")
    })
    UserFact findByUserIdAndKey(@Param("userId") Integer userId, @Param("factKey") String factKey);

    /**
     * 插入或更新用户事实。
     * <p>
     * 利用 (user_id, fact_key) 唯一索引实现"存在即更新，不存在即插入"。
     *
     * @param fact 事实实体
     * @return 影响行数
     */
    @Insert("INSERT INTO user_fact(user_id, fact_key, fact_value, update_time) " +
            "VALUES(#{userId}, #{factKey}, #{factValue}, NOW()) " +
            "ON DUPLICATE KEY UPDATE fact_value = #{factValue}, update_time = NOW()")
    int saveOrUpdate(UserFact fact);

    /**
     * 删除某用户的全部事实。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM user_fact WHERE user_id = #{userId}")
    int deleteByUserId(Integer userId);
}
