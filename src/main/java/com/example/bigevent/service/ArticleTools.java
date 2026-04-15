package com.example.bigevent.service;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ArticleTools {

    @Autowired
    private ArticleService articleService;

    /**
     * 添加文章
     */
    @Tool("添加一篇新文章。需要提供标题、内容、封面图片URL、分类ID和状态(已发布/草稿)")
    public String addArticle(String title, String content, String coverImg, Integer categoryId, String state) {
        try {
            Article article = new Article();
            article.setTitle(title);
            article.setContent(content);
            article.setCoverImg(coverImg);
            article.setCategoryId(categoryId);
            article.setState(state != null ? state : "已发布");
            
            articleService.addarticle(article);
            return "文章添加成功！标题: " + title;
        } catch (Exception e) {
            return "文章添加失败: " + e.getMessage();
        }
    }

    /**
     * 查询所有文章
     */
    @Tool("查询所有文章的列表")
    public String listAllArticles() {
        try {
            List<Article> articles = articleService.findarticle();
            if (articles == null || articles.isEmpty()) {
                return "没有找到任何文章。";
            }
            
            StringBuilder result = new StringBuilder("找到 " + articles.size() + " 篇文章:\n\n");
            for (int i = 0; i < articles.size(); i++) {
                Article article = articles.get(i);
                result.append((i + 1)).append(". ID: ").append(article.getId())
                      .append(", 标题: ").append(article.getTitle())
                      .append(", 状态: ").append(article.getState())
                      .append(", 分类ID: ").append(article.getCategoryId())
                      .append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            return "查询文章失败: " + e.getMessage();
        }
    }

    /**
     * 分页查询文章
     */
    @Tool("分页查询文章，支持按分类ID和状态筛选。参数：页码(pageNum)、每页大小(pageSize)、分类ID(categoryId，可选)、状态(state，可选)")
    public String queryArticles(Integer pageNum, Integer pageSize, Integer categoryId, String state) {
        try {
            if (pageNum == null || pageNum < 1) pageNum = 1;
            if (pageSize == null || pageSize < 1) pageSize = 10;
            
            PageBean<Article> pageBean = articleService.fenyearticle(pageNum, pageSize, categoryId, state);
            
            StringBuilder result = new StringBuilder();
            result.append("第 ").append(pageNum).append(" 页，共 ").append(pageBean.getTotal())
                  .append(" 篇文章，每页 ").append(pageSize).append(" 条:\n\n");
            
            List<Article> articles = pageBean.getItems();
            if (articles == null || articles.isEmpty()) {
                result.append("没有找到符合条件的文章。");
            } else {
                for (int i = 0; i < articles.size(); i++) {
                    Article article = articles.get(i);
                    result.append((i + 1)).append(". ID: ").append(article.getId())
                          .append(", 标题: ").append(article.getTitle())
                          .append(", 状态: ").append(article.getState())
                          .append(", 分类ID: ").append(article.getCategoryId())
                          .append("\n");
                }
            }
            return result.toString();
        } catch (Exception e) {
            return "查询文章失败: " + e.getMessage();
        }
    }

    /**
     * 删除文章
     */
    @Tool("根据文章ID删除文章")
    public String deleteArticle(Integer articleId) {
        try {
            if (articleId == null) {
                return "请提供要删除的文章ID。";
            }
            articleService.deletearticle(articleId);
            return "文章(ID: " + articleId + ")已成功删除。";
        } catch (Exception e) {
            return "删除文章失败: " + e.getMessage();
        }
    }

    /**
     * 更新文章
     */
    @Tool("更新文章信息。需要提供文章ID，以及要更新的字段(标题、内容、封面图片URL、分类ID、状态)")
    public String updateArticle(Integer articleId, String title, String content, String coverImg, Integer categoryId, String state) {
        try {
            if (articleId == null) {
                return "请提供要更新的文章ID。";
            }
            
            // 先查询现有文章
            List<Article> articles = articleService.findarticle();
            Article existingArticle = articles.stream()
                    .filter(a -> a.getId().equals(articleId))
                    .findFirst()
                    .orElse(null);
            
            if (existingArticle == null) {
                return "未找到ID为 " + articleId + " 的文章。";
            }
            
            // 更新字段
            if (title != null && !title.isEmpty()) {
                existingArticle.setTitle(title);
            }
            if (content != null && !content.isEmpty()) {
                existingArticle.setContent(content);
            }
            if (coverImg != null && !coverImg.isEmpty()) {
                existingArticle.setCoverImg(coverImg);
            }
            if (categoryId != null) {
                existingArticle.setCategoryId(categoryId);
            }
            if (state != null && !state.isEmpty()) {
                existingArticle.setState(state);
            }
            
            articleService.updatearticle(existingArticle);
            return "文章(ID: " + articleId + ")已成功更新。";
        } catch (Exception e) {
            return "更新文章失败: " + e.getMessage();
        }
    }

    /**
     * 根据ID查询文章详情
     */
    @Tool("根据文章ID查询文章的详细信息")
    public String getArticleById(Integer articleId) {
        try {
            if (articleId == null) {
                return "请提供要查询的文章ID。";
            }
            
            List<Article> articles = articleService.findarticle();
            Article article = articles.stream()
                    .filter(a -> a.getId().equals(articleId))
                    .findFirst()
                    .orElse(null);
            
            if (article == null) {
                return "未找到ID为 " + articleId + " 的文章。";
            }
            
            StringBuilder result = new StringBuilder("文章详情:\n");
            result.append("ID: ").append(article.getId()).append("\n");
            result.append("标题: ").append(article.getTitle()).append("\n");
            result.append("内容: ").append(article.getContent()).append("\n");
            result.append("封面图片: ").append(article.getCoverImg()).append("\n");
            result.append("状态: ").append(article.getState()).append("\n");
            result.append("分类ID: ").append(article.getCategoryId()).append("\n");
            result.append("创建时间: ").append(article.getCreateTime()).append("\n");
            result.append("更新时间: ").append(article.getUpdateTime()).append("\n");
            
            return result.toString();
        } catch (Exception e) {
            return "查询文章详情失败: " + e.getMessage();
        }
    }
}
