package com.example.bigevent.service;

import com.example.bigevent.domain.dto.rag.ChunkEmbeddingDTO;
import com.example.bigevent.domain.vo.rag.SearchResultVO;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 基于 RedisSearch 向量索引的向量存储服务
 * 底层使用 LangChain4j RedisEmbeddingStore，通过 FT.SEARCH 做 ANN 向量检索
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_DOC_ID = "docId";
    public static final String META_BOOK_ID = "bookId";

    /**
     * 保存 chunk 向量到 RedisSearch
     */
    public void saveChunk(Long chunkId, Long docId, Long bookId,
                          String content, Embedding embedding, Integer pageNum) {
        Metadata metadata = buildMetadata(chunkId, docId, bookId, pageNum);
        TextSegment segment = TextSegment.from(content, metadata);
        embeddingStore.add(embedding, segment);
    }

    /**
     * 批量保存 chunk 向量
     */
    public void saveChunks(List<ChunkEmbeddingDTO> chunks) {
        List<TextSegment> segments = chunks.stream()
                .map(chunk -> {
                    Metadata metadata = buildMetadata(
                            chunk.getChunkId(), chunk.getDocId(), chunk.getBookId(), chunk.getPageNum());
                    return TextSegment.from(chunk.getContent(), metadata);
                })
                .collect(Collectors.toList());

        List<Embedding> embeddings = chunks.stream()
                .map(ChunkEmbeddingDTO::getEmbedding)
                .collect(Collectors.toList());

        embeddingStore.addAll(embeddings, segments);
    }

    /**
     * 向量检索：基于 RedisSearch ANN 搜索
     *
     * @param bookId   图书ID，为 null 时搜索全部
     * @param question 用户问题
     * @param topK     返回数量
     * @param minScore 最小相似度阈值
     */
    public List<SearchResultVO> search(Long bookId, String question, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore);

        // 如果指定了 bookId，加上过滤条件
        if (bookId != null) {
            Filter filter = metadataKey(META_BOOK_ID).isEqualTo(String.valueOf(bookId));
            requestBuilder.filter(filter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(requestBuilder.build());

        return result.matches().stream()
                .map(this::convertToSearchResult)
                .collect(Collectors.toList());
    }

    /**
     * 删除单个 chunk 向量
     */
    public void deleteByChunkId(Long chunkId) {
        Filter filter = metadataKey(META_CHUNK_ID).isEqualTo(String.valueOf(chunkId));
        embeddingStore.removeAll(filter);
    }

    /**
     * 删除某个文档下的所有向量
     */
    public void deleteByDocId(Long docId) {
        Filter filter = metadataKey(META_DOC_ID).isEqualTo(String.valueOf(docId));
        embeddingStore.removeAll(filter);
        log.info("已删除 docId={} 的向量", docId);
    }

    /**
     * 删除某本书下的所有向量
     */
    public void deleteByBookId(Long bookId) {
        Filter filter = metadataKey(META_BOOK_ID).isEqualTo(String.valueOf(bookId));
        embeddingStore.removeAll(filter);
        log.info("已删除 bookId={} 的向量", bookId);
    }

    private Metadata buildMetadata(Long chunkId, Long docId, Long bookId, Integer pageNum) {
        Metadata metadata = new Metadata();
        metadata.put(META_CHUNK_ID, String.valueOf(chunkId));
        metadata.put(META_DOC_ID, String.valueOf(docId));
        metadata.put(META_BOOK_ID, String.valueOf(bookId));
        metadata.put("pageNum", String.valueOf(pageNum));
        return metadata;
    }

    private SearchResultVO convertToSearchResult(
            dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();

        SearchResultVO searchResult = new SearchResultVO();
        searchResult.setChunkId(parseLong(metadata.getString(META_CHUNK_ID)));
        searchResult.setDocId(parseLong(metadata.getString(META_DOC_ID)));
        searchResult.setBookId(parseLong(metadata.getString(META_BOOK_ID)));
        searchResult.setPageNum(parseInt(metadata.getString("pageNum")));
        searchResult.setContent(segment.text());
        searchResult.setScore((float) match.score().doubleValue());
        return searchResult;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
