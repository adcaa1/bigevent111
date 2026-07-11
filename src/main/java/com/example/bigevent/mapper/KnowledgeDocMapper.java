package com.example.bigevent.mapper;

import com.example.bigevent.domain.KnowledgeDoc;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeDocMapper {

    @Insert("insert into knowledge_doc(book_id, create_user, file_name, file_type, file_url, file_size, file_md5, content, chunk_count, status, visibility, fail_reason, create_time, update_time) " +
            "values(#{bookId}, #{createUser}, #{fileName}, #{fileType}, #{fileUrl}, #{fileSize}, #{fileMd5}, #{content}, #{chunkCount}, #{status}, #{visibility}, #{failReason}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeDoc doc);

    @Update("update knowledge_doc set book_id=#{bookId}, file_name=#{fileName}, file_type=#{fileType}, file_url=#{fileUrl}, " +
            "file_size=#{fileSize}, file_md5=#{fileMd5}, content=#{content}, chunk_count=#{chunkCount}, status=#{status}, " +
            "visibility=#{visibility}, fail_reason=#{failReason}, update_time=now() where id=#{id}")
    void update(KnowledgeDoc doc);

    @Select("select * from knowledge_doc where id = #{id}")
    KnowledgeDoc findById(Long id);

    @Select("select * from knowledge_doc where file_md5 = #{fileMd5} limit 1")
    KnowledgeDoc findByFileMd5(String fileMd5);

    @Select("select * from knowledge_doc where book_id = #{bookId} order by create_time desc")
    List<KnowledgeDoc> findByBookId(Long bookId);

    @Select("select * from knowledge_doc order by create_time desc")
    List<KnowledgeDoc> findAll();

    @Delete("delete from knowledge_doc where id = #{id}")
    void deleteById(Long id);
}
