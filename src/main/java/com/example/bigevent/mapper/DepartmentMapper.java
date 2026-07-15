package com.example.bigevent.mapper;

import com.example.bigevent.domain.Department;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    @Select("select * from department order by id")
    List<Department> findAll();

    @Select("select * from department where id = #{id}")
    Department findById(Integer id);

    @Insert("insert into department(name) values(#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Department department);

    @Update("update department set name = #{name} where id = #{id}")
    void update(Department department);

    @Delete("delete from department where id = #{id}")
    void deleteById(Integer id);
}