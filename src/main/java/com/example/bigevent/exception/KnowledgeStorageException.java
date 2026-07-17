package com.example.bigevent.exception;

import lombok.Getter;

/**
 * 知识库存储异常：MySQL、Redis、Elasticsearch 任一环节写入失败时抛出。
 */
@Getter
public class KnowledgeStorageException extends RuntimeException {

    public enum Stage {
        EMBEDDING("向量生成"),
        MYSQL("MySQL"),
        REDIS("Redis"),
        ELASTICSEARCH("Elasticsearch");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Stage stage;

    public KnowledgeStorageException(Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public KnowledgeStorageException(Stage stage, String message) {
        super(message);
        this.stage = stage;
    }
}
