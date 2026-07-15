package com.example.bigevent.domain.vo.rag;

import lombok.Data;

import java.util.List;

/**
 * RAG 问答返回结果 VO
 */
@Data
public class RagAnswerVO {

    /**
     * AI 回答内容
     */
    private String answer;

    /**
     * 引用来源列表
     */
    private List<Citation> citations;

    /**
     * 引用来源
     */
    @Data
    public static class Citation {

        /**
         * 引用编号，对应回答中的 [^1] [^2]
         */
        private int id;

        /**
         * 文档标题
         */
        private String title;

        /**
         * 页码
         */
        private Integer pageNum;

        /**
         * 片段内容摘要
         */
        private String content;
    }
}
