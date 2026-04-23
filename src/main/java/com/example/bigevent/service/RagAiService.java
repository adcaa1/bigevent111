package com.example.bigevent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * RAG专用的AI服务
 * 不使用任何tools，仅用于基于知识库的问答
 * 
 * 这是一个 @AiService 接口，LangChain4j 会自动创建实现类
 * 通过 chatModelRef 指定不使用 tools
 */
@AiService
public interface RagAiService {

    @SystemMessage(
        "你是一个基于知识库的智能助手。\n" +
        "请根据提供的知识库内容回答问题。\n" +
        "如果知识库中没有相关信息，请明确说明\"知识库中没有相关信息\"。\n" +
        "不要编造知识库中不存在的内容。\n" +
        "回答要简洁、准确。"
    )
    String chat(@UserMessage String userMessage);
}
