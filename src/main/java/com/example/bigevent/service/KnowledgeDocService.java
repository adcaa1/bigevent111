package com.example.bigevent.service;

import com.example.bigevent.constant.KnowledgeConstants;
import com.example.bigevent.domain.KnowledgeChunk;
import com.example.bigevent.domain.KnowledgeDoc;
import com.example.bigevent.domain.dto.rag.ChunkEmbeddingDTO;
import com.example.bigevent.mapper.KnowledgeChunkMapper;
import com.example.bigevent.mapper.KnowledgeDocMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文档服务：负责文档解析、分块、向量化、持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocService {

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final VectorStoreService vectorStoreService;
    private final ElasticsearchKeywordService elasticsearchKeywordService;
    private final DocumentStorageService documentStorageService;
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;

    /**
     * 上传并处理文档（同步存储文件后处理）
     * 计算 MD5 → 查重 → 保存文件 → 解析
     */
    @Transactional
    public KnowledgeDoc uploadAndProcess(MultipartFile file, Long bookId, Integer createUser) throws IOException {
        validateFile(file);

        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);

        // 先计算 MD5 并查重，避免重复文件落盘产生孤儿文件
        String fileMd5 = documentStorageService.computeMd5(file);
        KnowledgeDoc existDoc = knowledgeDocMapper.findByFileMd5(fileMd5);
        if (existDoc != null) {
            throw new IllegalArgumentException("该文件已经上传，无需重复上传: " + existDoc.getFileName());
        }

        String relativePath = documentStorageService.store(file);
        return processStoredFile(relativePath, fileName, fileType, file.getSize(), fileMd5, bookId, createUser);
    }

    /**
     * 处理已存储的本地文件
     * <p>
     * 适用于 Controller 先同步落盘、再异步解析的场景，避免 MultipartFile 在异步线程中失效。
     */
    @Transactional
    public KnowledgeDoc processStoredFile(String relativePath, String fileName, String fileType,
                                          long fileSize, String fileMd5, Long bookId, Integer createUser) throws IOException {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setBookId(bookId);
        doc.setCreateUser(createUser);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFileUrl(relativePath);
        doc.setFileSize(fileSize);
        doc.setFileMd5(fileMd5);
        doc.setStatus(KnowledgeConstants.DocStatus.PROCESSING);
        doc.setVisibility(KnowledgeConstants.Visibility.PRIVATE);
        knowledgeDocMapper.insert(doc);

        try (InputStream is = documentStorageService.load(relativePath)) {
            String content = parseContent(fileType, is);
            return processDocContent(doc, content, fileName);
        }
    }

    /**
     * 创建并处理文本知识文档
     * <p>
     * 统一文本知识与文件知识的入口，都会生成 KnowledgeDoc 和 KnowledgeChunk 记录。
     */
    @Transactional
    public KnowledgeDoc createAndProcessTextDoc(String text, Long bookId, Integer createUser, Integer visibility) {
        String fileName = "文本知识_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".txt";

        // 文本内容按 MD5 查重
        String fileMd5 = md5(text);
        KnowledgeDoc existDoc = knowledgeDocMapper.findByFileMd5(fileMd5);
        if (existDoc != null) {
            throw new IllegalArgumentException("该文本内容已经添加，无需重复添加: " + existDoc.getFileName());
        }

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setBookId(bookId);
        doc.setCreateUser(createUser);
        doc.setFileName(fileName);
        doc.setFileType("txt");
        doc.setFileUrl(null);
        doc.setFileSize((long) text.getBytes(StandardCharsets.UTF_8).length);
        doc.setFileMd5(fileMd5);
        doc.setContent(text);
        doc.setStatus(KnowledgeConstants.DocStatus.PROCESSING);
        doc.setVisibility(visibility == null ? KnowledgeConstants.Visibility.PRIVATE : visibility);
        knowledgeDocMapper.insert(doc);

        return processDocContent(doc, text, fileName);
    }

    /**
     * 文档内容处理核心流程：分块 → Embedding → MySQL + Redis + ES
     * <p>
     * 事务一致性策略：
     * 1. 先完成所有 MySQL 插入（doc + chunks）
     * 2. 再批量写入 RedisSearch
     * 3. 再批量写入 Elasticsearch
     * 4. 最后更新 doc 状态为成功
     * <p>
     * 若 Redis/ES 写入成功后 MySQL 事务回滚，会清理 Redis/ES 残留数据，避免孤儿向量。
     */
    @Transactional
    public KnowledgeDoc processDocContent(KnowledgeDoc doc, String content, String title) {
        Long bookId = doc.getBookId();
        Integer userId = doc.getCreateUser();
        String fileName = doc.getFileName();

        doc.setContent(content);

        Document document = Document.from(content);
        List<TextSegment> segments = documentSplitter.split(document);
        log.info("文档 [{}] 解析完成，共 {} 个片段", fileName, segments.size());

        var embeddingResponse = embeddingModel.embedAll(segments);
        var embeddings = embeddingResponse.content();

        List<ChunkEmbeddingDTO> chunkEmbeddings = new ArrayList<>();
        boolean redisWritten = false;
        boolean esWritten = false;

        try {
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                String esDocId = "doc_" + doc.getId() + "_chunk_" + i;

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(doc.getId());
                chunk.setBookId(bookId);
                chunk.setContent(segment.text());
                chunk.setChunkIndex(i);
                chunk.setPageNum(extractPageNum(segment));
                chunk.setWordCount(segment.text().length());
                chunk.setEsDocId(esDocId);
                knowledgeChunkMapper.insert(chunk);

                ChunkEmbeddingDTO dto = new ChunkEmbeddingDTO();
                dto.setChunkId(chunk.getId());
                dto.setDocId(doc.getId());
                dto.setBookId(bookId);
                dto.setUserId(userId);
                dto.setVisibility(doc.getVisibility());
                dto.setTitle(title);
                dto.setContent(segment.text());
                dto.setEmbedding(embeddings.get(i));
                dto.setChunkIndex(i);
                dto.setPageNum(chunk.getPageNum());
                dto.setEsDocId(esDocId);
                chunkEmbeddings.add(dto);
            }

            if (!chunkEmbeddings.isEmpty()) {
                vectorStoreService.saveChunks(chunkEmbeddings);
                redisWritten = true;
            }

            if (!chunkEmbeddings.isEmpty()) {
                elasticsearchKeywordService.bulkIndexChunks(chunkEmbeddings);
                esWritten = true;
            }

            doc.setChunkCount(segments.size());
            doc.setStatus(KnowledgeConstants.DocStatus.SUCCESS);
            knowledgeDocMapper.update(doc);

            log.info("文档 [{}] 处理成功，存入 {} 个片段", fileName, segments.size());
            return doc;

        } catch (Exception e) {
            log.error("文档 [{}] 处理失败: {}", fileName, e.getMessage(), e);

            if (redisWritten) {
                log.warn("Redis 已写入但后续失败，清理 docId={} 的向量残留", doc.getId());
                vectorStoreService.deleteByDocId(doc.getId());
            }
            if (esWritten) {
                log.warn("ES 已写入但后续失败，清理 docId={} 的索引残留", doc.getId());
                elasticsearchKeywordService.deleteByDocId(doc.getId());
            }

            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文档，同时清理 MySQL、RedisSearch、Elasticsearch 和本地文件
     */
    @Transactional
    public void deleteDoc(Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.findById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        vectorStoreService.deleteByDocId(docId);
        elasticsearchKeywordService.deleteByDocId(docId);

        knowledgeChunkMapper.deleteByDocId(docId);
        knowledgeDocMapper.deleteById(docId);

        documentStorageService.delete(doc.getFileUrl());
    }

    /**
     * 查询某本书下的所有文档
     */
    public List<KnowledgeDoc> findDocsByBookId(Long bookId) {
        return knowledgeDocMapper.findByBookId(bookId);
    }

    /**
     * 查询所有文档
     */
    public List<KnowledgeDoc> findAllDocs() {
        return knowledgeDocMapper.findAll();
    }

    /**
     * 根据文件 MD5 查询文档
     */
    public KnowledgeDoc findByFileMd5(String fileMd5) {
        if (fileMd5 == null || fileMd5.isBlank()) {
            return null;
        }
        return knowledgeDocMapper.findByFileMd5(fileMd5);
    }

    /**
     * 重新处理某本书的所有文档
     * <p>
     * 每篇文档独立事务处理，避免单篇失败导致整批回滚。
     */
    public void reprocessBook(Long bookId) {
        List<KnowledgeDoc> docs = knowledgeDocMapper.findByBookId(bookId);
        if (docs == null || docs.isEmpty()) {
            return;
        }

        for (KnowledgeDoc doc : docs) {
            try {
                reprocessDoc(doc.getId());
            } catch (Exception e) {
                log.error("重新处理文档失败, docId={}: {}", doc.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 重新处理单篇文档
     */
    @Transactional
    public void reprocessDoc(Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.findById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        reprocessSingleDoc(doc);
    }

    private void reprocessSingleDoc(KnowledgeDoc doc) {
        String content;
        try {
            if (doc.getFileUrl() != null && !doc.getFileUrl().isBlank()) {
                try (InputStream is = documentStorageService.load(doc.getFileUrl())) {
                    content = parseContent(doc.getFileType(), is);
                }
            } else if (doc.getContent() != null) {
                content = doc.getContent();
            } else {
                throw new IllegalStateException("文档没有文件路径也没有内容，无法重新处理: " + doc.getId());
            }

            knowledgeChunkMapper.deleteByDocId(doc.getId());
            vectorStoreService.deleteByDocId(doc.getId());
            elasticsearchKeywordService.deleteByDocId(doc.getId());

            doc.setStatus(KnowledgeConstants.DocStatus.PROCESSING);
            doc.setFailReason(null);
            knowledgeDocMapper.update(doc);

            processDocContent(doc, content, doc.getFileName());
        } catch (Exception e) {
            String reason = "重新处理文档失败: " + e.getMessage();
            log.error(reason, e);

            doc.setStatus(KnowledgeConstants.DocStatus.FAILED);
            doc.setFailReason(reason);
            knowledgeDocMapper.update(doc);

            throw new RuntimeException(reason, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > KnowledgeConstants.MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("文件大小不能超过 5MB，当前: %.2fMB", file.getSize() / 1024.0 / 1024.0)
            );
        }
    }

    private String parseContent(String fileType, InputStream inputStream) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> new ApachePdfBoxDocumentParser().parse(inputStream).text();
            case "doc", "docx" -> new ApachePoiDocumentParser().parse(inputStream).text();
            case "txt", "md" -> new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        };
    }

    private String getFileType(String fileName) {
        if (fileName == null) throw new IllegalArgumentException("文件名不能为空");
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) throw new IllegalArgumentException("文件名缺少后缀: " + fileName);
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    private Integer extractPageNum(TextSegment segment) {
        String pageNum = segment.metadata().getString("page_number");
        if (pageNum != null) {
            try {
                return Integer.parseInt(pageNum);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(text.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
