package com.example.bigevent.service;

import com.example.bigevent.domain.vo.rag.HybridResultVO;
import com.example.bigevent.domain.vo.rag.SearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多路召回服务：向量检索 + 关键词检索融合排序
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final VectorStoreService vectorStoreService;
    private final ElasticsearchKeywordService elasticsearchKeywordService;

    private static final int VECTOR_TOP_K = 10;
    private static final int KEYWORD_TOP_K = 10;
    private static final double VECTOR_WEIGHT = 1.0;
    private static final double KEYWORD_WEIGHT = 0.7;
    private static final int RRF_K = 60;

    /**
     * 多路召回检索，支持用户隔离与指定范围过滤
     *
     * @param userId    当前用户ID，用于权限过滤
     * @param bookId    图书 ID，null 时检索通用知识库
     * @param docId     文档 ID，null 时不限制
     * @param question  用户问题
     * @param finalTopK 最终返回条数
     * @return 融合排序后的结果
     */
    public List<HybridResultVO> search(Integer userId, Long bookId, Long docId, String question, int finalTopK) {
        List<HybridResultVO> vectorResults = vectorSearch(userId, bookId, docId, question, VECTOR_TOP_K);
        List<HybridResultVO> keywordResults = elasticsearchKeywordService.search(userId, bookId, docId, question, KEYWORD_TOP_K);

        log.info("向量召回 {} 条，ES 关键词召回 {} 条", vectorResults.size(), keywordResults.size());

        Map<Long, HybridResultVO> merged = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            HybridResultVO vo = vectorResults.get(i);
            double score = VECTOR_WEIGHT * (1.0 / (RRF_K + i + 1));
            vo.setScore(score);
            merged.merge(vo.getChunkId(), vo, this::mergeScore);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            HybridResultVO vo = keywordResults.get(i);
            double score = KEYWORD_WEIGHT * (1.0 / (RRF_K + i + 1));
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
    private List<HybridResultVO> vectorSearch(Integer userId, Long bookId, Long docId, String question, int topK) {
        List<SearchResultVO> vectorResults = vectorStoreService.search(userId, bookId, docId, question, topK, 0.0);

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
