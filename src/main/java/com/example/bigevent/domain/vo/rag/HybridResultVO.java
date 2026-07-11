package com.example.bigevent.domain.vo.rag;

import lombok.Data;

/**
 * 多路召回结果 VO
 */
@Data
public class HybridResultVO {

    private Long chunkId;
    private Long docId;
    private Long bookId;
    private Integer userId;
    private String title;
    private String content;
    private Integer chunkIndex;
    private Integer pageNum;

    /**
     * 融合排序分数，不参与持久化
     */
    private double score;
}
