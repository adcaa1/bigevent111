package com.example.bigevent.service;

import com.example.bigevent.config.RabbitMQConfig;
import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.ChatMessageEvent;
import com.example.bigevent.domain.vo.WsMessageVO;
import com.example.bigevent.mapper.ChatGroupMapper;
import com.example.bigevent.websocket.ChatWebSocketServer;
import com.example.bigevent.websocket.WsSessionManager;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * 消费单聊消息
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_PRIVATE_QUEUE)
    public void handlePrivateMessage(ChatMessageEvent event) {
        System.out.println("[RabbitMQ Consumer] 收到单聊消息: " + event);
        try {
            // 1. 存数据库
            System.out.println("[RabbitMQ Consumer] 开始保存单聊消息到数据库...");
            ChatMessage saved = chatService.savePrivateMessage(
                    event.getSenderId(),
                    event.getReceiverId(),
                    event.getContent()
            );
            System.out.println("[RabbitMQ Consumer] 单聊消息已保存到数据库, messageId=" + saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            String json = ChatWebSocketServer.toJson(vo);

            // 3. 推送给接收者
            boolean pushed = wsSessionManager.sendMessage(event.getReceiverId(), json);
            if (!pushed) {
                // 不在本机，Redis 广播给集群其他机器
                System.out.println("[RabbitMQ Consumer] 接收者不在本机，通过Redis广播");
                redisPubSubService.publish(
                        new RedisPubSubService.RedisChatMessage(event.getReceiverId(), json));
            } else {
                System.out.println("[RabbitMQ Consumer] 消息已推送给接收者: " + event.getReceiverId());
            }

            // 4. 回推给发送者（带 messageId 的完整消息）
            wsSessionManager.sendMessage(event.getSenderId(), json);
            System.out.println("[RabbitMQ Consumer] 消息已回推给发送者: " + event.getSenderId());

        } catch (Exception e) {
            System.err.println("[RabbitMQ Consumer] 处理单聊消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 消费群聊消息
     */
    @RabbitListener(queues = RabbitMQConfig.CHAT_GROUP_QUEUE)
    public void handleGroupMessage(ChatMessageEvent event) {
        System.out.println("[RabbitMQ Consumer] 收到群聊消息: " + event);
        try {
            // 1. 存数据库
            System.out.println("[RabbitMQ Consumer] 开始保存群聊消息到数据库...");
            ChatMessage saved = chatService.saveGroupMessage(
                    event.getSenderId(),
                    event.getGroupId(),
                    event.getContent()
            );
            System.out.println("[RabbitMQ Consumer] 群聊消息已保存到数据库, messageId=" + saved.getId());

            // 2. 组装推送消息
            WsMessageVO vo = ChatWebSocketServer.buildMessageVO(saved, event.getTempId());
            String json = ChatWebSocketServer.toJson(vo);

            // 3. 获取群成员并推送
            List<ChatGroupMember> members = chatGroupMapper.findMembersByGroupId(event.getGroupId());
            System.out.println("[RabbitMQ Consumer] 群组成员数量: " + members.size());
            for (ChatGroupMember member : members) {
                if (member.getUserId().equals(event.getSenderId())) {
                    continue; // 不推给自己
                }
                boolean pushed = wsSessionManager.sendMessage(member.getUserId(), json);
                if (!pushed) {
                    System.out.println("[RabbitMQ Consumer] 群组成员 " + member.getUserId() + " 不在本机，通过Redis广播");
                    redisPubSubService.publish(
                            new RedisPubSubService.RedisChatMessage(member.getUserId(), json));
                } else {
                    System.out.println("[RabbitMQ Consumer] 消息已推送给群组成员: " + member.getUserId());
                }
            }

            // 4. 回推给发送者（带 messageId 的完整消息）
            wsSessionManager.sendMessage(event.getSenderId(), json);
            System.out.println("[RabbitMQ Consumer] 消息已回推给发送者: " + event.getSenderId());

        } catch (Exception e) {
            System.err.println("[RabbitMQ Consumer] 处理群聊消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
