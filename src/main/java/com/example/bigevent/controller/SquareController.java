package com.example.bigevent.controller;

import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.vo.UserSquareVO;
import com.example.bigevent.service.FollowService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 广场Controller
 * 展示所有用户，支持关注和私信（需登录）
 */
@RestController
@RequestMapping("/square")
public class SquareController {

    @Autowired
    private FollowService followService;

    /**
     * 获取广场用户列表
     * 需登录，返回每个人的粉丝数/关注数/当前用户是否已关注
     */
    @GetMapping("/users")
    public Result<List<UserSquareVO>> getSquareUsers() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<UserSquareVO> users = followService.getSquareUsers(currentUserId);
        return Result.success(users);
    }
}
