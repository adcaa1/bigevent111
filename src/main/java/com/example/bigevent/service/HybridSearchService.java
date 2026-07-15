package com.example.bigevent.service;

import com.example.bigevent.domain.vo.rag.HybridResultVO;
import com.example.bigevent.domain.vo.rag.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多路召回服务：向量检索 + 关键词检索融合排序
 */
@Service
@Slf4j
public class HybridSearchService {

    private final VectorStoreService vectorStoreService;
    private final ElasticsearchKeywordService elasticsearchKeywordService;

    private final int vectorTopK;
    private final int keywordTopK;
    private final double vectorWeight;
    private final double keywordWeight;
    private final int rrfK;
    private final double minScore;

    public HybridSearchService(VectorStoreService vectorStoreService,
                               ElasticsearchKeywordService elasticsearchKeywordService,
                               @Value("${rag.search.vector-top-k:10}") int vectorTopK,
                               @Value("${rag.search.keyword-top-k:10}") int keywordTopK,
                               @Value("${rag.search.vector-weight:1.0}") double vectorWeight,
                               @Value("${rag.search.keyword-weight:0.7}") double keywordWeight,
                               @Value("${rag.search.rrf-k:60}") int rrfK,
                               @Value("${rag.search.min-score:0.2}") double minScore) {
        this.vectorStoreService = vectorStoreService;
        this.elasticsearchKeywordService = elasticsearchKeywordService;
        this.vectorTopK = vectorTopK;
        this.keywordTopK = keywordTopK;
        this.vectorWeight = vectorWeight;
        this.keywordWeight = keywordWeight;
        this.rrfK = rrfK;
        this.minScore = minScore;
    }

    /**
     * 多路召回检索，支持用户隔离、部门过滤与指定范围过滤
     *
     * @param userId       当前用户ID，用于权限过滤
     * @param departmentId 当前用户部门ID，用于部门级可见性判断
     * @param bookId       图书 ID，null 时检索通用知识库
     * @param docId        文档 ID，null 时不限制
     * @param question     用户问题
     * @param finalTopK    最终返回条数
     * @return 融合排序后的结果
     */
    public List<HybridResultVO> search(Integer userId, Integer departmentId, Long bookId, Long docId, String question, int finalTopK) {
        List<HybridResultVO> vectorResults = vectorSearch(userId, departmentId, bookId, docId, question, vectorTopK);
        List<HybridResultVO> keywordResults = elasticsearchKeywordService.search(userId, departmentId, bookId, docId, question, keywordTopK);

        log.info("向量召回 {} 条，ES 关键词召回 {} 条", vectorResults.size(), keywordResults.size());

        Map<Long, HybridResultVO> merged = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            HybridResultVO vo = vectorResults.get(i);
            double score = vectorWeight * (1.0 / (rrfK + i + 1));
            vo.setScore(score);
            merged.merge(vo.getChunkId(), vo, this::mergeScore);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            HybridResultVO vo = keywordResults.get(i);
            double score = keywordWeight * (1.0 / (rrfK + i + 1));
            vo.setScore(score);
            merged.merge(vo.getChunkId(), vo, this::mergeScore);
        }

        List<HybridResultVO> result = merged.values().stream()
                .sorted(Comparator.comparing(HybridResultVO::getScore).reversed())
                .limit(finalTopK)
                .peek(vo -> log.info("融合结果 chunkId={}, score={}", vo.getChunkId(), vo.getScore()))
                .toList();

        log.info("多路召回最终返回 {} 条", result.size());
        return result;
    }

    /**
     * 向量检索封装
     */
    private List<HybridResultVO> vectorSearch(Integer userId, Integer departmentId, Long bookId, Long docId, String question, int topK) {
        List<SearchResultVO> vectorResults = vectorStoreService.search(userId, departmentId, bookId, docId, question, topK, minScore);

        return vectorResults.stream().map(r -> {
            HybridResultVO vo = new HybridResultVO();
            vo.setChunkId(r.getChunkId());
            vo.setDocId(r.getDocId());
            vo.setBookId(r.getBookId());
            vo.setUserId(r.getUserId());
            vo.setTitle(r.getTitle());
            vo.setContent(r.getContent());
            vo.setChunkIndex(r.getChunkIndex());
            vo.setPageNum(r.getPageNum());
            return vo;
        }).toList();
    }

    /**
     * 合并两路召回的分数
     */
    private HybridResultVO mergeScore(HybridResultVO old, HybridResultVO neo) {
        old.setScore(old.getScore() + neo.getScore());
        return old;
    }
}
