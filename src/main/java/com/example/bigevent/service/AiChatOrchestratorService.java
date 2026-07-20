package com.example.bigevent.service;

import com.example.bigevent.domain.AiConversation;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * AI 对话编排层。
 * <p>
 * 整合三层记忆（用户关键事实、历史摘要、最近 6 轮对话）并统一构建 Prompt，
 * 手动调用 StreamingChatModel 生成流式回复。
 */
@Slf4j
@Service
public class AiChatOrchestratorService {

    private static final String SYSTEM_PROMPT = "你是一个友好、简洁且热情的 AI 助手。请根据提供的用户关键信息、历史对话摘要和最近对话上下文回答问题。";

    @Autowired
    private UserFactService userFactService;

    @Autowired
    private AiConversationService aiConversationService;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    private AiArticleService aiArticleService;

    @Autowired
    private Executor aiExecutor;

    @Autowired
    private ConversationContextService conversationContextService;

    /**
     * 通用 AI 流式聊天。
     * <p>
     * 整体流程：
     * <ol>
     *     <li>确保会话存在</li>
     *     <li>重复问题走缓存直接返回答案</li>
     *     <li>读取 Redis 短期记忆（最近 6 轮）与历史摘要</li>
     *     <li>读取 MySQL 用户关键事实</li>
     *     <li>通过 PromptBuilder 组装分层 Prompt</li>
     *     <li>调用流式模型生成回复</li>
     *     <li>保存当前 QA 到上下文管理服务，维护窗口与摘要</li>
     * </ol>
     *
     * @param userId         当前用户 ID
     * @param conversationId 会话 ID，为空时自动创建
     * @param message        用户当前问题
     * @return 流式 AI 回复
     */
    public Flux<String> chatStream(Integer userId, String conversationId, String message) {
        String convId = ensureConversation(userId, conversationId);

        String cachedAnswer = conversationContextService.getCachedAnswerIfRepeated(convId, message);
        if (cachedAnswer != null) {
            log.info("通用聊天命中重复问题缓存，直接返回答案");
            return Flux.just(cachedAnswer);
        }

        ConversationContext context = conversationContextService.load(convId);
        String userFactText = userFactService.formatFactsForPrompt(userId);

        List<ChatMessage> promptMessages = promptBuilder.build(
                SYSTEM_PROMPT, userFactText, context.summary(), context.recentMessages(), null, message);

        return Flux.<String>create(sink -> streamingChatModel.chat(
                        promptMessages, buildHandler(sink, convId, message)))
                .subscribeOn(reactor.core.scheduler.Schedulers.fromExecutor(aiExecutor));
    }

    /**
     * AI 文章管理工具聊天（保留 LangChain4j 工具能力）。
     * <p>
     * 该接口不直接走通用 PromptBuilder，而是复用 {@link AiArticleService} 中的 @AiService 工具定义。
     * 它会先确保会话存在，让 LangChain4j 自动管理短期记忆。
     *
     * @param userId         当前用户 ID
     * @param conversationId 会话 ID，为空时自动创建
     * @param message        用户当前问题
     * @return 流式 AI 回复
     */
    public Flux<String> chatWithArticleTools(Integer userId, String conversationId, String message) {
        String convId = ensureConversation(userId, conversationId);
        return aiArticleService.chatWithArticleTools(Long.valueOf(convId), message)
                .subscribeOn(reactor.core.scheduler.Schedulers.fromExecutor(aiExecutor));
    }

    /**
     * 构建流式响应处理器。
     * <p>
     * 负责把模型回调的 token 推给 Flux 消费者，并在流结束时把完整 QA 保存到上下文管理服务。
     */
    private StreamingChatResponseHandler buildHandler(FluxSink<String> sink,
                                                      String convId, String message) {
        return new StreamingChatResponseHandler() {
            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
                sink.next(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                String answer = answerBuilder.toString();
                sink.complete();
                saveInteraction(convId, message, answer);
            }

            @Override
            public void onError(Throwable error) {
                log.error("AI 流式响应失败", error);
                sink.error(error);
            }
        };
    }

    /**
     * 保存当前轮次的 QA，并交给 ConversationContextService 维护窗口与摘要。
     */
    private void saveInteraction(String convId, String message, String answer) {
        try {
            conversationContextService.saveInteraction(convId, message, answer);
        } catch (Exception e) {
            log.error("保存对话上下文失败", e);
        }
    }

    /**
     * 确保会话存在。
     * <p>
     * 如果调用方传入了有效的 conversationId，则校验后复用；
     * 否则为当前用户创建一个新的默认会话。
     *
     * @param userId         用户 ID
     * @param conversationId 可能为空的会话 ID
     * @return 最终使用的会话 ID 字符串
     */
    private String ensureConversation(Integer userId, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            try {
                AiConversation conversation = aiConversationService.findById(Long.valueOf(conversationId));
                if (conversation != null) {
                    return String.valueOf(conversation.getId());
                }
            } catch (NumberFormatException e) {
                log.warn("非法 conversationId: {}", conversationId);
            }
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        AiConversation conversation = aiConversationService.createConversation(userId, "新对话");
        return String.valueOf(conversation.getId());
    }
}
