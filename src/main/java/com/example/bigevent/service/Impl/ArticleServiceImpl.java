package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import com.example.bigevent.mapper.ArticleMapper;
import com.example.bigevent.service.ArticleService;
import com.example.bigevent.util.ThreadLocalUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Override
    public void addarticle(Article article) {
//        添加文章肯定在用户登录时，所以可以获得用户id来插入文章
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        article.setCreateUser(id);
        articleMapper.addarticle(article);

    }

    @Override
    public List<Article> findarticle() {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        List<Article>  article = articleMapper.findarticle(id);
        return article;
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
    public void deletearticle(Integer id) {
        articleMapper.deletearticle(id);
    }

    @Override
    public void updatearticle(Article article) {
        articleMapper.updatearticle(article);
    }


}
