package com.example.bigevent.mapper;

import com.example.bigevent.domain.Article;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ArticleMapper {

    @Insert("insert into article(title,content,cover_img,state,category_id,create_user,create_time,update_time) " +
            "values(#{title},#{content},#{coverImg},#{state},#{categoryId},#{createUser},now(),now())")
    void addarticle(Article article);

    @Select("select * from article where create_user=#{id}")
    List<Article> findarticle(Integer id);
//    注解方式实现动态sql，
    @SelectProvider(type = ArticlePrivoder.class, method = "list")
    List<Article> fenyearticle(Integer id, Integer categoryId, String state);

//    XML方式实现动态sql
//    还没写
    @Delete("delete from article where id=#{id}")
    void deletearticle(Integer id);
    @Update("update article set title=#{title},content=#{content},cover_img=#{coverImg},state=#{state},category_id=#{categoryId},update_time=now() where id=#{id}")
    void updatearticle(Article article);
}
