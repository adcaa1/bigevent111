package com.example.bigevent.config;


import com.example.bigevent.repository.RedisChatMemoryStore;
import com.example.bigevent.service.ArticleTools;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;


@Configuration
public class AiConfig {
    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

//    注入向量数据库信息,配置有了，依赖会自动注入进去
    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;
    @Autowired
    private EmbeddingModel embeddingModel;
    
    @Autowired
    private ArticleTools articleTools;


    @Bean
    public ChatMemory chatMemory() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        return chatMemory;
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // 为每个 memoryId 创建独立的记忆窗口，保留最近 10 条消息
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)// 建议控制大小，避免 token 超限
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

/**
 * 向量数据库注入
 */
    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor() {
        return EmbeddingStoreIngestor.builder()
                .embeddingStore(redisEmbeddingStore)
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .embeddingModel(embeddingModel)
                .build();
    }
/**
 * 内容检索
 */
    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(redisEmbeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();
    }

    /**
     * 配置文章管理工具
     * 将ArticleTools中的@Tool方法注册为AI可调用的工具
     */
    @Bean
    public Map<String, ToolExecutor> articleToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();
        
        // 注册所有ArticleTools中的@Tool方法
        for (Method method : ArticleTools.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                String toolName = method.getName();
                executors.put(toolName, new DefaultToolExecutor(articleTools, method));
            }
        }
        
        return executors;
    }


}
