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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * RabbitMQ 聊天消息消费者
 * 异步处理：存数据库 + 推送给接收者
 */
@Slf4j
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
        log.info("[RabbitMQ Consumer] 收到单聊消息: senderId={}, receiverId={}, tempId={}",
                event.getSenderId(), event.getReceiverId(), event.getTempId());
        String json = null;
        try {
            // 1. 存数据库（temp_id 唯一索引保证幂等，重复消息不会重复插入）
            ChatMessage saved = chatService.savePrivateMessage(
                    event.getSenderId(),
                    event.getReceiverId(),
                    event.getContent(),
                    event.getTempId()
            );
            log.info("[RabbitMQ Consumer] 单聊消息已保存到数据库, messageId={}", saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            json = ChatWebSocketServer.toJson(vo);

            // 3. 推送给接收者
            boolean pushed = wsSessionManager.sendMessage(event.getReceiverId(), json);
            if (!pushed) {
                // 不在本机，Redis 广播给集群其他机器
                log.debug("[RabbitMQ Consumer] 接收者不在本机，通过Redis广播, receiverId={}", event.getReceiverId());
                redisPubSubService.publish(
                        new RedisChatMessageDTO(event.getReceiverId(), json));
            } else {
                log.debug("[RabbitMQ Consumer] 消息已推送给接收者: {}", event.getReceiverId());
            }

            // 4. 回推给发送者
            wsSessionManager.sendMessage(event.getSenderId(), json);
            log.debug("[RabbitMQ Consumer] 消息已回推给发送者: {}", event.getSenderId());

        } catch (DuplicateKeyException e) {
            // temp_id 唯一索引冲突 → 重复消息（上次已存库但可能没推送完）
            log.warn("[RabbitMQ Consumer] 检测到重复消息，重新推送: tempId={}", event.getTempId());
            ChatMessage saved = chatService.getMessageBySenderAndTempId(event.getSenderId(), event.getTempId());
            if (saved != null) {
                WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
                json = ChatWebSocketServer.toJson(vo);
                wsSessionManager.sendMessage(event.getReceiverId(), json);
                wsSessionManager.sendMessage(event.getSenderId(), json);
            }
        } catch (Exception e) {
            log.error("[RabbitMQ Consumer] 处理单聊消息失败, tempId={}", event.getTempId(), e);
            // 先回推 ACK，避免发送者一直等待
            if (json == null) {
                wsSessionManager.sendMessage(event.getSenderId(),
                        "{\"type\":\"ack\",\"tempId\":\"" + event.getTempId() + "\"}");
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 群聊推送批次大小：每个 executor 任务处理 200 人，避免单任务过大。
     * 万人群约拆成 50 个任务，配合 async remote 发送，不会阻塞消费者。
     */
    private static final int GROUP_PUSH_BATCH_SIZE = 200;

    /**
     * 消费群聊消息
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_GROUP_QUEUE)
    public void handleGroupMessage(ChatMessageEvent event) {
        log.info("[RabbitMQ Consumer] 收到群聊消息: senderId={}, groupId={}, tempId={}",
                event.getSenderId(), event.getGroupId(), event.getTempId());
        String json = null;
        try {
            // 1. 存数据库
            ChatMessage saved = chatService.saveGroupMessage(
                    event.getSenderId(),
                    event.getGroupId(),
                    event.getContent(),
                    event.getTempId()
            );
            log.info("[RabbitMQ Consumer] 群聊消息已保存到数据库, messageId={}", saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            json = ChatWebSocketServer.toJson(vo);
            final String messageJson = json;

            // 3. 回推给发送者（先回推，让发送者立即看到）
            wsSessionManager.sendMessage(event.getSenderId(), messageJson);

            // 4. 批量广播给群成员，不阻塞 RabbitMQ 消费者
            broadcastGroupMessage(event.getSenderId(), event.getGroupId(), messageJson);

        } catch (DuplicateKeyException e) {
            log.warn("[RabbitMQ Consumer] 检测到重复群聊消息，重新推送: tempId={}", event.getTempId());
            ChatMessage saved = chatService.getMessageBySenderAndTempId(event.getSenderId(), event.getTempId());
            if (saved != null) {
                WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
                String messageJson = ChatWebSocketServer.toJson(vo);
                broadcastGroupMessage(event.getSenderId(), event.getGroupId(), messageJson);
                wsSessionManager.sendMessage(event.getSenderId(), messageJson);
            }
        } catch (Exception e) {
            log.error("[RabbitMQ Consumer] 处理群聊消息失败, tempId={}", event.getTempId(), e);
            if (json == null) {
                wsSessionManager.sendMessage(event.getSenderId(),
                        "{\"type\":\"ack\",\"tempId\":\"" + event.getTempId() + "\"}");
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 批量广播群消息。
     * <p>
     * 按 {@link #GROUP_PUSH_BATCH_SIZE} 拆成多个任务丢到线程池，每个任务内使用 WebSocket
     * async remote 发送，不阻塞消费者线程，也不创建和成员数等量的 CompletableFuture。
     */
    private void broadcastGroupMessage(Integer senderId, Integer groupId, String messageJson) {
        List<ChatGroupMember> members = chatGroupMapper.findMembersByGroupId(groupId);
        List<Integer> targetUserIds = members.stream()
                .map(ChatGroupMember::getUserId)
                .filter(userId -> !userId.equals(senderId))
                .collect(Collectors.toList());

        if (targetUserIds.isEmpty()) {
            return;
        }

        log.debug("[RabbitMQ Consumer] 群聊广播目标用户数: {}", targetUserIds.size());

        for (int i = 0; i < targetUserIds.size(); i += GROUP_PUSH_BATCH_SIZE) {
            List<Integer> batch = targetUserIds.subList(i, Math.min(i + GROUP_PUSH_BATCH_SIZE, targetUserIds.size()));
            taskExecutor.execute(() -> {
                for (Integer userId : batch) {
                    try {
                        boolean pushed = wsSessionManager.sendMessage(userId, messageJson);
                        if (!pushed) {
                            // 不在本机，通过 Redis 广播给集群其他节点
                            redisPubSubService.publish(new RedisChatMessageDTO(userId, messageJson));
                        }
                    } catch (Exception ex) {
                        log.error("[RabbitMQ Consumer] 群聊推送给用户失败, userId={}", userId, ex);
                    }
                }
            });
        }
    }
}
