package com.example.bigevent.service;

import dev.langchain4j.service.SystemMessage;

/**
 * RAG Agent 图书管理助手接口
 * <p>
 * 通过自然语言帮用户查询、添加、修改、删除图书（文章）。
 * 由 RagService 使用 AiServices.builder() 手动构建代理。
 */
@SystemMessage("你是图书管理助手，可以通过自然语言帮用户查询、添加、修改、删除图书（文章）。调用工具时把文章当作图书处理。回答要简洁。")
public interface RagAgentAssistant {

    String chat(String message);
}
