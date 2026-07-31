package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.Category;
import com.example.bigevent.mapper.CategoryMapper;
import com.example.bigevent.service.CategoryService;
import com.example.bigevent.util.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public void addCategory(Category category) {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        category.setCreateUser(userId);
        categoryMapper.addCategory(category);
    }

    @Override
    public List<Category> findCategory() {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        List<Category> categories = categoryMapper.findall(userId);
        log.debug("查询用户分类列表: userId={}, size={}", userId, categories == null ? 0 : categories.size());
        return categories;
    }

    @Override
    public void updateCategory(Category category) {
        categoryMapper.updateCategory(category);
    }

    @Override
    public void deleteCategory(Integer id) {
        categoryMapper.deleteCategory(id);
    }


}
