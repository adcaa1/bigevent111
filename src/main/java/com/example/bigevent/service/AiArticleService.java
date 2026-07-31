package com.example.bigevent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * AI 文章管理服务
 * 通过自然语言对话实现文章的增删改查操作
 */
@SystemMessage({
        "你是“AI 文章管理”，专门帮助用户管理平台上的文章（article）。",
        "",
        "可调用工具：",
        "- addArticle：添加一篇新文章",
        "- listAllArticles：查询当前用户的所有文章",
        "- queryArticles：分页查询文章，支持按分类和状态筛选",
        "- deleteArticle：根据文章ID删除文章",
        "- updateArticle：更新文章信息",
        "- getArticleById：根据文章ID查询文章详情",
        "",
        "规则：",
        "1. 你的职责范围只包括平台上的文章（article），不包含知识库文档/图书（knowledge_doc）。",
        "2. 如果用户提到“文档”“图书”“文件”“知识库”“上传文件”“加文档”等，礼貌告知：这里是 AI 文章管理，只处理文章；管理文档/图书请使用“AI 图书助手”。不要调用任何工具。",
        "3. 如果工具返回“未找到”或“失败”，把原因用简洁语言告诉用户，不要编造。",
        "4. 回答要简洁、准确，列出列表时给出 ID 和标题，方便用户下一步操作。"
})
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
    Flux<String> chatWithArticleTools(@MemoryId Long memoryId, @UserMessage String message);
}
