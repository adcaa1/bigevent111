package com.example.bigevent.controller;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.*;
import com.example.bigevent.util.ThreadLocalUtil;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Slf4j
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
    //    本地文档存储
    @Autowired
    private DocumentStorageService documentStorageService;

    // AI 专用线程池，避免阻塞 Tomcat 主线程
    @Autowired
    @Qualifier("aiExecutor")
    private Executor aiExecutor;

    private Integer getCurrentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return claims == null ? null : (Integer) claims.get("id");
    }

    private String getFileType(String fileName) {
        if (fileName == null) throw new IllegalArgumentException("文件名不能为空");
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) throw new IllegalArgumentException("文件名缺少后缀: " + fileName);
        return fileName.substring(lastDot + 1).toLowerCase();
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
     * @param text       文本内容
     * @param file       上传文件
     * @param bookId     关联图书ID，为空表示通用知识库
     * @param visibility 可见性：0-私有 1-团队 2-公共，默认私有
     */
    @PostMapping("/rag/add")
    public Result<String> addKnowledge(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false, defaultValue = "0") Integer visibility) {
        try {
            Integer currentUserId = getCurrentUserId();

            if (file != null && !file.isEmpty()) {
                if (file.getSize() > KnowledgeConstants.MAX_FILE_SIZE) {
                    return Result.error("上传文件过大，文件大小不能超过5MB，当前文件大小: " +
                            String.format("%.2f", file.getSize() / 1024.0 / 1024.0) + "MB。请上传较小的文件");
                }

                String fileName = file.getOriginalFilename();
                String fileType = getFileType(fileName);

                // 先计算 MD5 并查重，避免重复文件落盘产生孤儿文件
                final String fileMd5;
                try {
                    fileMd5 = documentStorageService.computeMd5(file);
                } catch (IOException e) {
                    log.error("计算文件 MD5 失败: {}", fileName, e);
                    return Result.error("文件校验失败: " + e.getMessage());
                }

                KnowledgeDoc existDoc = knowledgeDocService.findByFileMd5(fileMd5);
                if (existDoc != null) {
                    return Result.error("该文件已经上传，无需重复上传: " + existDoc.getFileName());
                }

                // 同步落盘，避免 MultipartFile 在异步线程中失效
                final String relativePath;
                try {
                    relativePath = documentStorageService.store(file);
                } catch (IOException e) {
                    log.error("文件落盘失败: {}", fileName, e);
                    return Result.error("文件保存失败: " + e.getMessage());
                }

                // 文件解析耗时，放到线程池异步处理，立即返回提示
                aiExecutor.execute(() -> {
                    try {
                        ragService.processUploadedFile(relativePath, fileName, fileType,
                                file.getSize(), fileMd5, bookId, currentUserId);
                    } catch (IOException e) {
                        throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
                    }
                });
                return Result.success("文件已提交后台处理: " + fileName + "，请稍后查询知识库");
            } else if (text != null && !text.isEmpty()) {
                ragService.addKnowledge(text, bookId, currentUserId, visibility);
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
     * @param docId    文档ID，为空时不限制
     */
    @GetMapping("/rag/chat")
    public CompletableFuture<String> ragChat(@RequestParam String question,
                                             @RequestParam(required = false) Long bookId,
                                             @RequestParam(required = false) Long docId) {
        Integer currentUserId = getCurrentUserId();
        return CompletableFuture.supplyAsync(() -> ragService.ragChat(question, currentUserId, bookId, docId), aiExecutor);
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
     * 重新处理单篇文档
     */
    @PostMapping("/rag/doc/{id}/reprocess")
    public Result<String> reprocessDoc(@PathVariable Long id) {
        knowledgeDocService.reprocessDoc(id);
        return Result.success("重新处理任务已提交");
    }

    /**
     * 重新处理某本书下的所有文档
     */
    @PostMapping("/rag/book/{bookId}/reprocess")
    public Result<String> reprocessBook(@PathVariable Long bookId) {
        aiExecutor.execute(() -> knowledgeDocService.reprocessBook(bookId));
        return Result.success("重新处理任务已提交后台执行");
    }

    /**
     * AI对话式文章管理（流式输出）
     */
    @GetMapping(value = "/chat/article", produces = "text/plain;charset=utf-8")
    public Flux<String> chatWithArticleTools(@RequestParam Long userId, @RequestParam String message) {
        return aiArticleService.chatWithArticleTools(userId, message);
    }
}
