package com.example.bigevent.constant;

/**
 * 知识库模块常量
 */
public final class KnowledgeConstants {

    private KnowledgeConstants() {
    }

    /**
     * 文档处理状态
     */
    public static final class DocStatus {
        private DocStatus() {
        }

        public static final int PENDING = 0;
        public static final int PROCESSING = 1;
        public static final int SUCCESS = 2;
        public static final int FAILED = 3;
    }

    /**
     * 文件上传大小限制：10MB
     */
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * 知识可见性
     */
    public static final class Visibility {
        private Visibility() {
        }

        public static final int PRIVATE = 0;
        public static final int TEAM = 1;
        public static final int PUBLIC = 2;
    }
}
