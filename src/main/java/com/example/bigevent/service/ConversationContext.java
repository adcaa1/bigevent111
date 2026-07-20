package com.example.bigevent.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 对话上下文容器。
 * <p>
 * 封装两层记忆：
 * <ul>
 *     <li>summary：历史对话摘要（长期记忆）</li>
 *     <li>recentMessages：最近 N 条原始消息（短期记忆，通常保留 6 轮 / 12 条）</li>
 * </ul>
 */
public record ConversationContext(String summary, List<ChatMessage> recentMessages) {
}
