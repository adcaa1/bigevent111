package com.example.bigevent.controller;

import com.example.bigevent.domain.Department;
import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.DepartmentVO;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.DepartmentService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private Usermapper usermapper;

    /**
     * 校验当前用户是否为管理员
     */
    private Result<?> checkAdmin() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        User user = usermapper.findById(currentUserId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            return Result.error("无权限，仅管理员可操作");
        }
        return null;
    }

    /**
     * 获取所有部门列表（包含人数）
     */
    @GetMapping
    public Result<List<DepartmentVO>> list() {
        return Result.success(departmentService.findAllWithMemberCount());
    }

    /**
     * 新增部门（管理员）
     */
    @PostMapping
    public Result<Department> create(@RequestBody Department department) {
        Result<?> authResult = checkAdmin();
        if (authResult != null) {
            return (Result<Department>) authResult;
        }
        String name = department.getName();
        if (name == null || name.isBlank()) {
            return Result.error("部门名称不能为空");
        }
        return Result.success(departmentService.create(name));
    }

    /**
     * 修改部门名称（管理员）
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Integer id, @RequestBody Department department) {
        Result<?> authResult = checkAdmin();
        if (authResult != null) {
            return (Result<String>) authResult;
        }
        String name = department.getName();
        if (name == null || name.isBlank()) {
            return Result.error("部门名称不能为空");
        }
        departmentService.update(id, name);
        return Result.success("修改成功");
    }

    /**
     * 删除部门（管理员）
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Integer id) {
        Result<?> authResult = checkAdmin();
        if (authResult != null) {
            return (Result<String>) authResult;
        }
        departmentService.deleteById(id);
        return Result.success("删除成功");
    }
}