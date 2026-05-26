package com.example.bigevent.config;

import com.example.bigevent.service.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis Pub/Sub 配置
 * 如果你只有一台服务器，这个配置其实用不上
 * （因为 WsSessionManager.sendMessage() 在本机就能推成功，不会走到 Redis 广播）。
 * 但集群部署时它就至关重要了。
 */
@Configuration
public class RedisPubSubConfig {

    public static final String CHAT_BROADCAST_CHANNEL = "chat:broadcast";

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                           MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(CHAT_BROADCAST_CHANNEL));
        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }
}
