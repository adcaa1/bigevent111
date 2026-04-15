package com.example.bigevent.mapper;

import com.example.bigevent.domain.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;
@Mapper
public interface CategoryMapper {
    @Select("select * from category where create_user=#{userId}")
    public List<Category> findall(Integer userId);
    @Insert("insert into category(category_name,category_alias,create_user,create_time,update_time) " +
            "values(#{categoryName},#{categoryAlias},#{createUser},now(),now())")
     public void addCategory(Category category);
    @Update("update category set category_name=#{categoryName},category_alias=#{categoryAlias},update_time=now() where id=#{id}")
    void updateCategory(Category category);
   @Delete("delete from category where id=#{id}")
    void deleteCategory(Integer id);
}
