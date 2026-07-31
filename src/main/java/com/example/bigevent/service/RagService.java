package com.example.bigevent.service;

import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.vo.rag.HybridResultVO;
import com.example.bigevent.domain.vo.rag.RagAnswerVO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.bigevent.service.RagService.QueryType.*;

/**
 * RAG 服务：负责知识入库与基于知识库的问答。
 * <p>
 * 问答侧采用分层上下文管理：
 * <ul>
 *     <li>每轮根据当前问题重新召回 Top5 知识片段，不沿用上一轮知识库</li>
 *     <li>历史对话只保留最近 6 轮，更早内容自动总结为摘要</li>
 *     <li>Prompt 中不再把知识库原文拼进历史记录，避免知识被重复复制</li>
 * </ul>
 */
@Slf4j
@Service
public class RagService {

    private static final String RAG_SYSTEM_PROMPT = "你是一个基于知识库的智能助手，请根据提供的知识库片段回答用户问题。";

    private final KnowledgeDocService knowledgeDocService;
    private final HybridSearchService hybridSearchService;
    private final OpenAiChatModel openAiChatModel;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ConversationContextService conversationContextService;
    private final PromptBuilder promptBuilder;
    private final BookTools bookTools;

    public RagService(KnowledgeDocService knowledgeDocService,
                      HybridSearchService hybridSearchService,
                      OpenAiChatModel openAiChatModel,
                      StreamingChatModel streamingChatModel,
                      ChatMemoryProvider chatMemoryProvider,
                      ConversationContextService conversationContextService,
                      PromptBuilder promptBuilder,
                      BookTools bookTools) {
        this.knowledgeDocService = knowledgeDocService;
        this.hybridSearchService = hybridSearchService;
        this.openAiChatModel = openAiChatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.conversationContextService = conversationContextService;
        this.promptBuilder = promptBuilder;
        this.bookTools = bookTools;
    }

    /**
     * RAG Agent 聊天：通过自然语言操作图书/文档。
     *
     * @param message        用户自然语言指令
     * @param userId         当前用户ID
     * @param departmentId   当前用户部门ID
     * @param conversationId 会话ID，为空时不保留历史记忆
     * @return AI 回答
     */
    public String agentChat(String message, Integer userId, Integer departmentId, String conversationId) {
        return buildAssistant(userId, departmentId, conversationId).chat(message);
    }

    /**
     * RAG Agent 聊天（流式输出）：通过自然语言操作图书/文档。
     *
     * @param message        用户自然语言指令
     * @param userId         当前用户ID
     * @param departmentId   当前用户部门ID
     * @param conversationId 会话ID，为空时不保留历史记忆
     * @return 流式 AI 回答
     */
    public Flux<String> agentChatStream(String message, Integer userId, Integer departmentId, String conversationId) {
        return Flux.<String>create(sink -> {
                    try {
                        RagAgentAssistant assistant = buildAssistant(userId, departmentId, conversationId);
                        assistant.chatStream(message)
                                .doOnNext(sink::next)
                                .doOnError(error -> {
                                    log.error("AI 图书助手流式响应内部失败, userId={}, message={}", userId, message, error);
                                    sink.error(error);
                                })
                                .doOnComplete(sink::complete)
                                .subscribe();
                    } catch (Exception e) {
                        log.error("AI 图书助手启动流式响应失败, userId={}, message={}", userId, message, e);
                        sink.error(e);
                    }
                }, reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER)
                .timeout(Duration.ofSeconds(25),
                        Flux.just("\n【系统错误】响应超时，请稍后重试。"));
    }

    /**
     * 构建挂载了图书工具的 Agent Assistant，同时配置同步/流式模型。
     * <p>
     * BookTools 本身是 Spring 单例 Bean，通过 {@link dev.langchain4j.agent.tool.ToolMemoryId}
     * 在运行时获取 conversationId，再查询会话得到用户上下文，无需每次 new 实例。
     */
    private RagAgentAssistant buildAssistant(Integer userId, Integer departmentId, String conversationId) {
        var builder = AiServices.builder(RagAgentAssistant.class)
                .chatModel(openAiChatModel)
                .streamingChatModel(streamingChatModel)
                .tools(bookTools);

        // 只有在传了 conversationId 时才挂载记忆，避免所有无会话的 Agent 聊天共用同一份历史
        if (conversationId != null && !conversationId.isBlank()) {
            ChatMemory chatMemory = chatMemoryProvider.get("agent:" + conversationId);
            builder.chatMemory(chatMemory);
        }

        return builder.build();
    }

    /**
     * 添加文本知识
     * <p>
     * 统一走 KnowledgeDoc → KnowledgeChunk → Embedding → Redis/ES 流程。
     *
     * @param text       文本内容
     * @param createUser 上传用户ID
     * @param visibility 可见性：0-私有 1-部门 2-公共
     */
    public void addKnowledge(String text, Integer createUser, Integer visibility, Integer departmentId) {
        log.info("开始添加文本知识，文本长度: {}, createUser: {}", text.length(), createUser);

        KnowledgeDoc doc = knowledgeDocService.createAndProcessTextDoc(text, createUser, visibility, departmentId);

        log.info("文本知识添加成功，docId={}, 共 {} 个片段", doc.getId(), doc.getChunkCount());
    }

    /**
     * 从上传的文件中添加知识（文件已同步落盘）
     *
     * @param visibility 可见性：0-私有 1-部门 2-公共
     */
    public KnowledgeDoc processUploadedFile(String relativePath, String fileName, String fileType,
                                            long fileSize, String fileMd5, Integer createUser,
                                            Integer visibility, Integer departmentId) throws IOException {
        log.info("开始处理已存储文件: {}, createUser: {}, visibility: {}", fileName, createUser, visibility);
        return knowledgeDocService.processStoredFile(relativePath, fileName, fileType, fileSize, fileMd5, createUser, visibility, departmentId);
    }

    /**
     * RAG 问答
     *
     * @param query          用户问题
     * @param userId         当前用户ID，用于权限过滤
     * @param departmentId   当前用户部门ID，用于部门级可见性判断
     * @param docId          指定文档ID，为 null 时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     */
    public RagAnswerVO ragChat(String query, Integer userId, Integer departmentId, Long docId, String conversationId) {
        log.info("=== RAG查询开始 ===");
        log.info("查询内容: {}, userId: {}, departmentId: {}, docId: {}, conversationId: {}", query, userId, departmentId, docId, conversationId);

        String convId = normalizeConversationId(conversationId);

        // 重复问题直接走缓存，避免重复 Embedding + 检索 + LLM 调用
        String cachedAnswer = conversationContextService.getCachedAnswerIfRepeated(convId, query);
        if (cachedAnswer != null) {
            log.info("命中重复问题缓存，直接返回答案");
            RagAnswerVO cachedVO = new RagAnswerVO();
            cachedVO.setAnswer(cachedAnswer);
            cachedVO.setCitations(new ArrayList<>());
            return cachedVO;
        }

        List<HybridResultVO> results = hybridSearchService.search(userId, departmentId, docId, query, determineTopK(query));
        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            RagAnswerVO emptyAnswer = new RagAnswerVO();
            emptyAnswer.setAnswer("抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。");
            emptyAnswer.setCitations(new ArrayList<>());
            return emptyAnswer;
        }

        List<String> ragChunks = buildRagChunks(results);
        log.info("检索到的知识内容共 {} 个片段", ragChunks.size());

        ConversationContext context = conversationContextService.load(convId);
        log.info("RAG 上下文加载完成: conversationId={}, recentMessages={}, summaryLength={}",
                convId, context.recentMessages().size(), context.summary().length());

        List<ChatMessage> promptMessages = promptBuilder.build(
                RAG_SYSTEM_PROMPT, null, context.summary(), context.recentMessages(), ragChunks, query);

        String rawAnswer = openAiChatModel.chat(promptMessages).aiMessage().text();
        RagAnswerVO answerVO = parseAnswerWithCitations(rawAnswer, results);

        // 只保存最终回答，不保存知识库原文，防止下一轮 Prompt 把知识库又拼进来
        conversationContextService.saveInteraction(convId, query, answerVO.getAnswer());
        return answerVO;
    }

    /**
     * RAG 问答（流式输出）
     *
     * @param query          用户问题
     * @param userId         当前用户ID，用于权限过滤
     * @param departmentId   当前用户部门ID，用于部门级可见性判断
     * @param docId          指定文档ID，为 null 时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     * @return 流式 AI 回复
     */
    public Flux<String> ragChatStream(String query, Integer userId, Integer departmentId, Long docId, String conversationId) {
        log.info("=== RAG流式查询开始 ===");
        log.info("查询内容: {}, userId: {}, departmentId: {}, docId: {}, conversationId: {}", query, userId, departmentId, docId, conversationId);

        String convId = normalizeConversationId(conversationId);

        String cachedAnswer = conversationContextService.getCachedAnswerIfRepeated(convId, query);
        if (cachedAnswer != null) {
            log.info("命中重复问题缓存，直接返回答案");
            return Flux.just(cachedAnswer);
        }

        List<HybridResultVO> results = hybridSearchService.search(userId, departmentId, docId, query, determineTopK(query));
        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            return Flux.just("抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。");
        }

        List<String> ragChunks = buildRagChunks(results);
        ConversationContext context = conversationContextService.load(convId);
        log.info("RAG 上下文加载完成: conversationId={}, recentMessages={}, summaryLength={}",
                convId, context.recentMessages().size(), context.summary().length());

        List<ChatMessage> promptMessages = promptBuilder.build(
                RAG_SYSTEM_PROMPT, null, context.summary(), context.recentMessages(), ragChunks, query);

        return Flux.create(sink -> {
            StringBuilder fullAnswer = new StringBuilder();
            streamingChatModel.chat(promptMessages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullAnswer.append(partialResponse);
                    sink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    sink.complete();
                    conversationContextService.saveInteraction(convId, query, fullAnswer.toString());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("RAG 流式响应失败", error);
                    sink.error(error);
                }
            });
        });
    }

    /**
     * 构建带引用编号的知识库片段列表。
     * <p>
     * 每个片段格式为：[^n] 标题 第x页\n内容，与 PromptBuilder 中的来源列表保持一致。
     */
    private List<String> buildRagChunks(List<HybridResultVO> results) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            HybridResultVO r = results.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append("[^").append(i + 1).append("] ")
                    .append(r.getTitle() == null ? "未知文档" : r.getTitle());
            if (r.getPageNum() != null) {
                sb.append(" 第").append(r.getPageNum()).append("页");
            }
            sb.append("\n").append(r.getContent());
            chunks.add(sb.toString());
        }
        return chunks;
    }

    /**
     * 解析 LLM 返回的回答，提取引用编号并构建 RagAnswerVO
     */
    private RagAnswerVO parseAnswerWithCitations(String rawAnswer, List<HybridResultVO> results) {
        RagAnswerVO answerVO = new RagAnswerVO();

        // 提取引用编号，如 [^1] [^2]
        Pattern pattern = Pattern.compile("\\[\\^(\\d+)\\]");
        Matcher matcher = pattern.matcher(rawAnswer);
        Set<Integer> citationIds = new LinkedHashSet<>();
        while (matcher.find()) {
            try {
                int id = Integer.parseInt(matcher.group(1));
                if (id >= 1 && id <= results.size()) {
                    citationIds.add(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // 清理回答末尾的引用列表部分：匹配任意 [^n]: 开头的连续引用行区块
        String cleanAnswer = rawAnswer;
        Pattern citationListPattern = Pattern.compile(
                "(?:^|\\n)\\[\\^\\d+\\]:.*(?:\\r?\\n\\[\\^\\d+\\]:.*)*",
                Pattern.DOTALL
        );
        Matcher listMatcher = citationListPattern.matcher(rawAnswer);
        if (listMatcher.find()) {
            int start = listMatcher.start();
            if (start > 0) {
                cleanAnswer = rawAnswer.substring(0, start).trim();
            }
        }

        // 构建引用列表
        List<RagAnswerVO.Citation> citations = new ArrayList<>();
        for (Integer id : citationIds) {
            HybridResultVO r = results.get(id - 1);
            RagAnswerVO.Citation citation = new RagAnswerVO.Citation();
            citation.setId(id);
            citation.setTitle(r.getTitle() == null ? "未知文档" : r.getTitle());
            citation.setPageNum(r.getPageNum());
            citation.setChunkIndex(r.getChunkIndex());
            citation.setContent(truncateContent(r.getContent(), 200));
            citations.add(citation);
        }

        answerVO.setAnswer(cleanAnswer);
        answerVO.setCitations(citations);
        return answerVO;
    }

    /**
     * 截断内容，用于引用摘要展示
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 统一 RAG 会话 ID 前缀，避免与普通 AI 会话冲突。
     */
    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return "rag:" + conversationId;
    }

    // ==================== 动态检索量 ====================

    /** 默认检索量（普通问答） */
    private static final int DEFAULT_TOP_K = 8;
    /** 枚举类问题检索量（"有哪些"、"列出所有"等） */
    private static final int ENUMERATION_TOP_K = 20;

    /** 问题类型 */
    enum QueryType {
        /** 枚举/列表类：有哪些、列出、全部、多少、几个 */
        ENUMERATION,
        /** 普通问答 */
        GENERAL
    }

    /**
     * 根据问题意图判断检索量。
     * <p>
     * 枚举类问题（"有哪些手机"、"列出所有"）需要更大窗口才能覆盖更多结果；
     * 普通问答（"iPhone 15 的屏幕尺寸"）用默认量即可。
     */
    private int determineTopK(String query) {
        return classifyQuery(query) == ENUMERATION ? ENUMERATION_TOP_K : DEFAULT_TOP_K;
    }

    /**
     * 判断问题类型。
     */
    static QueryType classifyQuery(String query) {
        if (query == null || query.isBlank()) {
            return GENERAL;
        }
        // 枚举/列表类特征词
        if (query.matches(".*(有哪些|列出|所有|全部|多少|几个|哪些|列举|都有什么|都有哪些).*")) {
            return ENUMERATION;
        }
        return GENERAL;
    }
}
