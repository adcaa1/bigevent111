package com.example.bigevent.service.Impl;

import com.example.bigevent.domain.User;
import com.example.bigevent.mapper.Usermapper;
import com.example.bigevent.service.UserFactService;
import com.example.bigevent.service.Userservice;
import com.example.bigevent.util.ThreadLocalUtil;
import com.example.bigevent.websocket.WsSessionManager;
import jakarta.websocket.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserserviceImpl implements Userservice {

    @Autowired
    private Usermapper usermapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private WsSessionManager wsSessionManager;
    @Autowired
    private UserFactService userFactService;

    @Override
    public User findid(String username) {
        return usermapper.findid(username);
    }

    @Override
    public int add(String username, String password) {
        return usermapper.add(username,password);
    }

    /**
     * 更新用户信息，并在昵称发生变化时同步更新长期业务记忆。
     * <p>
     * 这样即使更换会话，AI 仍然能记住用户的昵称。
     *
     * @param user 待更新的用户实体
     */
    @Override
    public void update(User user) {
        usermapper.update(user);
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            userFactService.saveOrUpdateFact(user.getId(), "nickname", user.getNickname());
        }
    }

    @Override
    public void updatetx(String txurl) {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        usermapper.updatetx(txurl,id);
    }

    @Override
    public void updatepwd(String newpwd) {
        Map<String,Object> claims = ThreadLocalUtil.get();
        Integer id = (Integer) claims.get("id");
        usermapper.updatepwd(newpwd, id);
    }

    @Override
    public void deleteAccount(Integer userId, String token) {
        // 1. 查当前用户信息（获取原用户名）
        User user = usermapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            throw new RuntimeException("账号已注销");
        }

        // 2. 软删除 + 释放用户名：deleted_{userId}_{原用户名}
        String newUsername = "deleted_" + userId + "_" + user.getUsername();
        usermapper.softDeleteAndRename(userId, newUsername);

        // 3. 删除 Redis token，强制下线
        stringRedisTemplate.delete(token);

        // 4. 断开 WebSocket 连接
        Session session = wsSessionManager.getSession(userId);
        if (session != null && session.isOpen()) {
            wsSessionManager.sendMessage(userId, "{\"type\":\"logout\",\"msg\":\"账号已注销\"}");
            wsSessionManager.removeSession(session);
            try {
                session.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 5. 删除 websocket:online 中的在线标记
        stringRedisTemplate.opsForHash().delete("websocket:online", String.valueOf(userId));
    }
}
