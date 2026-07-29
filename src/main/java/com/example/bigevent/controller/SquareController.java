package com.example.bigevent.controller;

import com.example.bigevent.domain.PageBean;
import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.vo.UserSquareVO;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.FollowService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 广场Controller
 * 展示随机推荐用户，支持关注和私信（需登录）
 */
@RestController
@RequestMapping("/square")
public class SquareController {

    @Autowired
    private FollowService followService;

    @Autowired
    private Usermapper usermapper;

    /**
     * 获取广场推荐用户列表
     * 排除当前登录用户自己和互相关注好友
     * sort=article 时按发布文章数降序（首屏推荐）；否则随机推荐（换一批）
     */
    @GetMapping("/users")
    public Result<PageBean<UserSquareVO>> getSquareUsers(@RequestParam(required = false) Integer limit,
                                                         @RequestParam(required = false) String sort) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<UserSquareVO> users = followService.getSquareUsers(currentUserId, limit, sort);
        Long total = usermapper.countSquareUsers(currentUserId);
        return Result.success(new PageBean<>(total, users));
    }

    /**
     * 获取互相关注好友列表
     * 结构与 /square/users 统一，统一返回 PageBean
     */
    @GetMapping("/friends")
    public Result<PageBean<UserSquareVO>> getMutualFriends(@RequestParam(required = false) Integer limit) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<UserSquareVO> friends = followService.getMutualFriends(currentUserId, limit);
        return Result.success(new PageBean<>((long) friends.size(), friends));
    }
}
