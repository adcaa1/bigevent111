package com.example.bigevent.service;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.vo.rag.HybridResultVO;
import com.example.bigevent.domain.vo.rag.RagAnswerVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 服务：负责知识入库与基于知识库的问答
 */
@Slf4j
@Service
public class RagService {

    private final KnowledgeDocService knowledgeDocService;
    private final HybridSearchService hybridSearchService;
    private final OpenAiChatModel openAiChatModel;
    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ArticleTools articleTools;

    public RagService(KnowledgeDocService knowledgeDocService,
                      HybridSearchService hybridSearchService,
                      OpenAiChatModel openAiChatModel,
                      StreamingChatModel streamingChatModel,
                      ChatMemoryProvider chatMemoryProvider,
                      ArticleTools articleTools) {
        this.knowledgeDocService = knowledgeDocService;
        this.hybridSearchService = hybridSearchService;
        this.openAiChatModel = openAiChatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.articleTools = articleTools;
    }

    /**
     * RAG Agent 聊天：通过自然语言操作图书（文章）。
     *
     * @param message        用户自然语言指令
     * @param userId         当前用户ID
     * @param conversationId 会话ID，为空时使用 userId 作为默认会话
     * @return AI 回答
     */
    public String agentChat(String message, Integer userId, String conversationId) {
        String memoryId = (conversationId == null || conversationId.isBlank())
                ? "agent:user:" + userId
                : "agent:" + conversationId;

        ChatMemory chatMemory = chatMemoryProvider.get(memoryId);

        RagAgentAssistant assistant = AiServices.builder(RagAgentAssistant.class)
                .chatModel(openAiChatModel)
                .tools(articleTools)
                .chatMemory(chatMemory)
                .build();

        return assistant.chat(message);
    }

    /**
     * 添加文本知识
     * <p>
     * 统一走 KnowledgeDoc → KnowledgeChunk → Embedding → Redis/ES 流程。
     *
     * @param text       文本内容
     * @param bookId     关联图书ID，为 null 表示通用知识库
     * @param createUser 上传用户ID
     * @param visibility 可见性：0-私有 1-部门 2-公共
     */
    public void addKnowledge(String text, Long bookId, Integer createUser, Integer visibility, Integer departmentId) {
        log.info("开始添加文本知识，文本长度: {}, bookId: {}, createUser: {}", text.length(), bookId, createUser);

        KnowledgeDoc doc = knowledgeDocService.createAndProcessTextDoc(text, bookId, createUser, visibility, departmentId);

        log.info("文本知识添加成功，docId={}, 共 {} 个片段", doc.getId(), doc.getChunkCount());
    }

    /**
     * 从上传的文件中添加知识（文件已同步落盘）
     *
     * @param visibility 可见性：0-私有 1-部门 2-公共
     */
    public KnowledgeDoc processUploadedFile(String relativePath, String fileName, String fileType,
                                            long fileSize, String fileMd5, Long bookId, Integer createUser,
                                            Integer visibility, Integer departmentId) throws IOException {
        log.info("开始处理已存储文件: {}, bookId: {}, createUser: {}, visibility: {}", fileName, bookId, createUser, visibility);
        return knowledgeDocService.processStoredFile(relativePath, fileName, fileType, fileSize, fileMd5, bookId, createUser, visibility, departmentId);
    }

    /**
     * RAG 问答
     *
     * @param query          用户问题
     * @param userId         当前用户ID，用于权限过滤
     * @param departmentId   当前用户部门ID，用于部门级可见性判断
     * @param bookId         图书ID，为 null 时搜索通用知识库
     * @param docId          指定文档ID，为 null 时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     */
    public RagAnswerVO ragChat(String query, Integer userId, Integer departmentId, Long bookId, Long docId, String conversationId) {
        log.info("=== RAG查询开始 ===");
        log.info("查询内容: {}, userId: {}, departmentId: {}, bookId: {}, docId: {}, conversationId: {}", query, userId, departmentId, bookId, docId, conversationId);

        List<HybridResultVO> results = hybridSearchService.search(userId, departmentId, bookId, docId, query, 5);

        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            RagAnswerVO emptyAnswer = new RagAnswerVO();
            emptyAnswer.setAnswer("抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。");
            emptyAnswer.setCitations(new ArrayList<>());
            return emptyAnswer;
        }

        String knowledge = buildKnowledgeWithCitation(results);
        log.info("检索到的知识内容长度: {}", knowledge.length());

        String history = buildHistoryContext(conversationId);
        String enhancedPrompt = buildRagPrompt(query, knowledge, results, history);
        // 直接调用 OpenAiChatModel.chat()，不走 @AiService，避免记忆污染
        String rawAnswer = openAiChatModel.chat(enhancedPrompt);

        RagAnswerVO answerVO = parseAnswerWithCitations(rawAnswer, results);
        saveToMemory(conversationId, query, answerVO.getAnswer());
        return answerVO;
    }

    /**
     * RAG 问答（流式输出）
     *
     * @param query          用户问题
     * @param userId         当前用户ID，用于权限过滤
     * @param departmentId   当前用户部门ID，用于部门级可见性判断
     * @param bookId         图书ID，为 null 时搜索通用知识库
     * @param docId          指定文档ID，为 null 时不限制
     * @param conversationId 会话ID，为空时不使用历史记忆
     * @return 流式 AI 回复
     */
    public Flux<String> ragChatStream(String query, Integer userId, Integer departmentId, Long bookId, Long docId, String conversationId) {
        log.info("=== RAG流式查询开始 ===");
        log.info("查询内容: {}, userId: {}, departmentId: {}, bookId: {}, docId: {}, conversationId: {}", query, userId, departmentId, bookId, docId, conversationId);

        List<HybridResultVO> results = hybridSearchService.search(userId, departmentId, bookId, docId, query, 5);

        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            return Flux.just("抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。");
        }

        String knowledge = buildKnowledgeWithCitation(results);
        String history = buildHistoryContext(conversationId);
        String enhancedPrompt = buildRagPrompt(query, knowledge, results, history);
        ChatMemory chatMemory = getChatMemory(conversationId);

        return Flux.create(sink -> {
            StringBuilder fullAnswer = new StringBuilder();
            streamingChatModel.chat(enhancedPrompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullAnswer.append(partialResponse);
                    sink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    sink.complete();
                    if (chatMemory != null) {
                        try {
                            chatMemory.add(UserMessage.from(query));
                            chatMemory.add(AiMessage.from(fullAnswer.toString()));
                        } catch (Exception e) {
                            log.error("保存 RAG 流式对话记忆失败", e);
                        }
                    }
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
     * 构建带引用编号的知识库内容
     */
    private String buildKnowledgeWithCitation(List<HybridResultVO> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            HybridResultVO r = results.get(i);
            String source = "【来源 " + (i + 1) + "】" + (r.getTitle() == null ? "未知文档" : r.getTitle());
            if (r.getPageNum() != null) {
                source += " 第" + r.getPageNum() + "页";
            }
            sb.append(source).append("\n").append(r.getContent());
            if (i < results.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建 RAG Prompt，要求 LLM 标注引用来源
     */
    private String buildRagPrompt(String query, String knowledge, List<HybridResultVO> results, String history) {
        StringBuilder citationList = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            HybridResultVO r = results.get(i);
            citationList.append("[^").append(i + 1).append("]: ")
                    .append(r.getTitle() == null ? "未知文档" : r.getTitle());
            if (r.getPageNum() != null) {
                citationList.append(" 第").append(r.getPageNum()).append("页");
            }
            citationList.append("\n");
        }

        String historyPart = "";
        if (history != null && !history.isBlank()) {
            historyPart = "\n\n历史对话：\n" + history;
        }

        return "你是一个基于知识库的智能助手。请根据以下资料回答问题，不要编造知识库中不存在的内容。\n\n" +
                "知识库：\n" + knowledge + historyPart + "\n\n" +
                "问题：" + query + "\n\n" +
                "引用来源：\n" + citationList + "\n" +
                "要求：\n" +
                "1. 只根据知识库内容回答，不要编造\n" +
                "2. 如果知识库中没有相关信息，请明确说明\n" +
                "3. 引用资料时用 [^1]、[^2] 等标注来源编号\n" +
                "4. 回答末尾列出引用来源\n" +
                "5. 不要直接复制原文，请总结回答";
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

        // 清理回答中的引用列表部分（从第一个 "[^1]:" 开头的行开始截断）
        String cleanAnswer = rawAnswer;
        int citationListStart = rawAnswer.indexOf("[^1]:");
        if (citationListStart > 0) {
            cleanAnswer = rawAnswer.substring(0, citationListStart).trim();
        }

        // 构建引用列表
        List<RagAnswerVO.Citation> citations = new ArrayList<>();
        for (Integer id : citationIds) {
            HybridResultVO r = results.get(id - 1);
            RagAnswerVO.Citation citation = new RagAnswerVO.Citation();
            citation.setId(id);
            citation.setTitle(r.getTitle() == null ? "未知文档" : r.getTitle());
            citation.setPageNum(r.getPageNum());
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
     * 根据 conversationId 获取 ChatMemory，未传入时返回 null
     */
    private ChatMemory getChatMemory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return chatMemoryProvider.get("rag:" + conversationId);
    }

    /**
     * 构建历史对话上下文文本，最多最近 5 轮 / 10 条消息
     */
    private String buildHistoryContext(String conversationId) {
        ChatMemory chatMemory = getChatMemory(conversationId);
        if (chatMemory == null) {
            return "";
        }
        List<ChatMessage> messages = chatMemory.messages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        int start = Math.max(0, messages.size() - 10);
        List<ChatMessage> recentMessages = messages.subList(start, messages.size());

        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : recentMessages) {
            if (message instanceof UserMessage) {
                sb.append("User: ").append(((UserMessage) message).singleText()).append("\n");
            } else if (message instanceof AiMessage) {
                sb.append("Assistant: ").append(((AiMessage) message).text()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 将当前轮次 QA 保存到 ChatMemory
     */
    private void saveToMemory(String conversationId, String query, String answer) {
        ChatMemory chatMemory = getChatMemory(conversationId);
        if (chatMemory == null) {
            return;
        }
        try {
            chatMemory.add(UserMessage.from(query));
            chatMemory.add(AiMessage.from(answer));
        } catch (Exception e) {
            log.error("保存 RAG 对话记忆失败", e);
        }
    }
}

