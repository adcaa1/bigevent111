package com.example.bigevent.mapper;

import com.example.bigevent.domain.KnowledgeImage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeImageMapper {

    @Insert("insert into knowledge_image(doc_id, chunk_id, chunk_index, image_path, description, page_num, create_time) " +
            "values(#{docId}, #{chunkId}, #{chunkIndex}, #{imagePath}, #{description}, #{pageNum}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeImage image);

    @Select("select * from knowledge_image where doc_id = #{docId} order by chunk_index")
    List<KnowledgeImage> findByDocId(Long docId);

    @Delete("delete from knowledge_image where doc_id = #{docId}")
    void deleteByDocId(Long docId);

    @Update("update knowledge_image set chunk_id = #{chunkId} where id = #{id}")
    void updateChunkId(@Param("id") Long id, @Param("chunkId") Long chunkId);
}
