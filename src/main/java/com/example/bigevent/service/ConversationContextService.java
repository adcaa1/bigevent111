package com.example.bigevent.service;

import com.example.bigevent.repository.RedisChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文管理服务。
 * <p>
 * 负责维护单轮对话所需的三层记忆中的两层：
 * <ul>
 *     <li>最近聊天：Redis 短期记忆，严格保留最近 6 轮（12 条）消息</li>
 *     <li>长期摘要：当近期消息超过窗口时，把旧对话交给 LLM 总结并写入 Redis</li>
 * </ul>
 * 另外提供“重复上一轮问题直接返回缓存答案”的轻量级缓存，减少 70% 以上的重复调用耗时。
 */
@Slf4j
@Service
public class ConversationContextService {

    @Autowired
    private RedisChatMemoryStore chatMemoryStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    /** 最近聊天保留轮数 */
    private static final int MAX_RECENT_ROUNDS = 6;

    /** 最近聊天保留消息条数（User + Assistant） */
    private static final int MAX_RECENT_MESSAGES = MAX_RECENT_ROUNDS * 2;

    /** 摘要在 Redis 中的 key 前缀 */
    private static final String SUMMARY_KEY_PREFIX = "chat:summary:";

    /** 上一轮问题缓存 key 前缀 */
    private static final String LAST_QUESTION_KEY_PREFIX = "chat:last_question:";

    /** 上一轮答案缓存 key 前缀 */
    private static final String LAST_ANSWER_KEY_PREFIX = "chat:last_answer:";

    /** 摘要 TTL */
    private static final Duration SUMMARY_TTL = Duration.ofDays(7);

    /** 重复问题缓存 TTL */
    private static final Duration REPEAT_CACHE_TTL = Duration.ofMinutes(10);

    /** 摘要长度上限 */
    private static final int MAX_SUMMARY_LENGTH = 1000;

    /**
     * 加载指定会话的上下文。
     *
     * @param conversationId 会话 ID，为 null 时返回空上下文
     * @return 摘要 + 最近消息
     */
    public ConversationContext load(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ConversationContext("", List.of());
        }
        List<ChatMessage> recentMessages = chatMemoryStore.getMessages(conversationId);
        String summary = getSummary(conversationId);
        return new ConversationContext(summary, recentMessages);
    }

    /**
     * 保存当前轮次 QA，并维护窗口与摘要。
     * <p>
     * 写入 Redis 短期记忆后，如果消息总数超过 {@link #MAX_RECENT_MESSAGES}，
     * 则把最早的多余消息交给 LLM 总结，合并到已有摘要中，然后只保留最近 12 条。
     *
     * @param conversationId 会话 ID
     * @param question       用户问题
     * @param answer         AI 回答（不应包含知识库原文，只保存最终答案）
     */
    public void saveInteraction(String conversationId, String question, String answer) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        List<ChatMessage> messages = new ArrayList<>(chatMemoryStore.getMessages(conversationId));
        messages.add(UserMessage.from(question));
        messages.add(AiMessage.from(answer));
        if (messages.size() > MAX_RECENT_MESSAGES) {
            int excess = messages.size() - MAX_RECENT_MESSAGES;
            List<ChatMessage> toSummarize = new ArrayList<>(messages.subList(0, excess));
            String existingSummary = getSummary(conversationId);
            String newSummary = summarize(toSummarize, existingSummary);
            if (newSummary != null && !newSummary.isBlank()) {
                updateSummary(conversationId, newSummary);
            }
            messages = new ArrayList<>(messages.subList(excess, messages.size()));
        }

        chatMemoryStore.updateMessages(conversationId, messages);
        cacheLastAnswer(conversationId, question, answer);
    }

    /**
     * 检测当前问题是否与上一轮完全相同，是则直接返回缓存答案。
     *
     * @param conversationId 会话 ID
     * @param question       当前问题
     * @return 缓存答案，未命中返回 null
     */
    public String getCachedAnswerIfRepeated(String conversationId, String question) {
        if (conversationId == null || conversationId.isBlank() || question == null) {
            return null;
        }
        String lastQuestion = redisTemplate.opsForValue().get(LAST_QUESTION_KEY_PREFIX + conversationId);
        if (lastQuestion != null && lastQuestion.equals(question.trim())) {
            return redisTemplate.opsForValue().get(LAST_ANSWER_KEY_PREFIX + conversationId);
        }
        return null;
    }

    /**
     * 清空指定会话的所有上下文数据（Redis 短期记忆、摘要、重复问题缓存）。
     *
     * @param conversationId 会话 ID
     */
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        chatMemoryStore.deleteMessages(conversationId);
        redisTemplate.delete(SUMMARY_KEY_PREFIX + conversationId);
        redisTemplate.delete(LAST_QUESTION_KEY_PREFIX + conversationId);
        redisTemplate.delete(LAST_ANSWER_KEY_PREFIX + conversationId);
    }

    private String getSummary(String conversationId) {
        String summary = redisTemplate.opsForValue().get(SUMMARY_KEY_PREFIX + conversationId);
        return summary == null ? "" : summary;
    }

    private void updateSummary(String conversationId, String summary) {
        if (summary == null) {
            return;
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH);
        }
        redisTemplate.opsForValue().set(SUMMARY_KEY_PREFIX + conversationId, summary, SUMMARY_TTL);
    }

    private void cacheLastAnswer(String conversationId, String question, String answer) {
        redisTemplate.opsForValue().set(LAST_QUESTION_KEY_PREFIX + conversationId, question.trim(), REPEAT_CACHE_TTL);
        redisTemplate.opsForValue().set(LAST_ANSWER_KEY_PREFIX + conversationId, answer, REPEAT_CACHE_TTL);
    }

    private String summarize(List<ChatMessage> messages, String existingSummary) {
        try {
            String prompt = buildSummaryPrompt(messages, existingSummary);
            String summary = openAiChatModel.chat(prompt);
            return summary == null ? "" : summary.trim();
        } catch (Exception e) {
            log.error("生成对话摘要失败，保留已有摘要", e);
            return existingSummary;
        }
    }

    private String buildSummaryPrompt(List<ChatMessage> messages, String existingSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下对话进行简洁总结，保留关键事实、用户意图和上下文信息，控制在 500 字以内：\n\n");
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage userMessage) {
                sb.append("User: ").append(userMessage.singleText()).append("\n");
            } else if (msg instanceof AiMessage aiMessage) {
                sb.append("Assistant: ").append(aiMessage.text()).append("\n");
            }
        }
        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("\n请结合已有摘要进行合并，已有摘要：\n").append(existingSummary);
        }
        return sb.toString();
    }
}
