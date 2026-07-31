package com.example.bigevent.controller;

import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.User;
import com.example.bigevent.service.AiChatOrchestratorService;
import com.example.bigevent.service.RagService;
import com.example.bigevent.service.Userservice;
import com.example.bigevent.util.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * AI Agent 控制器：统一收口所有“工具调用型” AI 对话接口。
 * <p>
 * 与 {@link ChatController} 中的通用 AI 聊天、RAG 问答分离，
 * 便于后续独立扩展 Agent 工具集、权限控制、调用审计等。
 */
@Slf4j
@RestController
public class AgentController {

    // AI 图书助手（基于知识库文档/图书的 Agent）
    @Autowired
    private RagService ragService;

    // 对话编排层：用于文章管理 Agent 的会话管理
    @Autowired
    private AiChatOrchestratorService aiChatOrchestratorService;

    // 用户服务（获取部门信息）
    @Autowired
    private Userservice userservice;

    // AI 专用线程池
    @Autowired
    @Qualifier("aiExecutor")
    private Executor aiExecutor;

    private Integer getCurrentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return claims == null ? null : (Integer) claims.get("id");
    }

    private Integer getCurrentUserDepartmentId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null) return null;
        String username = (String) claims.get("username");
        if (username == null) return null;
        User user = userservice.findid(username);
        return user != null ? user.getDepartmentId() : null;
    }

    /**
     * AI 图书助手：通过自然语言操作图书/文档。
     *
     * @param message        用户自然语言指令
     * @param conversationId 可选的会话 ID
     */
    @PostMapping("/rag/agent/chat")
    public Result<String> ragAgentChat(@RequestParam String message,
                                       @RequestParam(required = false) String conversationId) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        Integer departmentId = getCurrentUserDepartmentId();
        String answer = ragService.agentChat(message, currentUserId, departmentId, conversationId);
        return Result.success(answer);
    }

    /**
     * AI 图书助手（流式输出）：通过自然语言操作图书/文档。
     *
     * @param message        用户自然语言指令
     * @param conversationId 可选的会话 ID
     */
    @GetMapping(value = "/rag/agent/chat/stream", produces = "text/plain;charset=utf-8")
    public Flux<String> ragAgentChatStream(@RequestParam String message,
                                           @RequestParam(required = false) String conversationId) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Flux.error(new IllegalArgumentException("请先登录"));
        }
        Integer departmentId = getCurrentUserDepartmentId();
        return ragService.agentChatStream(message, currentUserId, departmentId, conversationId)
                .subscribeOn(reactor.core.scheduler.Schedulers.fromExecutor(aiExecutor))
                .timeout(Duration.ofSeconds(30),
                        Flux.just("\n【系统错误】响应超时，请稍后重试。"))
                .onErrorResume(e -> {
                    log.error("AI 图书助手流式对话失败, userId={}, message={}", currentUserId, message, e);
                    return Flux.just("\n【系统错误】处理失败，请稍后重试。");
                });
    }

    /**
     * AI 文章管理工具聊天（流式输出）。
     * <p>
     * 保留 LangChain4j 工具执行能力，会话由 conversationId 隔离。
     *
     * @param userId         用户 ID
     * @param conversationId 可选的会话 ID
     * @param message        用户问题
     * @return 流式 AI 回复
     */
    @GetMapping(value = "/chat/article", produces = "text/plain;charset=utf-8")
    public Flux<String> chatWithArticleTools(@RequestParam Integer userId,
                                             @RequestParam(required = false) String conversationId,
                                             @RequestParam String message) {
        return aiChatOrchestratorService.chatWithArticleTools(userId, conversationId, message)
                .timeout(Duration.ofSeconds(30),
                        Flux.just("\n【系统错误】响应超时，请稍后重试。"))
                .onErrorResume(e -> {
                    log.error("AI 文章管理流式对话失败, userId={}, message={}", userId, message, e);
                    return Flux.just("\n【系统错误】处理失败，请稍后重试。");
                });
    }
}
