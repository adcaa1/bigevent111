package com.example.bigevent.controller;

import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.User;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.FollowService;
import com.example.bigevent.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 关注关系Controller
 * 处理关注、取消关注、查询粉丝/关注列表
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private FollowService followService;

    @Autowired
    private Usermapper usermapper;

    /**
     * 关注用户
     */
    @PostMapping("/{userId}")
    public Result follow(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        followService.follow(currentUserId, userId);
        return Result.success();
    }

    /**
     * 取消关注
     */
    @DeleteMapping("/{userId}")
    public Result unfollow(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        followService.unfollow(currentUserId, userId);
        return Result.success();
    }

    /**
     * 查询我的粉丝列表
     */
    @GetMapping("/fans")
    public Result<List<User>> getFans() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<User> fans = followService.findFans(currentUserId);
        return Result.success(fans);
    }

    /**
     * 查询我关注的人列表
     */
    @GetMapping("/following")
    public Result<List<User>> getFollowing() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        List<User> following = followService.findFollowing(currentUserId);
        return Result.success(following);
    }

    /**
     * 查询是否已关注某用户
     */
    @GetMapping("/status/{userId}")
    public Result<Boolean> isFollowed(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");
        boolean followed = followService.isFollowed(currentUserId, userId);
        return Result.success(followed);
    }

    /**
     * 查询某用户的粉丝列表（带隐私权限判断）
     */
    @GetMapping("/fans/{userId}")
    public Result<List<User>> getUserFans(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        // 查该用户的隐私设置
        User targetUser = usermapper.findById(userId);
        if (targetUser == null) {
            return Result.error("用户不存在");
        }

        // 粉丝列表设为私密，且不是本人查看
        if (targetUser.getFansVisible() != null && targetUser.getFansVisible() == 0) {
            if (!userId.equals(currentUserId)) {
                return Result.error("该用户已设置粉丝列表私密");
            }
        }

        List<User> fans = followService.findFans(userId);
        return Result.success(fans);
    }

    /**
     * 查询某用户的关注列表（带隐私权限判断）
     */
    @GetMapping("/following/{userId}")
    public Result<List<User>> getUserFollowing(@PathVariable Integer userId) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) claims.get("id");

        // 查该用户的隐私设置
        User targetUser = usermapper.findById(userId);
        if (targetUser == null) {
            return Result.error("用户不存在");
        }

        // 关注列表设为私密，且不是本人查看
        if (targetUser.getFollowingVisible() != null && targetUser.getFollowingVisible() == 0) {
            if (!userId.equals(currentUserId)) {
                return Result.error("该用户已设置关注列表私密");
            }
        }

        List<User> following = followService.findFollowing(userId);
        return Result.success(following);
    }
}
