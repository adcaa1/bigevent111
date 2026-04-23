package com.example.bigevent.controller;
import com.example.bigevent.domain.Result;
import com.example.bigevent.service.AiArticleService;
import com.example.bigevent.service.Aiservice;
import com.example.bigevent.service.FluxAiservice;
import com.example.bigevent.service.RagService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;


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
//    自带的ai
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return openAiChatModel.chat( message);
    }
//    等待输出
    @GetMapping("/chat1")
    public String chat1(@RequestParam String message) {
        return aiservice.chat( message);
    }
//    流式输出
    @GetMapping(value = "/chat/stream", produces = "text/plain;charset=utf-8")
    public Flux<String> streamChat(@RequestParam Long userId,@RequestParam String message) {
        return fluxAiservice.chat(userId,message);
    }
    //加文件 - 支持文本和文件上传
    @PostMapping("/rag/add")
    public Result<String> addKnowledge(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file) {
        try {
            if (file != null && !file.isEmpty()) {
                // 检查文件大小（5MB = 5 * 1024 * 1024 bytes）
                long maxSize = 5 * 1024 * 1024;
                if (file.getSize() > maxSize) {
                    return Result.error("上传文件过大，文件大小不能超过5MB，当前文件大小: " + 
                            String.format("%.2f", file.getSize() / 1024.0 / 1024.0) + "MB。请上传较小的文件");
                }
                
                // 处理文件上传
                ragService.addKnowledgeFromFile(file);
                return Result.success("文件知识添加成功: " + file.getOriginalFilename());
            } else if (text != null && !text.isEmpty()) {
                // 处理文本输入
                ragService.addKnowledge(text);
                return Result.success("文本知识添加成功");
            } else {
                return Result.error("请提供文本内容或上传文件");
            }
        } catch (IOException e) {
            return Result.error("文件处理失败: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
//    rag功能
    @GetMapping("/rag/chat")
    public String ragChat(@RequestParam String question) {
        return ragService.ragChat(question);
    }
    
    /**
     * AI对话式文章管理（流式输出）
     * 用户可以通过自然语言指令进行文章的增删改查操作
     * 例如：
     * - "帮我添加一篇文章，标题是'测试文章'，内容是'这是测试内容'"
     * - "查询所有文章"
     * - "删除ID为1的文章"
     * - "更新ID为2的文章，标题改为'新标题'"
     */
    @GetMapping(value = "/chat/article", produces = "text/plain;charset=utf-8")
    public Flux<String> chatWithArticleTools(@RequestParam Long userId, @RequestParam String message) {
        return aiArticleService.chatWithArticleTools(userId, message);
    }
}