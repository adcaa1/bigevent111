package com.example.bigevent.mapper;

import com.example.bigevent.domain.KnowledgeChunk;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {

    @Insert("insert into knowledge_chunk(doc_id, book_id, content, chunk_index, page_num, word_count, vector_key, create_time) " +
            "values(#{docId}, #{bookId}, #{content}, #{chunkIndex}, #{pageNum}, #{wordCount}, #{vectorKey}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeChunk chunk);

    @Select("select * from knowledge_chunk where doc_id = #{docId} order by chunk_index")
    List<KnowledgeChunk> findByDocId(Long docId);

    @Select("select * from knowledge_chunk where book_id = #{bookId} order by chunk_index")
    List<KnowledgeChunk> findByBookId(Long bookId);

    @Delete("delete from knowledge_chunk where doc_id = #{docId}")
    void deleteByDocId(Long docId);

    @Delete("delete from knowledge_chunk where book_id = #{bookId}")
    void deleteByBookId(Long bookId);

    @Update("update knowledge_chunk set vector_key = #{vectorKey} where id = #{id}")
    void updateVectorKey(@Param("id") Long id, @Param("vectorKey") String vectorKey);

    /**
     * 关键词检索：按内容模糊匹配
     */
    @Select("select * from knowledge_chunk where book_id = #{bookId} and content like concat('%', #{keyword}, '%') limit #{limit}")
    List<KnowledgeChunk> searchByKeyword(@Param("bookId") Long bookId,
                                         @Param("keyword") String keyword,
                                         @Param("limit") Integer limit);
}
