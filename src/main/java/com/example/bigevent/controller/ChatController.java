package com.example.bigevent.controller;

import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.*;
import com.example.bigevent.util.ThreadLocalUtil;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@RestController
public class ChatController {
    //   自带的ai
    @Autowired
    private OpenAiChatModel openAiChatModel;
    //    等待流
    @Autowired
    private Aiservice aiservice;
    //    流式输出
    @Autowired
    private FluxAiservice fluxAiservice;
    //    rag功能
    @Autowired
    private RagService ragService;
    //    AI文章管理服务（支持工具调用）
    @Autowired
    private AiArticleService aiArticleService;
    //    知识库文档管理
    @Autowired
    private KnowledgeDocService knowledgeDocService;

    // AI 专用线程池，避免阻塞 Tomcat 主线程
    @Autowired
    @Qualifier("aiExecutor")
    private Executor aiExecutor;

    private Integer getCurrentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return claims == null ? null : (Integer) claims.get("id");
    }

    //    自带的ai（异步，不阻塞 Tomcat 线程）
    @GetMapping("/chat")
    public CompletableFuture<String> chat(@RequestParam String message) {
        return CompletableFuture.supplyAsync(() -> openAiChatModel.chat(message), aiExecutor);
    }

    //    等待输出（异步）
    @GetMapping("/chat1")
    public CompletableFuture<String> chat1(@RequestParam String message) {
        return CompletableFuture.supplyAsync(() -> aiservice.chat(message), aiExecutor);
    }

    //    流式输出
    @GetMapping(value = "/chat/stream", produces = "text/plain;charset=utf-8")
    public Flux<String> streamChat(@RequestParam Long userId, @RequestParam String message) {
        return fluxAiservice.chat(userId, message);
    }

    /**
     * 添加知识：支持文本或文件上传
     *
     * @param text   文本内容
     * @param file   上传文件
     * @param bookId 关联图书ID，为空表示通用知识库
     */
    @PostMapping("/rag/add")
    public Result<String> addKnowledge(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long bookId) {
        try {
            Integer currentUserId = getCurrentUserId();

            if (file != null && !file.isEmpty()) {
                long maxSize = 5 * 1024 * 1024;
                if (file.getSize() > maxSize) {
                    return Result.error("上传文件过大，文件大小不能超过5MB，当前文件大小: " +
                            String.format("%.2f", file.getSize() / 1024.0 / 1024.0) + "MB。请上传较小的文件");
                }

                String fileName = file.getOriginalFilename();
                // 文件解析耗时，放到线程池异步处理，立即返回提示
                aiExecutor.execute(() -> {
                    try {
                        ragService.addKnowledgeFromFile(file, bookId, currentUserId);
                    } catch (IOException e) {
                        throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
                    }
                });
                return Result.success("文件已提交后台处理: " + fileName + "，请稍后查询知识库");
            } else if (text != null && !text.isEmpty()) {
                ragService.addKnowledge(text, bookId);
                return Result.success("文本知识添加成功");
            } else {
                return Result.error("请提供文本内容或上传文件");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * RAG 问答
     *
     * @param question 用户问题
     * @param bookId   图书ID，为空时搜索通用知识库
     */
    @GetMapping("/rag/chat")
    public CompletableFuture<String> ragChat(@RequestParam String question,
                                             @RequestParam(required = false) Long bookId) {
        return CompletableFuture.supplyAsync(() -> ragService.ragChat(question, bookId), aiExecutor);
    }

    /**
     * 查询某本书/通用知识库下的文档列表
     */
    @GetMapping("/rag/docs")
    public Result<List<KnowledgeDoc>> listDocs(@RequestParam(required = false) Long bookId) {
        List<KnowledgeDoc> docs;
        if (bookId == null) {
            docs = knowledgeDocService.findAllDocs();
        } else {
            docs = knowledgeDocService.findDocsByBookId(bookId);
        }
        return Result.success(docs);
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/rag/doc/{id}")
    public Result<String> deleteDoc(@PathVariable Long id) {
        knowledgeDocService.deleteDoc(id);
        return Result.success("删除成功");
    }

    /**
     * AI对话式文章管理（流式输出）
     */
    @GetMapping(value = "/chat/article", produces = "text/plain;charset=utf-8")
    public Flux<String> chatWithArticleTools(@RequestParam Long userId, @RequestParam String message) {
        return aiArticleService.chatWithArticleTools(userId, message);
    }
}
