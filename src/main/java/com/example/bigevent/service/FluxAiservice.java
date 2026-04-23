package com.example.bigevent.service;


import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface FluxAiservice {

    /**
     * 流式聊天接口
     * @param message 用户消息
     * @return 流式响应，每个元素是一个 token
     */
    Flux<String> chat(@MemoryId Long memoryId, @UserMessage String message);
}