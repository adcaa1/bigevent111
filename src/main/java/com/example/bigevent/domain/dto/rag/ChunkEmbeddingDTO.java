package com.example.bigevent.domain.dto.rag;

import dev.langchain4j.data.embedding.Embedding;
import lombok.Data;

/**
 * Chunk 向量化传输对象
 * 用于批量保存 chunk 到向量库时的参数传递
 */
@Data
public class ChunkEmbeddingDTO {

    private Long chunkId;
    private Long docId;
    private Long bookId;
    private String content;
    private Embedding embedding;
    private Integer pageNum;
}
