package com.example.bigevent.mapper;

import com.example.bigevent.domain.KnowledgeDoc;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeDocMapper {

    @Insert("insert into knowledge_doc(create_user, file_name, file_type, file_url, file_size, file_md5, content, chunk_count, status, visibility, department_id, fail_reason, create_time, update_time) " +
            "values(#{createUser}, #{fileName}, #{fileType}, #{fileUrl}, #{fileSize}, #{fileMd5}, #{content}, #{chunkCount}, #{status}, #{visibility}, #{departmentId}, #{failReason}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeDoc doc);

    @Update("update knowledge_doc set file_name=#{fileName}, file_type=#{fileType}, file_url=#{fileUrl}, " +
            "file_size=#{fileSize}, file_md5=#{fileMd5}, content=#{content}, chunk_count=#{chunkCount}, status=#{status}, " +
            "visibility=#{visibility}, department_id=#{departmentId}, fail_reason=#{failReason}, update_time=now() where id=#{id}")
    void update(KnowledgeDoc doc);

    @Select("select * from knowledge_doc where id = #{id}")
    KnowledgeDoc findById(Long id);

    @Select("select * from knowledge_doc where file_md5 = #{fileMd5} limit 1")
    KnowledgeDoc findByFileMd5(String fileMd5);

    /**
     * 查询当前用户有权限查看的所有文档：自己的 + 同部门 + 公共
     */
    @Select("select * from knowledge_doc where create_user = #{userId} or visibility = 2 " +
            "or (visibility = 1 and department_id = #{departmentId}) order by create_time desc")
    List<KnowledgeDoc> findAuthorizedAll(@Param("userId") Integer userId, @Param("departmentId") Integer departmentId);

    @Delete("delete from knowledge_doc where id = #{id}")
    void deleteById(Long id);

    /**
     * 按文件名关键词模糊搜索当前用户有权限查看的文档。
     */
    @Select("select * from knowledge_doc " +
            "where (create_user = #{userId} or visibility = 2 or (visibility = 1 and department_id = #{departmentId})) " +
            "and file_name like concat('%', #{keyword}, '%') " +
            "order by create_time desc")
    List<KnowledgeDoc> findAuthorizedByKeyword(@Param("userId") Integer userId,
                                               @Param("departmentId") Integer departmentId,
                                               @Param("keyword") String keyword);

    /**
     * 按 ID 查询文档，并校验当前用户是否有查看权限。
     */
    @Select("select * from knowledge_doc " +
            "where id = #{id} and (create_user = #{userId} or visibility = 2 or (visibility = 1 and department_id = #{departmentId})) " +
            "limit 1")
    KnowledgeDoc findAuthorizedById(@Param("id") Long id,
                                    @Param("userId") Integer userId,
                                    @Param("departmentId") Integer departmentId);

    /**
     * 分页查询当前用户有权限查看的文档。
     */
    @Select("select * from knowledge_doc " +
            "where (create_user = #{userId} or visibility = 2 or (visibility = 1 and department_id = #{departmentId})) " +
            "order by create_time desc " +
            "limit #{limit} offset #{offset}")
    List<KnowledgeDoc> findAuthorizedPage(@Param("userId") Integer userId,
                                          @Param("departmentId") Integer departmentId,
                                          @Param("limit") Integer limit,
                                          @Param("offset") Integer offset);
}
