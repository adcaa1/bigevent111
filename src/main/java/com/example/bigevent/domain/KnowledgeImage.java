package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档中的图片元信息。
 * <p>
 * 保存从文档中提取的图片本地路径、VLM 生成的描述，以及关联的 chunk 信息。
 */
@Data
public class KnowledgeImage {

    private Long id;

    /**
     * 所属文档ID
     */
    private Long docId;

    /**
     * 关联的文本片段ID（处理成功后回填）
     */
    private Long chunkId;

    /**
     * 片段序号（同一文档内从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 图片本地存储路径
     */
    private String imagePath;

    /**
     * 视觉模型生成的图片描述
     */
    private String description;

    /**
     * 页码，PDF 解析时可能有
     */
    private Integer pageNum;

    private LocalDateTime createTime;
}
