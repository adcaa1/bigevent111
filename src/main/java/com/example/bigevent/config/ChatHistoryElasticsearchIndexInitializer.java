package com.example.bigevent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 历史聊天向量 ES 索引初始化器。
 * <p>
 * 应用启动时检查并创建 {@code chat_history_vector} 索引，
 * 用于存储用户历史 QA 的文本和向量，支撑长期语义记忆召回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryElasticsearchIndexInitializer implements CommandLineRunner {

    /** ES 索引名 */
    public static final String CHAT_HISTORY_INDEX = "chat_history_vector";

    /** DashScope text-embedding-v2 向量维度 */
    public static final int EMBEDDING_DIM = 1536;

    private final ElasticsearchClient client;

    /**
     * 应用启动后执行：如果索引不存在则创建。
     *
     * @param args 命令行参数
     * @throws Exception ES 操作异常
     */
    @Override
    public void run(String... args) throws Exception {
        boolean exists = client.indices().exists(e -> e.index(CHAT_HISTORY_INDEX)).value();
        if (exists) {
            log.info("Elasticsearch 索引 [{}] 已存在，跳过初始化", CHAT_HISTORY_INDEX);
            return;
        }

        client.indices().create(c -> c
                .index(CHAT_HISTORY_INDEX)
                .settings(buildSettings())
                .mappings(buildMappings())
        );

        log.info("Elasticsearch 索引 [{}] 创建成功", CHAT_HISTORY_INDEX);
    }

    /**
     * 构建索引设置：1 个分片、0 个副本、IK 分词器。
     */
    private IndexSettings buildSettings() {
        return IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(a -> a
                        .analyzer("ik_analyzer", ia -> ia
                                .custom(ca -> ca.tokenizer("ik_max_word"))
                        )
                )
        );
    }

    /**
     * 构建索引映射：userId、conversationId、content、embedding、createTime。
     */
    private TypeMapping buildMappings() {
        return TypeMapping.of(m -> m
                .properties("userId", p -> p.keyword(k -> k))
                .properties("conversationId", p -> p.keyword(k -> k))
                .properties("content", p -> p.text(t -> t
                        .analyzer("ik_analyzer")
                        .searchAnalyzer("ik_smart")))
                .properties("embedding", p -> p.denseVector(d -> d
                        .dims(EMBEDDING_DIM)
                        .index(true)
                        .similarity("cosine")))
                .properties("createTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
        );
    }
}
