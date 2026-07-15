package com.example.bigevent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.example.bigevent.domain.ChatHistoryChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 长期语义记忆服务：基于 Elasticsearch 向量检索的历史 QA 召回
 * <p>
 * 核心设计：不按 conversationId 过滤，至少按 userId 召回，支持跨会话长期记忆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryVectorService {

    public static final String INDEX = "chat_history_vector";

    private final ElasticsearchClient client;
    private final EmbeddingModel embeddingModel;

    /**
     * 召回策略
     */
    public enum RecallStrategy {
        /** 仅召回当前会话的历史（短期记忆已覆盖，默认不建议使用） */
        SAME_SESSION,
        /** 召回同一用户所有会话的历史，实现跨会话长期记忆 */
        CROSS_SESSION,
        /** 跨会话召回，并按时间衰减重新打分 */
        WITH_DECAY
    }

    /**
     * 保存一轮 QA 到 ES，用于后续语义召回。
     * <p>
     * 文档中保留 userId、conversationId、content、embedding 和 createTime。
     * conversationId 仅用于溯源和 SAME_SESSION 策略，检索时默认不按它过滤。
     *
     * @param userId         用户 ID
     * @param conversationId 会话 ID
     * @param question       用户问题
     * @param answer         AI 回答
     */
    public void save(Integer userId, String conversationId, String question, String answer) {
        if (userId == null || conversationId == null || question == null || answer == null) {
            return;
        }
        String content = "User: " + question.trim() + "\nAssistant: " + answer.trim();
        Embedding embedding = embeddingModel.embed(content).content();

        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", String.valueOf(userId));
        doc.put("conversationId", conversationId);
        doc.put("content", content);
        doc.put("embedding", toFloatList(embedding.vector()));
        doc.put("createTime", formatTime(LocalDateTime.now()));

        try {
            client.index(i -> i
                    .index(INDEX)
                    .id(UUID.randomUUID().toString())
                    .document(doc)
            );
            log.debug("已保存历史 QA 向量，userId={} conversationId={}", userId, conversationId);
        } catch (IOException e) {
            log.error("保存历史 QA 向量失败", e);
        }
    }

    /**
     * 语义召回历史 QA。
     * <p>
     * 流程：
     * <ol>
     *     <li>把当前问题向量化</li>
     *     <li>在 ES 中按 userId 做 KNN 近邻检索（SAME_SESSION 策略会额外过滤 conversationId）</li>
     *     <li>用 maxAgeDays 过滤掉过期历史</li>
     *     <li>WITH_DECAY 策略下按时间衰减重新打分</li>
     *     <li>用 minScore 过滤低质量结果，返回 topK 条 QA 文本</li>
     * </ol>
     *
     * @param userId         用户ID（必须）
     * @param conversationId 当前会话ID，SAME_SESSION 策略下用于过滤
     * @param question       当前问题
     * @param topK           召回条数
     * @param minScore       最小相似度阈值
     * @param maxAgeDays     最大历史天数，小于等于 0 表示不限制
     * @param strategy       召回策略
     * @return 按相关度排序的历史 QA 文本列表
     */
    public List<String> search(Integer userId, String conversationId, String question,
                               int topK, double minScore, int maxAgeDays, RecallStrategy strategy) {
        if (userId == null || question == null || question.isBlank()) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        List<Float> queryVector = toFloatList(queryEmbedding.vector());

        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index(INDEX)
                            .knn(buildKnnQuery(queryVector, topK, userId, conversationId, strategy, maxAgeDays))
                            .size(topK),
                    Map.class
            );

            List<Hit<Map>> hits = response.hits().hits();
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }

            return hits.stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        String content = source == null ? null : (String) source.get("content");
                        double score = hit.score() == null ? 0 : hit.score();
                        if (strategy == RecallStrategy.WITH_DECAY && source != null) {
                            score = applyTimeDecay(score, (String) source.get("createTime"));
                        }
                        return new ScoredHistory(content, score, source == null ? null : (String) source.get("createTime"));
                    })
                    .filter(sh -> sh.score >= minScore)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(topK)
                    .map(sh -> sh.content)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("历史 QA 向量召回失败", e);
            return List.of();
        }
    }

    /**
     * 语义召回历史 QA（字符串策略版本，便于从配置文件读取）。
     *
     * @param strategyName 策略名称，例如 "cross_session"、"with_decay"
     * @return 按相关度排序的历史 QA 文本列表
     */
    public List<String> search(Integer userId, String conversationId, String question,
                               int topK, double minScore, int maxAgeDays, String strategyName) {
        RecallStrategy strategy;
        try {
            strategy = RecallStrategy.valueOf(strategyName.toUpperCase());
        } catch (Exception e) {
            strategy = RecallStrategy.CROSS_SESSION;
        }
        return search(userId, conversationId, question, topK, minScore, maxAgeDays, strategy);
    }

    /**
     * 删除某会话下的所有历史向量。
     *
     * @param conversationId 会话 ID
     */
    public void deleteByConversationId(String conversationId) {
        deleteByField("conversationId", conversationId);
    }

    /**
     * 删除某用户下的所有历史向量。
     *
     * @param userId 用户 ID
     */
    public void deleteByUserId(Integer userId) {
        deleteByField("userId", String.valueOf(userId));
    }

    /**
     * 构建 KNN 查询。
     * <p>
     * 过滤条件：
     * <ul>
     *     <li>必须匹配 userId（实现用户隔离）</li>
     *     <li>SAME_SESSION 策略下额外匹配 conversationId</li>
     *     <li>maxAgeDays 大于 0 时限制 createTime 范围</li>
     * </ul>
     */
    private KnnQuery buildKnnQuery(List<Float> queryVector, int topK, Integer userId,
                                   String conversationId, RecallStrategy strategy, int maxAgeDays) {
        return KnnQuery.of(k -> {
            k.field("embedding")
                    .queryVector(queryVector)
                    .k((long) topK)
                    .numCandidates((long) topK * 10)
                    .filter(f -> f.bool(b -> {
                        b.must(m -> m.term(t -> t.field("userId").value(String.valueOf(userId))));
                        if (strategy == RecallStrategy.SAME_SESSION && conversationId != null) {
                            b.must(m -> m.term(t -> t.field("conversationId").value(conversationId)));
                        }
                        if (maxAgeDays > 0) {
                            LocalDateTime cutoff = LocalDateTime.now().minusDays(maxAgeDays);
                            b.must(m -> m.range(r -> r
                                    .field("createTime")
                                    .gte(JsonData.of(formatTime(cutoff)))));
                        }
                        return b;
                    }));
            return k;
        });
    }

    /**
     * 对相似度分数应用时间衰减。
     * <p>
     * 公式：score * exp(-0.03 * daysAgo)
     * 天数越久，分数衰减越多。
     */
    private double applyTimeDecay(double score, String createTimeStr) {
        if (createTimeStr == null) {
            return score;
        }
        try {
            LocalDateTime createTime = LocalDateTime.parse(createTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            long daysAgo = ChronoUnit.DAYS.between(createTime, LocalDateTime.now());
            double decay = Math.exp(-0.03 * Math.max(0, daysAgo));
            return score * decay;
        } catch (Exception e) {
            return score;
        }
    }

    /**
     * 按指定字段删除 ES 文档。
     */
    private void deleteByField(String field, String value) {
        try {
            DeleteByQueryResponse response = client.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(INDEX)
                    .query(q -> q.term(t -> t.field(field).value(value)))
            ));
            log.info("ES 删除 {}={} 的历史向量，共 {} 条", field, value, response.deleted());
        } catch (IOException e) {
            log.error("ES 删除 {}={} 历史向量失败", field, value, e);
        }
    }

    /**
     * float 数组转 Float 列表。
     */
    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    /**
     * 格式化时间为 ISO 日期时间字符串，供 ES date 类型存储和检索。
     */
    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        }
        return time.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    /**
     * 内部评分记录，用于时间衰减后的重排序。
     */
    private record ScoredHistory(String content, double score, String createTime) {
    }
}
