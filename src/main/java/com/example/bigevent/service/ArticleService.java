package com.example.bigevent.service;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import com.example.bigevent.domain.vo.ArticleVO;

import java.util.List;

public interface ArticleService {
    void addarticle(Article article);

    List<Article> findarticle();

    PageBean<Article> fenyearticle(Integer pageNum, Integer pageSize, Integer categoryId, String state);

    void deletearticle(Integer id);

    void updatearticle(Article article);

    /**
     * 根据ID查询文章详情
     */
    ArticleVO findById(Integer id);

    /**
     * 查询某用户的已发布文章（用户主页用）
     */
    List<Article> findPublishedByUserId(Integer userId);
}
