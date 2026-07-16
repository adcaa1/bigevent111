package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文本片段：文档分块后的内容
 */
@Data
public class KnowledgeChunk {

    private Long id;

    /**
     * 所属文档ID
     */
    private Long docId;

    /**
     * 片段内容
     */
    private String content;

    /**
     * 片段序号（同一文档内从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 页码，PDF 解析时可能有
     */
    private Integer pageNum;

    /**
     * 字数
     */
    private Integer wordCount;

    /**
     * ES 中文档 _id，便于精确删除
     */
    private String esDocId;

    /**
     * Redis 向量 key 的后缀
     */
    private String vectorKey;

    private LocalDateTime createTime;
}
