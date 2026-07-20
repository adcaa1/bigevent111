package com.example.bigevent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * AI文章管理服务
 * 通过自然语言对话实现文章的增删改查操作
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = "articleTools"
)
public interface AiArticleService {

    /**
     * 流式对话式文章管理
     * AI会自动识别用户意图并调用相应的工具方法执行文章操作
     * 
     * @param memoryId 用户ID，用于维护对话上下文
     * @param message 用户的自然语言指令，例如：
     *                - "帮我添加一篇文章，标题是'测试文章'，内容是'这是测试内容'"
     *                - "查询所有文章"
     *                - "删除ID为1的文章"
     *                - "更新ID为2的文章，标题改为'新标题'"
     * @return 流式响应
     */
    Flux<String> chatWithArticleTools(@MemoryId Long memoryId, String message);
}
