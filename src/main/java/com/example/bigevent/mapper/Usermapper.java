package com.example.bigevent.mapper;

import com.example.bigevent.domain.User;
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
     @Update("update user set nickname=#{nickname},email=#{email},intro=#{intro},fans_visible=#{fansVisible},following_visible=#{followingVisible},update_time=now() where id=#{id}")
    void update(User user);
    @Update("update user set user_pic=#{txurl},update_time=now() where id=#{id}")
    void updatetx(String txurl,Integer id);
   @Update("update user set password=#{newpwd},update_time=now() where id=#{id}")
    void updatepwd(String newpwd,Integer id);
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
}
