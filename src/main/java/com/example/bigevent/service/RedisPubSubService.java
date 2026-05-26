package com.example.bigevent.service;

import com.example.bigevent.config.RedisPubSubConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 服务
 * 用于跨服务器消息广播
 */
@Service
public class RedisPubSubService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发布消息到广播频道
     */
    public void publish(RedisChatMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.CHAT_BROADCAST_CHANNEL, json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Redis 广播消息对象
     */
    public static class RedisChatMessage {
        private Integer targetUserId;   // 目标用户ID
        private String jsonMessage;     // 要推送的 WebSocket 消息 JSON

        public RedisChatMessage() {}

        public RedisChatMessage(Integer targetUserId, String jsonMessage) {
            this.targetUserId = targetUserId;
            this.jsonMessage = jsonMessage;
        }

        public Integer getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(Integer targetUserId) {
            this.targetUserId = targetUserId;
        }

        public String getJsonMessage() {
            return jsonMessage;
        }

        public void setJsonMessage(String jsonMessage) {
            this.jsonMessage = jsonMessage;
        }
    }
}
