package com.example.bigevent.controller;

import com.example.bigevent.domain.Result;
import com.example.bigevent.websocket.WsSessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 在线状态查询接口
 * 通过 Redis 查询全集群的在线状态
 */
@RestController
public class OnlineStatusController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private WsSessionManager wsSessionManager;

    /**
     * 获取全集群在线人数
     */
    @GetMapping("/online/count")
    public Result<Long> getOnlineCount() {
        Set<String> keys = stringRedisTemplate.keys("user:online:*");
        return Result.success(keys != null ? (long) keys.size() : 0L);
    }

    /**
     * 判断指定用户是否在线（跨服务器准确）
     */
    @GetMapping("/user/{userId}/online")
    public Result<Boolean> isUserOnline(@PathVariable Integer userId) {
        Boolean online = stringRedisTemplate.hasKey("user:online:" + userId);
        return Result.success(online);
    }
}