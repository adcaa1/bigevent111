package com.example.bigevent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.bigevent.config.RagElasticsearchIndexInitializer;
import com.example.bigevent.domain.dto.rag.ChunkEmbeddingDTO;
import com.example.bigevent.domain.vo.rag.HybridResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 Elasticsearch + IK 分词的关键词检索服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchKeywordService {

    private final ElasticsearchClient client;

    /**
     * 批量写入 chunk 到 ES
     */
    public void bulkIndexChunks(List<ChunkEmbeddingDTO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        try {
            client.bulk(b -> {
                for (ChunkEmbeddingDTO chunk : chunks) {
                    b.operations(op -> op.index(idx -> idx
                            .index(RagElasticsearchIndexInitializer.RAG_INDEX)
                            .id(chunk.getEsDocId())
                            .document(buildEsDocument(chunk))
                    ));
                }
                return b;
            });
            log.info("ES 批量写入 {} 个 chunk", chunks.size());
        } catch (IOException e) {
            log.error("ES 批量写入 chunk 失败", e);
            throw new RuntimeException("ES 索引写入失败", e);
        }
    }

    private Map<String, Object> buildEsDocument(ChunkEmbeddingDTO chunk) {
        List<Float> vector = new ArrayList<>();
        if (chunk.getEmbedding() != null && chunk.getEmbedding().vector() != null) {
            for (float v : chunk.getEmbedding().vector()) {
                vector.add(v);
            }
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("chunkId", chunk.getChunkId());
        doc.put("docId", chunk.getDocId());
        doc.put("userId", chunk.getUserId() == null ? 0 : chunk.getUserId());
        doc.put("visibility", chunk.getVisibility() == null ? 2 : chunk.getVisibility());
        doc.put("departmentId", chunk.getDepartmentId() == null ? 0 : chunk.getDepartmentId());
        doc.put("title", chunk.getTitle() == null ? "" : chunk.getTitle());
        doc.put("content", chunk.getContent());
        if (chunk.getPageNum() != null) {
            doc.put("pageNum", chunk.getPageNum());
        }
        doc.put("chunkIndex", chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex());
        doc.put("embedding", vector);
        doc.put("createTime", formatTime(LocalDateTime.now()));
        return doc;
    }

    /**
     * 关键词检索，支持用户隔离与部门过滤
     *
     * @param userId       当前用户ID，null 时只查公共知识
     * @param departmentId 当前用户部门ID，用于部门级可见性判断
     * @param docId  文档 ID，null 时不限制
     * @param keyword 用户问题
     * @param topK   返回条数
     */
    public List<HybridResultVO> search(Integer userId, Integer departmentId, Long docId, String keyword, int topK, double minScoreRatio) {
        try {
            SearchResponse<Map> response = client.search(s -> s
                            .index(RagElasticsearchIndexInitializer.RAG_INDEX)
                            .query(q -> q
                                    .bool(b -> {
                                        b.must(m -> m.multiMatch(mt -> mt
                                                .fields("title^2", "content")
                                                .query(keyword)));
                                        b.filter(f -> buildAuthFilter(f, userId, departmentId));
                                        if (docId != null) {
                                            b.filter(f -> f.term(t -> t.field("docId").value(docId)));
                                        }
                                        return b;
                                    })
                            )
                            .sort(so -> so.field(f -> f.field("_score").order(SortOrder.Desc)))
                            .size(topK),
                    Map.class
            );

            List<HybridResultVO> results = response.hits().hits().stream()
                    .map(this::toHybridResult)
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                return results;
            }

            double maxScore = results.stream()
                    .mapToDouble(HybridResultVO::getKeywordScore)
                    .max()
                    .orElse(0.0);
            double threshold = maxScore * minScoreRatio;

            return results.stream()
                    .filter(vo -> vo.getKeywordScore() >= threshold)
                    .peek(vo -> log.debug("ES 命中 chunkId={}, keywordScore={}/{}",
                            vo.getChunkId(), vo.getKeywordScore(), maxScore))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("ES 关键词检索失败", e);
            throw new RuntimeException("ES 检索失败", e);
        }
    }

    private co.elastic.clients.util.ObjectBuilder<co.elastic.clients.elasticsearch._types.query_dsl.Query> buildAuthFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.Query.Builder builder,
            Integer userId, Integer departmentId) {
        // 匿名用户：只看公共知识
        if (userId == null) {
            return builder.term(t -> t.field("visibility").value(2));
        }

        // 登录用户：自己的 + 同部门的 + 公共的
        var boolBuilder = builder.bool(b -> {
            b.should(s -> s.term(t -> t.field("userId").value(userId)));
            b.should(s -> s.term(t -> t.field("visibility").value(2)));

            if (departmentId != null) {
                b.should(s -> s.bool(sb -> sb
                        .must(m -> m.term(t -> t.field("visibility").value(1)))
                        .must(m -> m.term(t -> t.field("departmentId").value(departmentId)))
                ));
            }

            b.minimumShouldMatch("1");
            return b;
        });
        return boolBuilder;
    }

    /**
     * 按 docId 删除 ES 中的 chunk
     */
    public void deleteByDocId(Long docId) {
        deleteByField("docId", docId);
    }

    /**
     * 按 userId 删除 ES 中的 chunk
     */
    public void deleteByUserId(Integer userId) {
        deleteByField("userId", userId);
    }

    private void deleteByField(String field, Object value) {
        try {
            FieldValue fieldValue = value instanceof Number n
                    ? FieldValue.of(n.longValue())
                    : FieldValue.of(value.toString());
            DeleteByQueryResponse response = client.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(RagElasticsearchIndexInitializer.RAG_INDEX)
                    .query(q -> q.term(t -> t.field(field).value(fieldValue)))
            ));
            log.info("ES 删除 {}={} 的 chunk，共 {} 条", field, value, response.deleted());
        } catch (IOException e) {
            log.error("ES 删除 {}={} 失败", field, value, e);
            throw new RuntimeException("ES 删除失败", e);
        }
    }

    private HybridResultVO toHybridResult(Hit<Map> hit) {
        Map<String, Object> source = hit.source();
        HybridResultVO vo = new HybridResultVO();
        vo.setChunkId(toLong(source.get("chunkId")));
        vo.setDocId(toLong(source.get("docId")));
        vo.setUserId(toInt(source.get("userId")));
        vo.setTitle((String) source.get("title"));
        vo.setContent((String) source.get("content"));
        vo.setChunkIndex(toInt(source.get("chunkIndex")));
        vo.setPageNum(toInt(source.get("pageNum")));
        vo.setKeywordScore(hit.score() != null ? hit.score().floatValue() : null);
        return vo;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        if (value == null) return null;
        return ((Number) value).intValue();
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
        }
        return time.format(DateTimeFormatter.ISO_DATE_TIME);
    }
}
