package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.Department;
import com.example.bigevent.mapper.DepartmentMapper;
import com.example.bigevent.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    public List<Department> findAll() {
        return departmentMapper.findAll();
    }

    @Override
    public Department findById(Integer id) {
        return departmentMapper.findById(id);
    }

    @Override
    public Department create(String name) {
        Department department = new Department();
        department.setName(name);
        departmentMapper.insert(department);
        return department;
    }

    @Override
    public void update(Integer id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        departmentMapper.update(department);
    }

    @Override
    public void deleteById(Integer id) {
        departmentMapper.deleteById(id);
    }
}