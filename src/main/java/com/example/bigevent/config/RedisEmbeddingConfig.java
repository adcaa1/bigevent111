package com.example.bigevent.config;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 RedisSearch 向量索引配置。
 *
 * <p>不再依赖 LangChain4j 自动建索引的默认行为，而是在项目代码里显式声明：
 * <ul>
 *     <li>索引名、key 前缀、向量维度</li>
 *     <li>每个 metadata 字段的类型（TAG 用于精确匹配，TEXT 用于标题检索）</li>
 * </ul>
 *
 * <p>这样做的好处是索引结构完全由项目控制，避免第三方库升级后默认行为变化导致
 * metadata 字段未建索引的问题。
 */
@Slf4j
@Configuration
public class RedisEmbeddingConfig {

    public static final String INDEX_NAME = "embedding-index";
    public static final String KEY_PREFIX = "embedding:";
    public static final int EMBEDDING_DIMENSION = 1536;

    /**
     * metadata 字段的 RediSearch 类型声明。
     * 必须与 VectorStoreService.buildMetadata 中放入的字段名保持一致。
     */
    public static final Map<String, SchemaField> METADATA_SCHEMA;

    static {
        Map<String, SchemaField> map = new LinkedHashMap<>();
        map.put("docId", TagField.of("$.docId").as("docId"));
        map.put("userId", TagField.of("$.userId").as("userId"));
        map.put("visibility", TagField.of("$.visibility").as("visibility"));
        map.put("departmentId", TagField.of("$.departmentId").as("departmentId"));
        map.put("chunkId", TagField.of("$.chunkId").as("chunkId"));
        map.put("chunkIndex", TagField.of("$.chunkIndex").as("chunkIndex"));
        map.put("pageNum", TagField.of("$.pageNum").as("pageNum"));
        map.put("title", TextField.of("$.title").as("title").weight(1.0));
        METADATA_SCHEMA = Collections.unmodifiableMap(map);
    }

    @Bean
    @ConditionalOnMissingBean
    public UnifiedJedis redisEmbeddingClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new JedisPooled(host, port);
    }

    @Bean
    public RedisEmbeddingStore redisEmbeddingStore(
            UnifiedJedis client,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${rag.redis.rebuild-index-on-startup:false}") boolean rebuildIndexOnStartup) {

        boolean indexExists = indexExists(client);
        if (rebuildIndexOnStartup && indexExists) {
            client.ftDropIndex(INDEX_NAME);
            log.warn("已按 rag.redis.rebuild-index-on-startup=true 重建 Redis 索引 '{}'，"
                    + "旧索引已删除（保留 JSON 文档），新索引会重新索引已有数据", INDEX_NAME);
            indexExists = false;
        }

        if (!indexExists) {
            createIndex(client);
        } else {
            log.info("Redis 向量索引 '{}' 已存在，跳过创建", INDEX_NAME);
        }

        return RedisEmbeddingStore.builder()
                .host(host)
                .port(port)
                .indexName(INDEX_NAME)
                .prefix(KEY_PREFIX)
                .dimension(EMBEDDING_DIMENSION)
                .metadataConfig(METADATA_SCHEMA)
                .build();
    }

    private boolean indexExists(UnifiedJedis client) {
        try {
            client.ftInfo(INDEX_NAME);
            return true;
        } catch (JedisDataException e) {
            String message = e.getMessage();

            if (message != null &&
                    message.toLowerCase().contains("unknown index name")) {
                return false;
            }

            throw e;
        }
    }
    /**
     * 显式执行 FT.CREATE，字段类型完全由项目代码控制。
     */
    private void createIndex(UnifiedJedis client) {
        FTCreateParams params = FTCreateParams.createParams()
                .on(IndexDataType.JSON)
                .addPrefix(KEY_PREFIX);

        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of("$.text").as("text").weight(1.0));

        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", EMBEDDING_DIMENSION);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("INITIAL_CAP", 5);

        fields.add(VectorField.builder()
                .fieldName("$.vector")
                .algorithm(VectorField.VectorAlgorithm.HNSW)
                .attributes(vectorAttrs)
                .as("vector")
                .build());

        fields.addAll(METADATA_SCHEMA.values());

        client.ftCreate(INDEX_NAME, params, fields);
        log.info("已创建 Redis 向量索引 '{}': text + vector(HNSW/COSINE/{}) + metadata {}",
                INDEX_NAME, EMBEDDING_DIMENSION, METADATA_SCHEMA.keySet());
    }
}
