package com.example.bigevent.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话 Prompt 统一构建器。
 * <p>
 * 按文档推荐的分层上下文方案组装 Prompt：
 * System Prompt + 用户关键事实 + 历史摘要 + 最近 6 轮对话 + RAG Top5 + 当前问题。
 * 历史消息保持原始 User/Assistant 角色，避免模型把整段历史当成一条用户输入。
 */
@Component
public class PromptBuilder {

    /** 最近对话最多保留 6 轮（12 条消息） */
    private static final int MAX_RECENT_ROUNDS = 6;

    private static final int MAX_RECENT_MESSAGES = MAX_RECENT_ROUNDS * 2;

    /**
     * 构建完整对话消息列表。
     * <p>
     * System Prompt 只保留角色定义、用户事实、历史摘要等相对固定的内容；
     * RAG 召回的知识库片段放到当前 User 消息中，避免把大量动态知识塞进 System。
     *
     * @param systemPrompt    系统提示词
     * @param userFactText    用户关键事实文本，没有时传空字符串
     * @param summary         历史对话摘要（长期记忆），没有时传空字符串
     * @param recentMessages  Redis 短期记忆中的近期对话消息
     * @param ragChunks       RAG 召回的知识库片段（Top5），没有时传空列表
     * @param currentQuestion 用户当前问题
     * @return 按角色组装的 ChatMessage 列表
     */
    public List<ChatMessage> build(String systemPrompt,
                                   String userFactText,
                                   String summary,
                                   List<ChatMessage> recentMessages,
                                   List<String> ragChunks,
                                   String currentQuestion) {
        List<ChatMessage> messages = new ArrayList<>();

        // SystemMessage 只放角色定义、用户事实、历史摘要
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

        if (summary != null && !summary.isBlank()) {
            if (!systemBuilder.isEmpty()) {
                systemBuilder.append("\n\n");
            }
            systemBuilder.append("历史对话摘要：\n").append(summary);
        }

        if (!systemBuilder.isEmpty()) {
            messages.add(SystemMessage.from(systemBuilder.toString()));
        }

        // 近期对话保持原始角色追加，避免模型把历史当成当前用户输入
        if (recentMessages != null && !recentMessages.isEmpty()) {
            int start = Math.max(0, recentMessages.size() - MAX_RECENT_MESSAGES);
            messages.addAll(recentMessages.subList(start, recentMessages.size()));
        }

        // 知识库片段放到当前 User 消息中，不再重复列出“引用来源”
        StringBuilder userBuilder = new StringBuilder();
        if (ragChunks != null && !ragChunks.isEmpty()) {
            userBuilder.append("请根据以下知识库片段回答问题，不要编造知识库中不存在的内容，")
                    .append("使用 [^1]、[^2] 等标注来源编号：\n\n");
            for (int i = 0; i < ragChunks.size(); i++) {
                userBuilder.append("---\n").append(ragChunks.get(i)).append("\n");
            }
            userBuilder.append("\n问题：").append(currentQuestion);
        } else {
            userBuilder.append(currentQuestion);
        }

        messages.add(UserMessage.from(userBuilder.toString()));
        return messages;
    }
}
