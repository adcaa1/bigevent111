package com.example.bigevent.service;

import com.example.bigevent.domain.Department;
import com.example.bigevent.domain.vo.DepartmentVO;

import java.util.List;

public interface DepartmentService {

    List<Department> findAll();

    List<DepartmentVO> findAllWithMemberCount();

    Department findById(Integer id);

    Department create(String name);

    void update(Integer id, String name);

    void deleteById(Integer id);
}