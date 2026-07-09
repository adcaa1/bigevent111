package com.example.bigevent.websocket;

import com.example.bigevent.domain.ChatGroupMember;
import com.example.bigevent.domain.ChatMessage;
import com.example.bigevent.domain.vo.WsMessageDTO;
import com.example.bigevent.domain.vo.WsMessageVO;
import com.example.bigevent.mapper.ChatGroupMapper;
import com.example.bigevent.config.RabbitMQConfig;
import com.example.bigevent.domain.ChatMessageEvent;
import com.example.bigevent.service.ChatService;
import com.example.bigevent.service.RedisPubSubService;
import com.example.bigevent.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 聊天服务端
 */
@Component
@ServerEndpoint("/ws/chat")
public class ChatWebSocketServer {

    private static ChatService chatService;
    private static WsSessionManager wsSessionManager;
    private static RedisPubSubService redisPubSubService;
    private static StringRedisTemplate stringRedisTemplate;
    private static ChatGroupMapper chatGroupMapper;
    private static RabbitTemplate rabbitTemplate;

    @Autowired
    public void setChatService(ChatService service) {
        ChatWebSocketServer.chatService = service;
    }

    @Autowired
    public void setWsSessionManager(WsSessionManager manager) {
        ChatWebSocketServer.wsSessionManager = manager;
    }

    @Autowired
    public void setRedisPubSubService(RedisPubSubService service) {
        ChatWebSocketServer.redisPubSubService = service;
    }

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        ChatWebSocketServer.stringRedisTemplate = template;
    }

    @Autowired
    public void setChatGroupMapper(ChatGroupMapper mapper) {
        ChatWebSocketServer.chatGroupMapper = mapper;
    }

    @Autowired
    public void setRabbitTemplate(RabbitTemplate template) {
        ChatWebSocketServer.rabbitTemplate = template;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 连接建立
     */
    @OnOpen
    public void onOpen(Session session) {
        System.out.println("[WebSocket] 收到连接请求: " + session.getRequestURI());
        Integer userId = authenticate(session);
        if (userId == null) {
            System.err.println("[WebSocket] 认证失败，关闭连接");
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "认证失败"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        // 存入 Session 属性
        session.getUserProperties().put("userId", userId);
        wsSessionManager.addSession(userId, session);

        // Redis 标记在线（60 秒自动过期，断网后自动清除）
        String onlineKey = "user:online:" + userId;
        stringRedisTemplate.opsForValue().set(onlineKey, session.getId(), 60, TimeUnit.SECONDS);

        System.out.println("WebSocket 连接成功: userId=" + userId + ", 当前在线=" + wsSessionManager.getOnlineCount());
    }

    /**
     * 接收消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("[WebSocket] 收到原始消息: " + message);
        Integer senderId = (Integer) session.getUserProperties().get("userId");
        if (senderId == null) {
            System.err.println("[WebSocket] userId 为空，无法处理消息");
            return;
        }

        try {
            WsMessageDTO dto = objectMapper.readValue(message, WsMessageDTO.class);
            System.out.println("[WebSocket] 解析消息: type=" + dto.getType() + ", senderId=" + senderId);

            if ("ping".equals(dto.getType())) {
                // 心跳响应，同时刷新在线状态 TTL
                stringRedisTemplate.expire("user:online:" + senderId, 60, TimeUnit.SECONDS);
                session.getBasicRemote().sendText("{\"type\":\"pong\"}");
                return;
            }

            if ("private".equals(dto.getType())) {
                handlePrivateMessage(senderId, dto);
            } else if ("group".equals(dto.getType())) {
                handleGroupMessage(senderId, dto);
            } else {
                System.err.println("[WebSocket] 未知的消息类型: " + dto.getType());
            }

        } catch (Exception e) {
            System.err.println("[WebSocket] 消息解析失败: " + e.getMessage());
            e.printStackTrace();
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 连接关闭
     */
    @OnClose
    public void onClose(Session session) {
        Integer userId = (Integer) session.getUserProperties().get("userId");
        wsSessionManager.removeSession(session);
        if (userId != null) {
            stringRedisTemplate.delete("user:online:" + userId);
        }
        System.out.println("WebSocket 断开: userId=" + userId + ", 当前在线=" + wsSessionManager.getOnlineCount());
    }

    /**
     * 发生错误
     */
    @OnError
    public void onError(Session session, Throwable error) {
        Integer userId = (Integer) session.getUserProperties().get("userId");
        System.err.println("WebSocket 错误: userId=" + userId + ", error=" + error.getMessage());
    }

    /**
     * JWT 认证
     */
    private Integer authenticate(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokenList = params.get("token");
        if (tokenList == null || tokenList.isEmpty()) {
            return null;
        }
        String token = tokenList.get(0);
        try {
            Map<String, Object> claims = JwtUtil.parseToken(token);
            // 校验 Redis 中 token 是否有效
            String redisToken = stringRedisTemplate.opsForValue().get(token);
            if (redisToken == null) {
                return null;
            }
            return (Integer) claims.get("id");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 处理单聊消息 — 发送到 RabbitMQ 异步处理
     */
    private void handlePrivateMessage(Integer senderId, WsMessageDTO dto) {
        Integer receiverId = dto.getReceiverId();
        String content = dto.getContent();
        if (receiverId == null || content == null || content.trim().isEmpty()) {
            System.err.println("[WebSocket] 单聊消息参数无效: senderId=" + senderId + ", receiverId=" + receiverId + ", content=" + content);
            return;
        }

        // 组装 MQ 消息事件
        ChatMessageEvent event = new ChatMessageEvent();
        event.setSenderId(senderId);
        event.setReceiverId(receiverId);
        event.setContent(content.trim());
        event.setTempId(dto.getTempId());
        event.setType(0);

        System.out.println("[WebSocket] 准备发送单聊消息到RabbitMQ: " + event);

        // 发送到 RabbitMQ
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE,
                    RabbitMQConfig.CHAT_PRIVATE_KEY, event);
            System.out.println("[WebSocket] 单聊消息已发送到RabbitMQ: senderId=" + senderId + ", receiverId=" + receiverId);
        } catch (Exception e) {
            System.err.println("[WebSocket] 发送消息到RabbitMQ失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 立刻回推 ACK 给发送者
        wsSessionManager.sendMessage(senderId,
                "{\"type\":\"ack\",\"tempId\":\"" + dto.getTempId() + "\"}");
    }

    /**
     * 处理群聊消息 — 发送到 RabbitMQ 异步处理
     */
    private void handleGroupMessage(Integer senderId, WsMessageDTO dto) {
        Integer groupId = dto.getGroupId();
        String content = dto.getContent();
        if (groupId == null || content == null || content.trim().isEmpty()) {
            System.err.println("[WebSocket] 群聊消息参数无效: senderId=" + senderId + ", groupId=" + groupId + ", content=" + content);
            return;
        }

        // 检查发送者是否在群中（权限校验保留在 WebSocket 层）
        if (!chatService.isGroupMember(groupId, senderId)) {
            Session session = wsSessionManager.getSession(senderId);
            if (session != null) {
                sendError(session, "您不在该群中");
            }
            return;
        }

        // 组装 MQ 消息事件
        ChatMessageEvent event = new ChatMessageEvent();
        event.setSenderId(senderId);
        event.setGroupId(groupId);
        event.setContent(content.trim());
        event.setTempId(dto.getTempId());
        event.setType(1);

        System.out.println("[WebSocket] 准备发送群聊消息到RabbitMQ: " + event);

        // 发送到 RabbitMQ
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CHAT_EXCHANGE,
                    RabbitMQConfig.CHAT_GROUP_KEY, event);
            System.out.println("[WebSocket] 群聊消息已发送到RabbitMQ: senderId=" + senderId + ", groupId=" + groupId);
        } catch (Exception e) {
            System.err.println("[WebSocket] 发送消息到RabbitMQ失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 立刻回推 ACK 给发送者
        wsSessionManager.sendMessage(senderId,
                "{\"type\":\"ack\",\"tempId\":\"" + dto.getTempId() + "\"}");
    }

    /**
     * 构建推送消息VO
     */
    public static WsMessageVO buildMessageVO(ChatMessage msg, String tempId) {
        WsMessageVO vo = new WsMessageVO();
        if (msg.getType() == 0) {
            vo.setType("private");
        } else {
            vo.setType("group");
        }
        vo.setMessageId(msg.getId());
        vo.setSenderId(msg.getSenderId());
        vo.setReceiverId(msg.getReceiverId());
        vo.setGroupId(msg.getGroupId());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime().format(formatter));
        vo.setTempId(tempId);
        return vo;
    }

    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private void sendError(Session session, String errorMsg) {
        try {
            WsMessageDTO error = new WsMessageDTO();
            error.setType("system");
            error.setSubType("error");
            error.setMessage(errorMsg);
            session.getBasicRemote().sendText(toJson(error));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
