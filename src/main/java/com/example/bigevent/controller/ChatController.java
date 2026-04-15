package com.example.bigevent.controller;
import com.example.bigevent.service.AiArticleService;
import com.example.bigevent.service.Aiservice;
import com.example.bigevent.service.FluxAiservice;
import com.example.bigevent.service.RagService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


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
    //加文件
    @PostMapping("/rag/add")
    public String addKnowledge(@RequestParam String text) {
        ragService.addKnowledge(text);
        return "知识添加成功";
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