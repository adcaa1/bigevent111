package com.example.bigevent.domain.vo.rag;

import lombok.Data;

/**
 * 多路召回结果 VO
 */
@Data
public class HybridResultVO {

    private Long chunkId;
    private Long docId;
    private Integer userId;
    private String title;
    private String content;
    private Integer chunkIndex;
    private Integer pageNum;

    /**
     * 融合排序分数（RRF），不参与持久化
     */
    private double score;

    /**
     * 向量检索原始相似度分数（余弦相似度），不参与持久化
     */
    private Float vectorScore;

    /**
     * ES 关键词检索原始 BM25 分数，不参与持久化
     */
    private Float keywordScore;
}
