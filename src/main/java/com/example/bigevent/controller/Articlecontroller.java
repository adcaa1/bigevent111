package com.example.bigevent.controller;

import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.PageBean;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.ArticleService;
import com.example.bigevent.util.JwtUtil;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/article")
public class Articlecontroller {
    @RequestMapping("/allarticle1")
    //        原始验证token（jwt令牌)方法，要传入Authorization
   public Result allarticle(@RequestHeader("Authorization") String token/*这里我没设置错误状态为404，不知道需不需要*/) {
//        Map<String, Object> claims = JwtUtil.parseToken(token);这里要trycatch一下，这样的话，就好梳理
        try {
            Map<String, Object> claims = JwtUtil.parseToken(token);
            return Result.success("所有文章是....");
        } catch (Exception e) {
            return Result.error("请登录");
        }
    }
//    业务多了，可以设置拦截器，就不需要一个一个的加jwt令牌了
//    如果登录后，需要获得用户信息，可以在token里面获取，
//    但是如果每次都这样的话@RequestHeader("Authorization" String token)，
//    太麻烦了，所以可以设置一个
//    线程局变量（在拦截器里面实现线程），保存用户信息，这样，就不需要每次都去获取用户信息了





    @Autowired
    private ArticleService articleservice;
//    加文章
    @PostMapping
    public Result addarticle(@RequestBody Article article) {
        articleservice.addarticle(article);
        return Result.success("添加文章成功");
    }
//    查找所有文章
    @GetMapping
    public Result findarticle() {
        List<Article>  article = articleservice.findarticle();
        return Result.success(article);
    }
//    实现后端的分页查询，要用到pagehepler，这个很难，可以到时候再看看
//    重要
//    重要
//重要
//    重要
//    重要
    @GetMapping("/fenyearticle")
    public Result<PageBean<Article>> fenyearticle(
            Integer pageNum,
            Integer pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String state
    )
        {
            System.out.println("分页请求参数: pageNum=" + pageNum + ", pageSize=" + pageSize + ", categoryId=" + categoryId + ", state=" + state);
        PageBean<Article> pageBean = articleservice.fenyearticle(pageNum,pageSize,categoryId,state);
        return Result.success(pageBean);
    }
//    删除文章
    @DeleteMapping
    public Result deletearticle(@RequestParam Integer id) {
        articleservice.deletearticle(id);
        return Result.success("删除成功");
    }
//    更新文章
@PutMapping
    public Result updatearticle(@RequestBody Article article) {
        articleservice.updatearticle(article);
        return Result.success("更新成功");
    }
}
