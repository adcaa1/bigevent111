package com.example.bigevent.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    @Autowired
    private EmbeddingStoreIngestor ingestor;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private RagAiService ragAiService;  // 使用RAG专用的AI服务（不带tools）

    /**
     * 添加文本知识
     */
    public void addKnowledge(String text) {
        log.info("开始添加文本知识，文本长度: {}", text.length());
        Document document = Document.from(text);
        ingestor.ingest(document);
        log.info("文本知识添加成功");
    }

    /**
     * 从上传的文件中添加知识
     */
    public void addKnowledgeFromFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        log.info("开始处理文件: {}, 大小: {} bytes", fileName, file.getSize());

        // 检查文件大小（5MB）
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(String.format(
                "上传文件过大，文件大小不能超过5MB，当前文件大小: %.2fMB",
                file.getSize() / 1024.0 / 1024.0
            ));
        }

        Document document;
        // 根据文件类型选择不同的解析器
        if (fileName.toLowerCase().endsWith(".pdf")) {
            log.info("解析PDF文件: {}", fileName);
            ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
            document = pdfParser.parse(file.getInputStream());
        } else if (fileName.toLowerCase().endsWith(".docx") || fileName.toLowerCase().endsWith(".doc")) {
            log.info("解析Word文件: {}", fileName);
            ApachePoiDocumentParser wordParser = new ApachePoiDocumentParser();
            document = wordParser.parse(file.getInputStream());
        } else if (fileName.toLowerCase().endsWith(".txt")) {
            log.info("读取TXT文件: {}", fileName);
            // 对于txt文件，直接读取文本内容
            String content = new String(file.getBytes(), "UTF-8");
            document = Document.from(content);
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName + "，仅支持PDF、Word(.doc/.docx)、TXT格式");
        }

        log.info("文档解析完成，准备存入向量数据库...");
        try {
            ingestor.ingest(document);
            log.info("文件知识添加成功: {}", fileName);
        } catch (Exception e) {
            log.error("存入向量数据库失败: {}", e.getMessage(), e);
            throw new RuntimeException("知识入库失败: " + e.getMessage(), e);
        }
    }

    public String ragChat(String query) {
        log.info("=== RAG查询开始 ===");
        log.info("查询内容: {}", query);
        log.info("使用的AI服务: {}", ragAiService.getClass().getName());
        
        List<Content> contents = contentRetriever.retrieve(Query.from(query));
        
        log.info("检索到 {} 条相关内容", contents.size());
        
        if (contents.isEmpty()) {
            log.warn("未检索到相关知识库内容");
            return "抱歉，知识库中没有找到与您的问题相关的信息。请先上传相关文档或添加知识内容。";
        }

        String knowledge = contents.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));
        
        log.info("检索到的知识内容长度: {}", knowledge.length());

        String enhancedPrompt = "基于以下知识库内容回答问题：\n\n" +
                "知识库：\n" + knowledge + "\n\n" +
                "问题：" + query + "\n\n" +
                "请根据知识库内容回答，如果知识库中没有相关信息，请说明。";

        return ragAiService.chat(enhancedPrompt);
    }
}
