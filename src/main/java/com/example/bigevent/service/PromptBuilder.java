package com.example.bigevent.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话 Prompt 统一构建器
 * <p>
 * 负责把三层记忆（用户关键事实、相关历史、近期对话）与当前问题，
 * 按固定顺序组装成可供大模型使用的 ChatMessage 列表。
 * <p>
 * 构建顺序：System → User Fact → Relevant History → Recent Chat → Current Question
 */
@Component
public class PromptBuilder {

    /** 相关历史最多保留条数，防止 Prompt 过长 */
    private static final int MAX_RELEVANT_HISTORY = 5;

    /** 历史消息最多保留条数，超出时优先丢弃最早的消息 */
    private static final int MAX_RECENT_MESSAGES = 20;

    /**
     * 构建完整对话消息列表。
     * <p>
     * 把 system prompt、用户事实、相关历史放到 {@link SystemMessage} 中，
     * 把 Redis 中的近期对话按原角色（User/Assistant）追加，最后放入当前问题。
     * 这样大模型能正确识别每条消息的角色，而不是把整段历史当成一条用户消息。
     *
     * @param systemPrompt     系统提示词，定义助手角色和行为
     * @param userFactText     用户关键事实文本，没有时传空字符串
     * @param relevantHistory  ES 召回的相关历史 QA 列表
     * @param recentMessages   Redis 短期记忆中的近期对话消息
     * @param currentQuestion  用户当前问题
     * @return 按角色组装的 ChatMessage 列表
     */
    public List<ChatMessage> build(String systemPrompt,
                                   String userFactText,
                                   List<String> relevantHistory,
                                   List<ChatMessage> recentMessages,
                                   String currentQuestion) {
        List<ChatMessage> messages = new ArrayList<>();

        // SystemMessage 中放角色定义、用户事实、相关历史
        StringBuilder systemBuilder = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            systemBuilder.append(systemPrompt);
        }

        if (userFactText != null && !userFactText.isBlank()) {
            if (!systemBuilder.isEmpty()) {
                systemBuilder.append("\n\n");
            }
            systemBuilder.append(userFactText);
        }

        if (relevantHistory != null && !relevantHistory.isEmpty()) {
            if (!systemBuilder.isEmpty()) {
                systemBuilder.append("\n\n");
            }
            systemBuilder.append("以下是与当前问题相关的历史对话片段，可作为参考：\n");
            int count = 0;
            for (String history : relevantHistory) {
                if (count >= MAX_RELEVANT_HISTORY) {
                    break;
                }
                systemBuilder.append("---\n").append(history).append("\n");
                count++;
            }
        }

        if (!systemBuilder.isEmpty()) {
            messages.add(SystemMessage.from(systemBuilder.toString()));
        }

        // 近期对话保持原始角色追加，避免模型把历史当成当前用户输入
        if (recentMessages != null && !recentMessages.isEmpty()) {
            int start = Math.max(0, recentMessages.size() - MAX_RECENT_MESSAGES);
            messages.addAll(recentMessages.subList(start, recentMessages.size()));
        }

        messages.add(UserMessage.from(currentQuestion));
        return messages;
    }
}
