package com.example.bigevent.controller;

import com.auth0.jwt.algorithms.Algorithm;
import com.example.bigevent.domain.Article;
import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.UserProfileVO;
import com.example.bigevent.service.ArticleService;
import com.example.bigevent.service.FollowService;
import com.example.bigevent.service.Userservice;
import com.example.bigevent.util.BloomFilterUtil;
import com.example.bigevent.util.JwtUtil;
import com.example.bigevent.util.Md5Util;
import com.example.bigevent.util.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class Usercontroller {
    @Autowired
    private Userservice userservice1;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private BloomFilterUtil bloomFilterUtil;
    @Autowired
    private FollowService followService;     // 关注关系Service
    @Autowired
    private ArticleService articleService;   // 文章Service（用户主页用）
    @RequestMapping("/find")
    public Result find(String username) {
        // 布隆过滤器判断：不存在则肯定不存在，直接返回
        if (!bloomFilterUtil.mightContainUsername(username)) {
            return Result.error("查找失败");
        }
        User user = userservice1.findid(username);
        if (user != null) {
            return Result.success();
        }
        return Result.error("查找失败");
    }
//    根据线程获取用户信息，不用再传入属性了
     @RequestMapping("/find1")
    public  Result find1(){
         Map<String, Object> map = ThreadLocalUtil.get();
         String username = (String) map.get("username");
         User user = userservice1.findid( username);
         return Result.success(user);
    }
    //如果插入的数据不符合要求，可以插入一个全局处理器，免得返回的是错误页面，还没写，在课里面
    @PostMapping("/add")
    public Result add(String username, String password) {
        // 布隆过滤器判断用户名是否可能存在
        if (bloomFilterUtil.mightContainUsername(username)) {
            // 可能存在，回查数据库确认
            User existUser = userservice1.findid(username);
            if (existUser != null) {
                return Result.error("用户名已存在");
            }
        }
        // 加密
        password = Md5Util.getMD5String(password);
        int i = userservice1.add(username, password);
        if (i > 0) {
            // 同步到布隆过滤器
            bloomFilterUtil.addUsername(username);
            return Result.success();
        }
        return Result.error("插入失败");
    }
//    登录
    @PostMapping("/login")
    public Result login(String username, String password) {
        // 加密
        password = Md5Util.getMD5String(password);
        User user = userservice1.findid(username);
        if (user != null && user.getPassword().equals(password)) {
            //如果后面的操作需要在登录之后，可以设置一个jwt令牌，在之后的操作中，需要携带这个令牌，
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username",user.getUsername());
            String token = JwtUtil.genToken( map);
//                  返回这个token是为了在postman上使用
            //把token存储到redis中
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            operations.set(token,token,1, TimeUnit.HOURS);// 设置过期时间为1小时
            return Result.success(token);
        }
        return Result.error("登录失败");
    }
//    改nickname和 email
    /**
     * 修改用户信息（昵称、邮箱、简介）
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid User user) {
        userservice1.update(user);
        return Result.success();
    }
@PatchMapping("/updatetouxiang" )
    public Result updatetx(@RequestParam @URL String txurl) {
        userservice1.updatetx(txurl);
        return Result.success();
    }
@PatchMapping("/updatepwd")
    public Result updatepwd(@RequestParam String oldpwd,@RequestParam String newpwd,@RequestParam String repwd,@RequestHeader("Authorization") String token) {
    if(!newpwd.equals(repwd))
        return Result.error("输入的密码不一致11");
//    得到旧密码
    Map<String,Object> claims = ThreadLocalUtil.get();
    String username = (String) claims.get("username");
//    由于线程里面只有id和username，所以得通过名字得到当前的user，再调用getpassword来获取密码来比较
    User user = userservice1.findid(username);
    String oldpassword = user.getPassword();
    String oldpwd1 = Md5Util.getMD5String(oldpwd);
    if(!oldpassword.equals(oldpwd1))
       return Result.error("密码错误");
   String newpwd1 = Md5Util.getMD5String(newpwd);
    userservice1.updatepwd(newpwd1);
    ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
    operations.getOperations().delete(token);
    return Result.success();
   }
/**
 * 获取用户信息
 */
    @GetMapping("/user/{userId}/profile")
    public Result<UserProfileVO> getUserProfile(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = claims != null ? (Integer) claims.get("id") : null;
        UserProfileVO profile = followService.getUserProfile(userId, currentUserId);
        if (profile == null) {
            return Result.error("用户不存在");
        }
        return Result.success(profile);
    }
/**
     * 获取用户文章
     */
    @GetMapping("/user/{userId}/articles")
    public Result<List<Article>> getUserArticles(@PathVariable Integer userId) {
        List<Article> articles = articleService.findPublishedByUserId(userId);
        return Result.success(articles);
    }

}
