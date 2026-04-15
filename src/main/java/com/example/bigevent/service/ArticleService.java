package com.example.bigevent.service;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;

import java.util.List;

public interface ArticleService {
    void addarticle(Article article);

    List<Article> findarticle();

    PageBean<Article> fenyearticle(Integer pageNum, Integer pageSize, Integer categoryId, String state);

    void deletearticle(Integer id);

    void updatearticle(Article article);
}
