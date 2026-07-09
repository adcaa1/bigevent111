package com.example.bigevent.domain.vo.rag;

import lombok.Data;

/**
 * RAG 向量检索结果 VO
 */
@Data
public class SearchResultVO {

    private Long chunkId;
    private Long docId;
    private Long bookId;
    private String content;
    private Integer pageNum;
    private Float score;
}
