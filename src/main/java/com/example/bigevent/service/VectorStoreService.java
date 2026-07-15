package com.example.bigevent.service;

import com.example.bigevent.constant.KnowledgeConstants;
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
    public static final String META_USER_ID = "userId";
    public static final String META_VISIBILITY = "visibility";
    public static final String META_DEPARTMENT_ID = "departmentId";
    public static final String META_TITLE = "title";
    public static final String META_CHUNK_INDEX = "chunkIndex";
    public static final String META_PAGE_NUM = "pageNum";

    /**
     * 保存 chunk 向量到 RedisSearch
     */
    public void saveChunk(Long chunkId, Long docId, Long bookId, Integer userId, Integer visibility, Integer departmentId,
                          String title, String content, Embedding embedding,
                          Integer chunkIndex, Integer pageNum) {
        Metadata metadata = buildMetadata(chunkId, docId, bookId, userId, visibility, departmentId, title, chunkIndex, pageNum);
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
                            chunk.getChunkId(), chunk.getDocId(), chunk.getBookId(),
                            chunk.getUserId(), chunk.getVisibility(), chunk.getDepartmentId(), chunk.getTitle(),
                            chunk.getChunkIndex(), chunk.getPageNum());
                    return TextSegment.from(chunk.getContent(), metadata);
                })
                .collect(Collectors.toList());

        List<Embedding> embeddings = chunks.stream()
                .map(ChunkEmbeddingDTO::getEmbedding)
                .collect(Collectors.toList());

        embeddingStore.addAll(embeddings, segments);
    }

    /**
     * 向量检索：基于 RedisSearch ANN 搜索，支持用户隔离与部门过滤
     *
     * @param userId       当前用户ID，null 时只查公共知识
     * @param departmentId 当前用户部门ID，用于部门级可见性判断
     * @param bookId       图书ID，为 null 时搜索全部
     * @param docId        文档ID，为 null 时搜索全部
     * @param question     用户问题
     * @param topK         返回数量
     * @param minScore     最小相似度阈值
     */
    public List<SearchResultVO> search(Integer userId, Integer departmentId, Long bookId, Long docId, String question, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore);

        Filter filter = buildFilter(userId, departmentId, bookId, docId);
        if (filter != null) {
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

    /**
     * 删除某个用户下的所有向量
     */
    public void deleteByUserId(Integer userId) {
        Filter filter = metadataKey(META_USER_ID).isEqualTo(String.valueOf(userId));
        embeddingStore.removeAll(filter);
        log.info("已删除 userId={} 的向量", userId);
    }

    private Filter buildFilter(Integer userId, Integer departmentId, Long bookId, Long docId) {
        Filter filter = buildAuthFilter(userId, departmentId);

        if (bookId != null) {
            Filter bookFilter = metadataKey(META_BOOK_ID).isEqualTo(String.valueOf(bookId));
            filter = filter == null ? bookFilter : filter.and(bookFilter);
        }

        if (docId != null) {
            Filter docFilter = metadataKey(META_DOC_ID).isEqualTo(String.valueOf(docId));
            filter = filter == null ? docFilter : filter.and(docFilter);
        }

        return filter;
    }

    private Filter buildAuthFilter(Integer userId, Integer departmentId) {
        // 匿名用户：只看公共知识
        if (userId == null) {
            return metadataKey(META_VISIBILITY).isEqualTo(String.valueOf(2));
        }

        // 登录用户：自己的 + 同部门的 + 公共的
        Filter ownFilter = metadataKey(META_USER_ID).isEqualTo(String.valueOf(userId));
        Filter publicFilter = metadataKey(META_VISIBILITY).isEqualTo(String.valueOf(2));

        if (departmentId != null) {
            Filter deptVisibilityFilter = metadataKey(META_VISIBILITY).isEqualTo(String.valueOf(1));
            Filter deptIdFilter = metadataKey(META_DEPARTMENT_ID).isEqualTo(String.valueOf(departmentId));
            return ownFilter.or(deptVisibilityFilter.and(deptIdFilter)).or(publicFilter);
        }

        return ownFilter.or(publicFilter);
    }

    private Metadata buildMetadata(Long chunkId, Long docId, Long bookId, Integer userId,
                                   Integer visibility, Integer departmentId, String title, Integer chunkIndex, Integer pageNum) {
        Metadata metadata = new Metadata();
        metadata.put(META_CHUNK_ID, String.valueOf(chunkId));
        metadata.put(META_DOC_ID, String.valueOf(docId));
        metadata.put(META_BOOK_ID, String.valueOf(bookId));
        metadata.put(META_USER_ID, String.valueOf(userId));
        metadata.put(META_VISIBILITY, String.valueOf(visibility == null ? KnowledgeConstants.Visibility.PRIVATE : visibility));
        metadata.put(META_DEPARTMENT_ID, String.valueOf(departmentId));
        metadata.put(META_TITLE, title == null ? "" : title);
        metadata.put(META_CHUNK_INDEX, String.valueOf(chunkIndex));
        metadata.put(META_PAGE_NUM, String.valueOf(pageNum));
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
        searchResult.setUserId(parseInt(metadata.getString(META_USER_ID)));
        searchResult.setTitle(metadata.getString(META_TITLE));
        searchResult.setContent(segment.text());
        searchResult.setChunkIndex(parseInt(metadata.getString(META_CHUNK_INDEX)));
        searchResult.setPageNum(parseInt(metadata.getString(META_PAGE_NUM)));
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
