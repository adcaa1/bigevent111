package com.example.bigevent.controller;

import com.example.bigevent.domain.Category;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {
    @Autowired
    private CategoryService categoryService;
//    加文章分类
    @PostMapping("/addCategory")
    public Result addCategory(@RequestBody @Validated(Category.Add.class) Category category){
        categoryService.addCategory(category);
        return Result.success("添加成功");
    }
//    查找文章 分类
    @GetMapping("/findallCategory")
    public Result<List<Category>> findCategory(){
       List<Category>  category = categoryService.findCategory();
        return Result.success(category);
    }
//    更新文章 分类
    @PutMapping("/updateCategory")
    public Result updateCategory(@RequestBody @Validated(Category.Update.class)Category category){
        categoryService.updateCategory(category);
        return Result.success("修改成功");
    }
//    删除文章 分类
    @DeleteMapping("/deleteCategory")
    public Result deleteCategory(@RequestParam Integer id){
        categoryService.deleteCategory(id);
        return Result.success("删除成功");
    }
}
