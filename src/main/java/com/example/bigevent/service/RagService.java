package com.example.bigevent.service;

import com.example.bigevent.domain.KnowledgeDoc;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import com.example.bigevent.domain.vo.rag.SearchResultVO;
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
    private final VectorStoreService vectorStoreService;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;
    private final RagAiService ragAiService;

    /**
     * 添加文本知识
     *
     * @param text   文本内容
     * @param bookId 关联图书ID，为 null 表示通用知识库
     */
    public void addKnowledge(String text, Long bookId) {
        log.info("开始添加文本知识，文本长度: {}, bookId: {}", text.length(), bookId);

        Document document = Document.from(text);
        List<TextSegment> segments = documentSplitter.split(document);
        var embeddings = embeddingModel.embedAll(segments).content();

        // 使用一个临时文件名的文档记录
        // 实际场景中可以把 content 存到 knowledge_doc 表
        // 这里简化：不创建 doc 记录，直接把片段作为通用知识存入 Redis
        // 后续完善可以统一走 KnowledgeDocService

        for (int i = 0; i < segments.size(); i++) {
            // 文本知识没有 MySQL chunk 记录，用负数或时间戳作为临时 chunkId
            // 生产环境建议也统一走 KnowledgeDocService
            long tempChunkId = System.currentTimeMillis() + i;
            vectorStoreService.saveChunk(
                tempChunkId,
                null,
                bookId,
                segments.get(i).text(),
                embeddings.get(i),
                null
            );
        }

        log.info("文本知识添加成功，共 {} 个片段", segments.size());
    }

    /**
     * 从上传的文件中添加知识
     */
    public KnowledgeDoc addKnowledgeFromFile(MultipartFile file, Long bookId, Integer createUser) throws IOException {
        log.info("开始处理文件: {}, bookId: {}", file.getOriginalFilename(), bookId);
        return knowledgeDocService.uploadAndProcess(file, bookId, createUser);
    }

    /**
     * RAG 问答
     *
     * @param query  用户问题
     * @param bookId 图书ID，为 null 时搜索通用知识库
     */
    public String ragChat(String query, Long bookId) {
        log.info("=== RAG查询开始 ===");
        log.info("查询内容: {}, bookId: {}", query, bookId);

        List<SearchResultVO> results = vectorStoreService.search(bookId, query, 5, 0.6);

        log.info("检索到 {} 条相关内容", results.size());

        if (results.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            return "抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。";
        }

        String knowledge = results.stream()
                .map(SearchResultVO::getContent)
                .collect(Collectors.joining("\n\n"));

        log.info("检索到的知识内容长度: {}", knowledge.length());

        String enhancedPrompt = "基于以下知识库内容回答问题：\n\n" +
                "知识库：\n" + knowledge + "\n\n" +
                "问题：" + query + "\n\n" +
                "请根据知识库内容回答，如果知识库中没有相关信息，请说明。";

        return ragAiService.chat(enhancedPrompt);
    }
}
