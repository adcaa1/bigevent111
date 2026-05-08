package com.example.bigevent.mapper;

import com.example.bigevent.domain.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface Usermapper {
    @Select("select * from user where username=#{username} ")
    public User findid(String username);
    @Insert("insert into user(username,password,create_time,update_time)" +
            "values (#{username},#{password},now(),now())")
    public int add(String username,String password);
     @Update("update user set nickname=#{nickname},email=#{email},update_time=now() where id=#{id}")
    void update(User user);
    @Update("update user set user_pic=#{txurl},update_time=now() where id=#{id}")
    void updatetx(String txurl,Integer id);
   @Update("update user set password=#{newpwd},update_time=now() where id=#{id}")
    void updatepwd(String newpwd,Integer id);

    @Select("select username from user")
    List<String> findAllUsernames();
}
