package com.example.bigevent.service;

import com.example.bigevent.config.RedisPubSubConfig;
import com.example.bigevent.domain.dto.chat.RedisChatMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 服务
 * 用于跨服务器消息广播
 */
@Slf4j
@Service
public class RedisPubSubService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发布消息到广播频道
     */
    public void publish(RedisChatMessageDTO message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.CHAT_BROADCAST_CHANNEL, json);
        } catch (Exception e) {
            log.error("[Redis Pub/Sub] 消息发布失败", e);
        }
    }
}
