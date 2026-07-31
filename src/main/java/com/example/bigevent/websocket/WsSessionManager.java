package com.example.bigevent.websocket;

import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Session 管理器
 * 维护用户ID与Session的映射关系
 */
@Slf4j
@Component
public class WsSessionManager {

    /**
     * userId -> Session 映射
     */
    private static final Map<Integer, Session> USER_SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * sessionId -> userId 映射（用于断线时反向查找）
     */
    private static final Map<String, Integer> SESSION_USER_MAP = new ConcurrentHashMap<>();

    /**
     * 添加会话
     */
    public void addSession(Integer userId, Session session) {
        // 如果该用户已有连接，先关闭旧连接（同端登录踢下线）
        Session oldSession = USER_SESSION_MAP.get(userId);
        if (oldSession != null && oldSession.isOpen() && !oldSession.getId().equals(session.getId())) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.error("[WebSocket] 关闭旧会话失败, userId={}", userId, e);
            }
        }
        USER_SESSION_MAP.put(userId, session);
        SESSION_USER_MAP.put(session.getId(), userId);
    }

    /**
     * 移除会话
     */
    public void removeSession(Session session) {
        Integer userId = SESSION_USER_MAP.remove(session.getId());
        if (userId != null) {
            Session current = USER_SESSION_MAP.get(userId);
            if (current != null && current.getId().equals(session.getId())) {
                USER_SESSION_MAP.remove(userId);
            }
        }
    }

    /**
     * 根据用户ID获取Session
     */
    public Session getSession(Integer userId) {
        return USER_SESSION_MAP.get(userId);
    }

    /**
     * 用户是否在线
     */
    public boolean isOnline(Integer userId) {
        Session session = USER_SESSION_MAP.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 发送消息给指定用户（使用 async remote，避免大群广播时阻塞推送线程）
     */
    public boolean sendMessage(Integer userId, String message) {
        Session session = USER_SESSION_MAP.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getAsyncRemote().sendText(message);
                return true;
            } catch (Exception e) {
                log.error("[WebSocket] 发送消息失败, userId={}", userId, e);
            }
        }
        return false;
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineCount() {
        return USER_SESSION_MAP.size();
    }
}
