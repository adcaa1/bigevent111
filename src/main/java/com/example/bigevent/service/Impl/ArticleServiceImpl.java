package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.ArticleVO;
import com.example.bigevent.mapper.ArticleMapper;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.ArticleService;
import com.example.bigevent.service.UserFactService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.BeanUtils;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private Usermapper usermapper;
    @Autowired
    private UserFactService userFactService;
    /**
     * 添加文章，并在成功后刷新用户的"最近文章"长期业务记忆。
     * <p>
     * 这样 AI 在后续对话中可以引用用户最近添加的文章。
     *
     * @param article 待添加的文章实体
     */
    @Override
    public void addarticle(Article article) {
//        添加文章肯定在用户登录时，所以可以获得用户id来插入文章
        Integer id = article.getCreateUser();
        if (id == null) {
            Map<String,Object> claims = ThreadLocalUtil.get();
            id = claims != null ? (Integer) claims.get("id") : null;
        }
        if (id == null) {
            throw new IllegalArgumentException("无法获取当前用户ID");
        }
        article.setCreateUser(id);
        articleMapper.addarticle(article);
        // 已发布文章同步递增冗余计数
        if ("已发布".equals(article.getState())) {
            usermapper.deltaArticleCount(id, 1);
        }
// 刷新用户的"最近文章"长期业务记忆
        refreshLatestArticles(id);
    }

    /**
     * 刷新用户最近添加的文章列表到长期业务记忆。
     * <p>
     * 取该用户最新的 5 篇文章标题，以逗号分隔存入 user_fact.latest_articles。
     *
     * @param userId 用户 ID
     */
    private void refreshLatestArticles(Integer userId) {
        List<Article> articles = articleMapper.findarticle(userId);
        if (articles == null || articles.isEmpty()) {
            return;
        }
        String latest = articles.stream()
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .limit(5)
                .map(Article::getTitle)
                .collect(Collectors.joining(","));
        userFactService.saveOrUpdateFact(userId, "latest_articles", latest);
    }

    @Override
    public List<Article> findarticle() {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        List<Article>  article = articleMapper.findarticle(id);
        return article;
    }

    @Override
    public List<Article> findarticle(Integer userId) {
        return articleMapper.findarticle(userId);
    }

    @Override
    public PageBean<Article> fenyearticle(Integer pageNum,
                                          Integer pageSize,
                                          Integer categoryId,
                                          String state)
    {

        PageBean<Article> pageBean = new PageBean<>();
        PageHelper.startPage(pageNum,pageSize);
        //因为是查询当前用户的文章，所以要传 userId
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        List<Article>  article = articleMapper.fenyearticle(id,categoryId,state);
        //Page中提供了方法,可以获取PageHelper分页查询后 得到的总记录条数和当前页数据
        Page<Article> page = (Page<Article>) article;

        pageBean.setTotal(page.getTotal());// 总记录条数
        pageBean.setItems(page.getResult());// 当前页数据
        return pageBean;
    }

    @Override
    public PageBean<Article> fenyearticle(Integer userId,
                                          Integer pageNum,
                                          Integer pageSize,
                                          Integer categoryId,
                                          String state)
    {
        PageBean<Article> pageBean = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        List<Article> article = articleMapper.fenyearticle(userId, categoryId, state);
        Page<Article> page = (Page<Article>) article;
        pageBean.setTotal(page.getTotal());
        pageBean.setItems(page.getResult());
        return pageBean;
    }

    @Override
    @CacheEvict(value = "article", key = "#id")
    public void deletearticle(Integer id) {
        Article old = articleMapper.findById(id);
        if (old != null && "已发布".equals(old.getState())) {
            usermapper.deltaArticleCount(old.getCreateUser(), -1);
        }
        articleMapper.deletearticle(id);
    }

    @Override
    @CacheEvict(value = "article", key = "#article.id")
    public void updatearticle(Article article) {
        Article old = articleMapper.findById(article.getId());
        if (old != null) {
            boolean wasPublished = "已发布".equals(old.getState());
            boolean willPublish = "已发布".equals(article.getState());
            if (!wasPublished && willPublish) {
                usermapper.deltaArticleCount(old.getCreateUser(), 1);
            } else if (wasPublished && !willPublish) {
                usermapper.deltaArticleCount(old.getCreateUser(), -1);
            }
        }
        articleMapper.updatearticle(article);
    }

    /**
     * 根据ID查询文章详情
     */
    @Override
    @Cacheable(value = "article", key = "#id", unless = "#result == null")
    public ArticleVO findById(Integer id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            return null;
        }
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        // 查询作者信息
        User user = usermapper.findById(article.getCreateUser());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setUserPic(user.getUserPic());
        }
        return vo;
    }

    /**
     * 查询某用户的已发布文章（用户主页用）
     */
    @Override
    public List<Article> findPublishedByUserId(Integer userId) {
        return articleMapper.findPublishedByUserId(userId);
    }

}
