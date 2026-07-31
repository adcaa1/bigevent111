package com.example.bigevent.service;

import com.example.bigevent.domain.AiConversation;
import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 文章管理工具集：通过自然语言对话实现文章的增删改查操作。
 * <p>
 * 该类是 Spring 管理的单例 Bean，不持有任何用户状态。工具方法通过
 * {@link ToolMemoryId} 获取会话 ID，再查询 {@link AiConversationService}
 * 得到当前用户 ID，从而避开线程切换导致的 ThreadLocal 失效问题。
 */
@Component
public class ArticleTools {

    private final ArticleService articleService;
    private final AiConversationService aiConversationService;

    @Autowired
    public ArticleTools(ArticleService articleService, AiConversationService aiConversationService) {
        this.articleService = articleService;
        this.aiConversationService = aiConversationService;
    }

    /**
     * 根据会话 ID 获取当前用户 ID。
     * <p>
     * {@link ToolMemoryId} 参数不会被暴露给 LLM，由 LangChain4j 自动注入。
     * 参数类型声明为 Object，避免 LangChain4j 内部传入的类型与声明类型不一致导致反射失败。
     */
    private Integer getUserId(Object memoryId) {
        Long convId = toLong(memoryId);
        if (convId == null) {
            return null;
        }
        AiConversation conversation = aiConversationService.findById(convId);
        return conversation != null ? conversation.getUserId() : null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 添加文章
     */
    @Tool("添加一篇新文章。需要提供标题、内容、封面图片URL、分类ID和状态(已发布/草稿)")
    public String addArticle(@ToolMemoryId Object memoryId,
                             String title, String content, String coverImg,
                             Integer categoryId, String state) {
        try {
            Integer userId = getUserId(memoryId);
            if (userId == null) {
                return "文章添加失败: 无法获取当前用户信息";
            }

            Article article = new Article();
            article.setTitle(title);
            article.setContent(content);
            article.setCoverImg(coverImg);
            article.setCategoryId(categoryId);
            article.setState(state != null ? state : "已发布");
            article.setCreateUser(userId);

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
    public String listAllArticles(@ToolMemoryId Object memoryId) {
        try {
            Integer userId = getUserId(memoryId);
            if (userId == null) {
                return "查询文章失败: 无法获取当前用户信息";
            }

            List<Article> articles = articleService.findarticle(userId);
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
    public String queryArticles(@ToolMemoryId Object memoryId,
                                Integer pageNum, Integer pageSize,
                                @P(value = "分类ID，可选", required = false) Integer categoryId,
                                @P(value = "状态，可选", required = false) String state) {
        try {
            Integer userId = getUserId(memoryId);
            if (userId == null) {
                return "查询文章失败: 无法获取当前用户信息";
            }

            if (pageNum == null || pageNum < 1) pageNum = 1;
            if (pageSize == null || pageSize < 1) pageSize = 10;

            PageBean<Article> pageBean = articleService.fenyearticle(userId, pageNum, pageSize, categoryId, state);

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
    public String deleteArticle(@ToolMemoryId Object memoryId, Integer articleId) {
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
    public String updateArticle(@ToolMemoryId Object memoryId,
                                Integer articleId,
                                @P(value = "标题，可选", required = false) String title,
                                @P(value = "内容，可选", required = false) String content,
                                @P(value = "封面图片URL，可选", required = false) String coverImg,
                                @P(value = "分类ID，可选", required = false) Integer categoryId,
                                @P(value = "状态，可选", required = false) String state) {
        try {
            if (articleId == null) {
                return "请提供要更新的文章ID。";
            }

            Integer userId = getUserId(memoryId);
            if (userId == null) {
                return "更新文章失败: 无法获取当前用户信息";
            }

            List<Article> articles = articleService.findarticle(userId);
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
    public String getArticleById(@ToolMemoryId Object memoryId, Integer articleId) {
        try {
            if (articleId == null) {
                return "请提供要查询的文章ID。";
            }

            Integer userId = getUserId(memoryId);
            if (userId == null) {
                return "查询文章详情失败: 无法获取当前用户信息";
            }

            List<Article> articles = articleService.findarticle(userId);
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
