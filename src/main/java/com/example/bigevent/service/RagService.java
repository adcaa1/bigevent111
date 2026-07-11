package com.example.bigevent.service;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.vo.rag.HybridResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 服务：负责知识入库与基于知识库的问答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final KnowledgeDocService knowledgeDocService;
    private final HybridSearchService hybridSearchService;
    private final RagAiService ragAiService;

    /**
     * 添加文本知识
     * <p>
     * 统一走 KnowledgeDoc → KnowledgeChunk → Embedding → Redis/ES 流程。
     *
     * @param text       文本内容
     * @param bookId     关联图书ID，为 null 表示通用知识库
     * @param createUser 上传用户ID
     * @param visibility 可见性：0-私有 1-团队 2-公共
     */
    public void addKnowledge(String text, Long bookId, Integer createUser, Integer visibility) {
        log.info("开始添加文本知识，文本长度: {}, bookId: {}, createUser: {}", text.length(), bookId, createUser);

        KnowledgeDoc doc = knowledgeDocService.createAndProcessTextDoc(text, bookId, createUser, visibility);

        log.info("文本知识添加成功，docId={}, 共 {} 个片段", doc.getId(), doc.getChunkCount());
    }

    /**
     * 简化版：默认私有可见性
     */
    public void addKnowledge(String text, Long bookId, Integer createUser) {
        addKnowledge(text, bookId, createUser, KnowledgeConstants.Visibility.PRIVATE);
    }

    /**
     * 从上传的文件中添加知识（文件已同步落盘）
     */
    public KnowledgeDoc processUploadedFile(String relativePath, String fileName, String fileType,
                                            long fileSize, String fileMd5, Long bookId, Integer createUser) throws IOException {
        log.info("开始处理已存储文件: {}, bookId: {}, createUser: {}", fileName, bookId, createUser);
        return knowledgeDocService.processStoredFile(relativePath, fileName, fileType, fileSize, fileMd5, bookId, createUser);
    }

    /**
     * 从上传的文件中添加知识（同步存储并处理）
     */
    public KnowledgeDoc addKnowledgeFromFile(MultipartFile file, Long bookId, Integer createUser) throws IOException {
        log.info("开始处理文件: {}, bookId: {}, createUser: {}", file.getOriginalFilename(), bookId, createUser);
        return knowledgeDocService.uploadAndProcess(file, bookId, createUser);
    }

    /**
     * RAG 问答
     *
     * @param query      用户问题
     * @param userId     当前用户ID，用于权限过滤
     * @param bookId     图书ID，为 null 时搜索通用知识库
     * @param docId      指定文档ID，为 null 时不限制
     */
    public String ragChat(String query, Integer userId, Long bookId, Long docId) {
        log.info("=== RAG查询开始 ===");
        log.info("查询内容: {}, userId: {}, bookId: {}, docId: {}", query, userId, bookId, docId);

        List<HybridResultVO> results = hybridSearchService.search(userId, bookId, docId, query, 5);

        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            return "抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。";
        }

        String knowledge = results.stream()
                .map(r -> {
                    String source = "【来源】" + (r.getTitle() == null ? "未知文档" : r.getTitle());
                    if (r.getPageNum() != null) {
                        source += " 第" + r.getPageNum() + "页";
                    }
                    return source + "\n" + r.getContent();
                })
                .collect(Collectors.joining("\n\n"));

        log.info("检索到的知识内容长度: {}", knowledge.length());

        String enhancedPrompt = "基于以下知识库内容回答问题：\n\n" +
                "知识库：\n" + knowledge + "\n\n" +
                "问题：" + query + "\n\n" +
                "不要直接复制原文。"+
                "请根据知识库内容回答，如果知识库中没有相关信息，请说明。";

        return ragAiService.chat(enhancedPrompt);
    }
}
