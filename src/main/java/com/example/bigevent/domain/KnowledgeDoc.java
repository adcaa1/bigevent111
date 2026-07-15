package com.example.bigevent.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档：存储上传的原始文档元信息
 */
@Data
public class KnowledgeDoc {

    private Long id;

    /**
     * 关联图书ID，为 null 时表示通用知识库
     */
    private Long bookId;

    /**
     * 上传用户ID（知识所有者）
     */
    private Integer createUser;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型：pdf / doc / docx / txt / md
     */
    private String fileType;

    /**
     * 文件存储路径（本地或 OSS）
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件 MD5，用于去重和重新处理校验
     */
    private String fileMd5;

    /**
     * 解析后的完整纯文本
     */
    private String content;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 处理状态：0-待处理 1-处理中 2-成功 3-失败
     */
    private Integer status;

    /**
     * 可见性：0-私有 1-部门 2-公共
     */
    private Integer visibility;

    /**
     * 所属部门ID（visibility=1时有效）
     */
    private Integer departmentId;

    /**
     * 失败原因
     */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
