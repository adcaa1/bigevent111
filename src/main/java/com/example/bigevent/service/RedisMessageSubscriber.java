package com.example.bigevent.service;

import com.example.bigevent.websocket.WsSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 消息订阅者
 * 接收集群广播消息，推送给本地连接的 WebSocket 客户端
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    @Autowired
    private WsSessionManager wsSessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            RedisPubSubService.RedisChatMessage chatMessage =
                    objectMapper.readValue(body, RedisPubSubService.RedisChatMessage.class);

            // 尝试推送给本地连接的客户端
            if (chatMessage.getTargetUserId() != null) {
                wsSessionManager.sendMessage(chatMessage.getTargetUserId(), chatMessage.getJsonMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
