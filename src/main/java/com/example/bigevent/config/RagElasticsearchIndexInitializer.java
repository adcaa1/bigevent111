package com.example.bigevent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * RAG 知识库 ES 索引初始化
 * <p>
 * 启动时检查并创建 `document_chunk_index` 索引，使用 ik 分词器处理中文内容，
 * 同时存储向量字段以支持后续扩展和统一检索。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagElasticsearchIndexInitializer implements CommandLineRunner {

    public static final String RAG_INDEX = "document_chunk_index";

    /**
     * DashScope text-embedding-v2 向量维度
     */
    public static final int EMBEDDING_DIM = 1536;

    private final ElasticsearchClient client;
/**
 * 在 Spring Boot 启动时自动执行，不需要手动调用。
 * 检查 document_chunk_index 索引是否存在，不存在则自动创建。
 */
    @Override
    public void run(String... args) throws Exception {
        boolean exists = client.indices().exists(e -> e.index(RAG_INDEX)).value();
        if (exists) {
            log.info("Elasticsearch 索引 [{}] 已存在，跳过初始化", RAG_INDEX);
            return;
        }

        client.indices().create(c -> c
                .index(RAG_INDEX)
                .settings(buildSettings())
                .mappings(buildMappings())
        );

        log.info("Elasticsearch 索引 [{}] 创建成功", RAG_INDEX);
    }
/**
 * 配置中文分词器
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
 * 定义 RAG 所需的数据结构（文档 ID、Chunk、页码等字段）。
 * 配置 dense_vector 向量字段（1536 维、余弦相似度、支持向量索引），为后续语义检索做好准备。
 */
    private TypeMapping buildMappings() {
        return TypeMapping.of(m -> m
                .properties("chunkId", p -> p.long_(l -> l))
                .properties("docId", p -> p.long_(l -> l))
                .properties("bookId", p -> p.long_(l -> l))
                .properties("userId", p -> p.integer(i -> i))
                .properties("visibility", p -> p.integer(i -> i))
                .properties("title", p -> p.text(t -> t
                        .analyzer("ik_analyzer")
                        .searchAnalyzer("ik_smart")))
                .properties("content", p -> p.text(t -> t
                        .analyzer("ik_analyzer")
                        .searchAnalyzer("ik_smart")))
                .properties("pageNum", p -> p.integer(i -> i))
                .properties("chunkIndex", p -> p.integer(i -> i))
                .properties("embedding", p -> p.denseVector(d -> d
                        .dims(EMBEDDING_DIM)
                        .index(true)
                        .similarity("cosine")))
                .properties("createTime", p -> p.date(d -> d.format("strict_date_optional_time||epoch_millis")))
        );
    }
}
