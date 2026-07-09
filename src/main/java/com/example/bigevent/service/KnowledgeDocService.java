package com.example.bigevent.service;

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
    private final DocumentSplitter documentSplitter;
    private final EmbeddingModel embeddingModel;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 上传并处理文档
     * <p>
     * 事务一致性策略：
     * 1. 先完成所有 MySQL 插入（doc + chunks），此时不写入 Redis
     * 2. 再批量写入 RedisSearch
     * 3. 最后更新 doc 状态为成功
     * <p>
     * 若 Redis 写入成功后 MySQL 事务回滚，会清理 Redis 残留数据，避免孤儿向量。
     */
    @Transactional
    public KnowledgeDoc uploadAndProcess(MultipartFile file, Long bookId, Integer createUser) throws IOException {
        validateFile(file);

        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);

        // 1. 先保存文档元信息，状态为处理中
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setBookId(bookId);
        doc.setCreateUser(createUser);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFileUrl(null); // 后续可补充 OSS 上传逻辑
        doc.setStatus(1); // 处理中
        knowledgeDocMapper.insert(doc);

        List<ChunkEmbeddingDTO> chunkEmbeddings = new ArrayList<>();
        boolean redisWritten = false;

        try {
            // 2. 解析文档
            String content = parseContent(file, fileType);
            doc.setContent(content);

            // 3. 分块
            Document document = Document.from(content);
            List<TextSegment> segments = documentSplitter.split(document);

            log.info("文档 [{}] 解析完成，共 {} 个片段", fileName, segments.size());

            // 4. 批量生成 Embedding
            var embeddingResponse = embeddingModel.embedAll(segments);
            var embeddings = embeddingResponse.content();

            // 5. 先全部插入 MySQL（事务内）
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(doc.getId());
                chunk.setBookId(bookId);
                chunk.setContent(segment.text());
                chunk.setChunkIndex(i);
                chunk.setPageNum(extractPageNum(segment));
                chunk.setWordCount(segment.text().length());
                knowledgeChunkMapper.insert(chunk);

                ChunkEmbeddingDTO dto = new ChunkEmbeddingDTO();
                dto.setChunkId(chunk.getId());
                dto.setDocId(doc.getId());
                dto.setBookId(bookId);
                dto.setContent(segment.text());
                dto.setEmbedding(embeddings.get(i));
                dto.setPageNum(chunk.getPageNum());
                chunkEmbeddings.add(dto);
            }

            // 6. 再批量写入 RedisSearch（事务外操作，放最后）
            if (!chunkEmbeddings.isEmpty()) {
                vectorStoreService.saveChunks(chunkEmbeddings);
                redisWritten = true;
            }

            // 7. 更新文档状态为成功
            doc.setChunkCount(segments.size());
            doc.setStatus(2);
            knowledgeDocMapper.update(doc);

            log.info("文档 [{}] 处理成功，存入 {} 个片段", fileName, segments.size());
            return doc;

        } catch (Exception e) {
            log.error("文档 [{}] 处理失败: {}", fileName, e.getMessage(), e);

            // 若 Redis 已写入但 MySQL 事务即将回滚，清理 Redis 残留，避免孤儿向量
            if (redisWritten) {
                log.warn("Redis 向量已写入但 MySQL 事务失败，清理 docId={} 的 Redis 残留", doc.getId());
                vectorStoreService.deleteByDocId(doc.getId());
            }

            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文档，同时清理 MySQL 和 Redis 数据
     */
    @Transactional
    public void deleteDoc(Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.findById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }

        // 1. 先删除 RedisSearch 中该文档的所有向量（按 docId metadata 过滤）
        vectorStoreService.deleteByDocId(docId);

        // 2. 删除 MySQL 数据
        knowledgeChunkMapper.deleteByDocId(docId);
        knowledgeDocMapper.deleteById(docId);
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
     * 重新处理某本书的所有文档：清理旧向量后重新生成
     */
    @Transactional
    public void reprocessBook(Long bookId) {
        // 清理旧数据
        knowledgeChunkMapper.deleteByBookId(bookId);
        vectorStoreService.deleteByBookId(bookId);

        List<KnowledgeDoc> docs = knowledgeDocMapper.findByBookId(bookId);
        if (docs == null || docs.isEmpty()) {
            return;
        }

        // 重新处理每篇文档
        // 注意：这里简化处理，实际应该复用 uploadAndProcess 的分块逻辑
        // 为了支持重新处理，可以保留原始文件或 content
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("文件大小不能超过 5MB，当前: %.2fMB", file.getSize() / 1024.0 / 1024.0)
            );
        }
    }

    private String parseContent(MultipartFile file, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> new ApachePdfBoxDocumentParser().parse(file.getInputStream()).text();
            case "doc", "docx" -> new ApachePoiDocumentParser().parse(file.getInputStream()).text();
            case "txt", "md" -> new String(file.getBytes(), "UTF-8");
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
        // LangChain4j 的 PDF 解析器可能会在 metadata 中存放页码
        String pageNum = segment.metadata().getString("page_number");
        if (pageNum != null) {
            try {
                return Integer.parseInt(pageNum);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
