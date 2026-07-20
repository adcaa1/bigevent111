package com.example.bigevent.controller;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.AiConversation;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.Result;
import com.example.bigevent.domain.User;
import com.example.bigevent.domain.vo.rag.RagAnswerVO;
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
import java.util.Set;
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
    //    用户服务（获取部门信息）
    @Autowired
    private Userservice userservice;

    // AI 专用线程池，避免阻塞 Tomcat 主线程
    @Autowired
    @Qualifier("aiExecutor")
    private Executor aiExecutor;

    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of(
            "txt", "md", "pdf", "doc", "docx", "xls", "xlsx"
    );

    private Integer getCurrentUserId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return claims == null ? null : (Integer) claims.get("id");
    }

    /**
     * 获取当前用户所属部门ID
     */
    private Integer getCurrentUserDepartmentId() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null) return null;
        String username = (String) claims.get("username");
        if (username == null) return null;
        User user = userservice.findid(username);
        return user != null ? user.getDepartmentId() : null;
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

    @Autowired
    private AiChatOrchestratorService aiChatOrchestratorService;

    @Autowired
    private AiConversationService aiConversationService;

    /**
     * 通用 AI 流式聊天。
     * <p>
     * 未传 conversationId 时会自动创建新会话；传入有效 conversationId 时复用已有会话。
     *
     * @param userId         用户 ID
     * @param conversationId 可选的会话 ID
     * @param message        用户问题
     * @return 流式 AI 回复
     */
    @GetMapping(value = "/chat/stream", produces = "text/plain;charset=utf-8")
    public Flux<String> streamChat(@RequestParam Integer userId,
                                   @RequestParam(required = false) String conversationId,
                                   @RequestParam String message) {
        return aiChatOrchestratorService.chatStream(userId, conversationId, message);
    }

    /**
     * 添加知识：支持文本或文件上传
     *
     * @param text       文本内容
     * @param file       上传文件
     * @param visibility 可见性：0-私有 1-部门 2-公共，默认私有
     */
    @PostMapping("/rag/add")
    public Result<String> addKnowledge(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false, defaultValue = "0") Integer visibility) {
        try {
            Integer currentUserId = getCurrentUserId();
            Integer departmentId = getCurrentUserDepartmentId();

            // 部门可见性必须关联有效部门
            if (visibility != null && visibility == KnowledgeConstants.Visibility.DEPARTMENT && departmentId == null) {
                return Result.error("当前用户未分配部门，无法上传部门可见文档，请选择私有或公共");
            }

            if (file != null && !file.isEmpty()) {
                if (file.getSize() > KnowledgeConstants.MAX_FILE_SIZE) {
                    return Result.error("上传文件过大，文件大小不能超过10MB，当前文件大小: " +
                            String.format("%.2f", file.getSize() / 1024.0 / 1024.0) + "MB。请上传较小的文件");
                }

                String fileName = file.getOriginalFilename();
                String fileType = getFileType(fileName);

                // 校验文件类型，避免异步解析阶段才发现不支持
                if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
                    return Result.error("不支持此格式");
                }

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

                final Integer finalDepartmentId = departmentId;
                // 文件解析耗时，放到线程池异步处理，立即返回提示
                aiExecutor.execute(() -> {
                    try {
                        ragService.processUploadedFile(relativePath, fileName, fileType,
                                file.getSize(), fileMd5, currentUserId, visibility, finalDepartmentId);
                    } catch (Exception e) {
                        log.error("文件解析失败: {}", fileName, e);
                    }
                });
                return Result.success("文件已提交后台处理: " + fileName + "，请稍后查询知识库");
            } else if (text != null && !text.isEmpty()) {
                ragService.addKnowledge(text, currentUserId, visibility, departmentId);
                return Result.success("文本知识添加成功");
            } else {
                return Result.error("请提供文本内容或上传文件");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * RAG 问答（同步）
     *
     * @param question       用户问题
     * @param docId          文档ID，为空时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     */
    @GetMapping("/rag/chat")
    public CompletableFuture<RagAnswerVO> ragChat(@RequestParam String question,
                                                  @RequestParam(required = false) Long docId,
                                                  @RequestParam(required = false) String conversationId) {
        Integer currentUserId = getCurrentUserId();
        Integer departmentId = getCurrentUserDepartmentId();
        return CompletableFuture.supplyAsync(() -> ragService.ragChat(question, currentUserId, departmentId, docId, conversationId), aiExecutor);
    }

    /**
     * RAG Agent 聊天：通过自然语言操作图书（文章）。
     *
     * @param message        用户自然语言指令
     * @param conversationId 可选的会话 ID
     */
    @PostMapping("/rag/agent/chat")
    public Result<String> ragAgentChat(@RequestParam String message,
                                       @RequestParam(required = false) String conversationId) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        String answer = ragService.agentChat(message, currentUserId, conversationId);
        return Result.success(answer);
    }

    /**
     * RAG 问答（流式输出）
     *
     * @param question       用户问题
     * @param docId          文档ID，为空时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     */
    @GetMapping(value = "/rag/chat/stream", produces = "text/plain;charset=utf-8")
    public Flux<String> ragChatStream(@RequestParam String question,
                                      @RequestParam(required = false) Long docId,
                                      @RequestParam(required = false) String conversationId) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Flux.error(new IllegalArgumentException("请先登录"));
        }
        Integer departmentId = getCurrentUserDepartmentId();
        return ragService.ragChatStream(question, currentUserId, departmentId, docId, conversationId)
                .subscribeOn(reactor.core.scheduler.Schedulers.fromExecutor(aiExecutor));
    }

    /**
     * 查询知识库文档列表。
     * <p>
     * 只返回当前用户有权限查看的文档：自己上传的 + 同部门可见 + 公共可见。
     */
    @GetMapping("/rag/docs")
    public Result<List<KnowledgeDoc>> listDocs() {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        Integer departmentId = getCurrentUserDepartmentId();
        List<KnowledgeDoc> docs = knowledgeDocService.findAuthorizedDocs(currentUserId, departmentId);
        return Result.success(docs);
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/rag/doc/{id}")
    public Result<String> deleteDoc(@PathVariable Long id) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        knowledgeDocService.deleteDoc(id, currentUserId);
        return Result.success("删除成功");
    }

    /**
     * 重新处理单篇文档
     */
    @PostMapping("/rag/doc/{id}/reprocess")
    public Result<String> reprocessDoc(@PathVariable Long id) {
        Integer currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return Result.error("请先登录");
        }
        knowledgeDocService.reprocessDoc(id, currentUserId);
        return Result.success("重新处理任务已提交");
    }

    /**
     * AI 文章管理工具聊天（流式输出）。
     * <p>
     * 保留 LangChain4j 工具执行能力，会话由 conversationId 隔离。
     *
     * @param userId         用户 ID
     * @param conversationId 可选的会话 ID
     * @param message        用户问题
     * @return 流式 AI 回复
     */
    @GetMapping(value = "/chat/article", produces = "text/plain;charset=utf-8")
    public Flux<String> chatWithArticleTools(@RequestParam Integer userId,
                                             @RequestParam(required = false) String conversationId,
                                             @RequestParam String message) {
        return aiChatOrchestratorService.chatWithArticleTools(userId, conversationId, message);
    }

    /**
     * 创建新的 AI 会话。
     *
     * @param title 可选的会话标题，为空时默认为"新对话"
     * @return 创建成功的会话信息
     */
    @PostMapping("/ai/conversations")
    public Result<AiConversation> createConversation(@RequestParam(required = false) String title) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        return Result.success(aiConversationService.createConversation(userId, title));
    }

    /**
     * 获取当前登录用户的 AI 会话列表，按最近更新时间倒序排列。
     *
     * @return 会话列表
     */
    @GetMapping("/ai/conversations")
    public Result<List<AiConversation>> listConversations() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        return Result.success(aiConversationService.listByUserId(userId));
    }

    /**
     * 重命名指定 AI 会话。
     * <p>
     * 只有会话的创建者才能操作。
     *
     * @param id    会话 ID
     * @param title 新标题
     * @return 操作结果
     */
    @PutMapping("/ai/conversations/{id}/title")
    public Result<String> updateConversationTitle(@PathVariable Long id, @RequestParam String title) {
        Integer userId = getCurrentUserId();
        AiConversation conversation = aiConversationService.findById(id);
        if (conversation == null) {
            return Result.error("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            return Result.error("无权操作该会话");
        }
        aiConversationService.updateTitle(id, title);
        return Result.success("重命名成功");
    }

    /**
     * 删除指定 AI 会话，并级联清理该会话关联的记忆数据。
     * <p>
     * 级联清理范围：Redis 短期记忆、ES 长期语义记忆。
     * 只有会话的创建者才能删除。
     *
     * @param id 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/ai/conversations/{id}")
    public Result<String> deleteConversation(@PathVariable Long id) {
        Integer userId = getCurrentUserId();
        AiConversation conversation = aiConversationService.findById(id);
        if (conversation == null) {
            return Result.error("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            return Result.error("无权删除该会话");
        }
        aiConversationService.deleteConversation(id);
        return Result.success("删除成功");
    }
}
