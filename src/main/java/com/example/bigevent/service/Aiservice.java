package com.example.bigevent.service;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "openAiChatModel")
public interface Aiservice {

    @SystemMessage("你是一个友好的AI助手，回答要简洁、热情")
    String chat(@UserMessage String userMessage);
}
