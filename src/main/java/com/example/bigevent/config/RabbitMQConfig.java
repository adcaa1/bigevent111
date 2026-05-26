package com.example.bigevent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 * 声明聊天消息所需的交换机、队列和绑定关系
 */
@Configuration
public class RabbitMQConfig {

    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_PRIVATE_QUEUE = "chat.private.queue";
    public static final String CHAT_GROUP_QUEUE = "chat.group.queue";
    public static final String CHAT_PRIVATE_KEY = "chat.private";
    public static final String CHAT_GROUP_KEY = "chat.group";

//    接收生产者发来的消息，根据路由键（routing key）决定把消息扔到哪个队列。
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }

//    暂存消息。消息先放在这里排队，等消费者来取。
    @Bean
    public Queue chatPrivateQueue() {
        return new Queue(CHAT_PRIVATE_QUEUE);
    }
//    暂存消息。消息先放在这里排队，等消费者来取。
    @Bean
    public Queue chatGroupQueue() {
        return new Queue(CHAT_GROUP_QUEUE);
    }

//    绑定规则（分拣表）
//    告诉交换机："如果消息的路由键是 xxx，就把它放到这个队列里"。
    @Bean
    public Binding privateBinding() {
        return BindingBuilder.bind(chatPrivateQueue()).to(chatExchange()).with(CHAT_PRIVATE_KEY);
    }

    @Bean
    public Binding groupBinding() {
        return BindingBuilder.bind(chatGroupQueue()).to(chatExchange()).with(CHAT_GROUP_KEY);
    }

    /**
     * 配置 JSON 消息转换器
     * 解决反序列化安全问题，使用 JSON 格式替代 Java 原生序列化
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
