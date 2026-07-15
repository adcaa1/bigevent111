package com.example.bigevent.controller;

import com.example.bigevent.domain.Department;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    /**
     * 获取所有部门列表
     */
    @GetMapping
    public Result<List<Department>> list() {
        return Result.success(departmentService.findAll());
    }

    /**
     * 新增部门
     */
    @PostMapping
    public Result<Department> create(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            return Result.error("部门名称不能为空");
        }
        return Result.success(departmentService.create(name));
    }

    /**
     * 修改部门名称
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Integer id, @RequestParam String name) {
        if (name == null || name.isBlank()) {
            return Result.error("部门名称不能为空");
        }
        departmentService.update(id, name);
        return Result.success("修改成功");
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Integer id) {
        departmentService.deleteById(id);
        return Result.success("删除成功");
    }
}