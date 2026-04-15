package com.example.bigevent.service;

import com.example.bigevent.domain.Category;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

public interface CategoryService {
    void addCategory(Category  category);
    List<Category> findCategory();

    void updateCategory(Category category);

    void deleteCategory(Integer id);
}
