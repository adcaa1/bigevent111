package com.example.bigevent.service;

import com.example.bigevent.config.RabbitMQConfig;
import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.ChatMessageEvent;
import com.example.bigevent.domain.vo.WsMessageVO;
import com.example.bigevent.domain.dto.chat.RedisChatMessageDTO;
import com.example.bigevent.mapper.ChatGroupMapper;
import com.example.bigevent.websocket.ChatWebSocketServer;
import com.example.bigevent.websocket.WsSessionManager;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * RabbitMQ 聊天消息消费者
 * 异步处理：存数据库 + 推送给接收者
 */
@Component
public class ChatMessageConsumer {

    @Autowired
    private ChatService chatService;

    @Autowired
    private WsSessionManager wsSessionManager;

    @Autowired
    private RedisPubSubService redisPubSubService;

    @Autowired
    private ChatGroupMapper chatGroupMapper;

    // 通用线程池，用于群消息并行推送
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * 消费单聊消息
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_PRIVATE_QUEUE)
    public void handlePrivateMessage(ChatMessageEvent event) {
        System.out.println("[RabbitMQ Consumer] 收到单聊消息: " + event);
        String json = null;
        try {
            // 1. 存数据库（temp_id 唯一索引保证幂等，重复消息不会重复插入）
            System.out.println("[RabbitMQ Consumer] 开始保存单聊消息到数据库...");
            ChatMessage saved = chatService.savePrivateMessage(
                    event.getSenderId(),
                    event.getReceiverId(),
                    event.getContent(),
                    event.getTempId()
            );
            System.out.println("[RabbitMQ Consumer] 单聊消息已保存到数据库, messageId=" + saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            json = ChatWebSocketServer.toJson(vo);

            // 3. 推送给接收者
            boolean pushed = wsSessionManager.sendMessage(event.getReceiverId(), json);
            if (!pushed) {
                // 不在本机，Redis 广播给集群其他机器
                System.out.println("[RabbitMQ Consumer] 接收者不在本机，通过Redis广播");
                redisPubSubService.publish(
                        new RedisChatMessageDTO(event.getReceiverId(), json));
            } else {
                System.out.println("[RabbitMQ Consumer] 消息已推送给接收者: " + event.getReceiverId());
            }

            // 4. 回推给发送者
            wsSessionManager.sendMessage(event.getSenderId(), json);
            System.out.println("[RabbitMQ Consumer] 消息已回推给发送者: " + event.getSenderId());

        } catch (DuplicateKeyException e) {
            // temp_id 唯一索引冲突 → 重复消息（上次已存库但可能没推送完）
            System.out.println("[RabbitMQ Consumer] 检测到重复消息，重新推送: tempId=" + event.getTempId());
            ChatMessage saved = chatService.getMessageBySenderAndTempId(event.getSenderId(), event.getTempId());
            if (saved != null) {
                WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
                json = ChatWebSocketServer.toJson(vo);
                wsSessionManager.sendMessage(event.getReceiverId(), json);
                wsSessionManager.sendMessage(event.getSenderId(), json);
            }
        } catch (Exception e) {
            System.err.println("[RabbitMQ Consumer] 处理单聊消息失败: " + e.getMessage());
            e.printStackTrace();
            // 先回推 ACK，避免发送者一直等待
            if (json == null) {
                wsSessionManager.sendMessage(event.getSenderId(),
                        "{\"type\":\"ack\",\"tempId\":\"" + event.getTempId() + "\"}");
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 消费群聊消息
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_GROUP_QUEUE)
    public void handleGroupMessage(ChatMessageEvent event) {
        System.out.println("[RabbitMQ Consumer] 收到群聊消息: " + event);
        String json = null;
        try {
            // 1. 存数据库
            System.out.println("[RabbitMQ Consumer] 开始保存群聊消息到数据库...");
            ChatMessage saved = chatService.saveGroupMessage(
                    event.getSenderId(),
                    event.getGroupId(),
                    event.getContent(),
                    event.getTempId()
            );
            System.out.println("[RabbitMQ Consumer] 群聊消息已保存到数据库, messageId=" + saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            json = ChatWebSocketServer.toJson(vo);

            // 3. 获取群成员并并行推送（自定义线程池加速，避免大群串行阻塞）
            final String messageJson = json;  // lambda 只能捕获 final 变量
            List<ChatGroupMember> members = chatGroupMapper.findMembersByGroupId(event.getGroupId());
            System.out.println("[RabbitMQ Consumer] 群组成员数量: " + members.size());
            List<CompletableFuture<Void>> pushFutures = members.stream()
                    .filter(member -> !member.getUserId().equals(event.getSenderId()))
                    .map(member -> CompletableFuture.runAsync(() -> {
                        boolean pushed = wsSessionManager.sendMessage(member.getUserId(), messageJson);
                        if (!pushed) {
                            System.out.println("[RabbitMQ Consumer] 群组成员 " + member.getUserId() + " 不在本机，通过Redis广播");
                            redisPubSubService.publish(
                                    new RedisChatMessageDTO(member.getUserId(), messageJson));
                        } else {
                            System.out.println("[RabbitMQ Consumer] 消息已推送给群组成员: " + member.getUserId());
                        }
                    }, taskExecutor))
                    .toList();
            // 等待所有推送完成（不抛异常，单条推送失败不影响整体）
            CompletableFuture.allOf(pushFutures.toArray(new CompletableFuture[0])).join();

            // 4. 回推给发送者
            wsSessionManager.sendMessage(event.getSenderId(), json);
            System.out.println("[RabbitMQ Consumer] 消息已回推给发送者: " + event.getSenderId());

        } catch (DuplicateKeyException e) {
            System.out.println("[RabbitMQ Consumer] 检测到重复群聊消息，重新推送: tempId=" + event.getTempId());
            ChatMessage saved = chatService.getMessageBySenderAndTempId(event.getSenderId(), event.getTempId());
            if (saved != null) {
                WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
                final String messageJson = ChatWebSocketServer.toJson(vo);
                List<ChatGroupMember> members = chatGroupMapper.findMembersByGroupId(event.getGroupId());
                List<CompletableFuture<Void>> pushFutures = members.stream()
                        .filter(member -> !member.getUserId().equals(event.getSenderId()))
                        .map(member -> CompletableFuture.runAsync(() -> {
                            boolean pushed = wsSessionManager.sendMessage(member.getUserId(), messageJson);
                            if (!pushed) {
                                redisPubSubService.publish(
                                        new RedisChatMessageDTO(member.getUserId(), messageJson));
                            }
                        }, taskExecutor))
                        .toList();
                CompletableFuture.allOf(pushFutures.toArray(new CompletableFuture[0])).join();
                wsSessionManager.sendMessage(event.getSenderId(), messageJson);
            }
        } catch (Exception e) {
            System.err.println("[RabbitMQ Consumer] 处理群聊消息失败: " + e.getMessage());
            e.printStackTrace();
            if (json == null) {
                wsSessionManager.sendMessage(event.getSenderId(),
                        "{\"type\":\"ack\",\"tempId\":\"" + event.getTempId() + "\"}");
            }
            throw new RuntimeException(e);
        }
    }
}
