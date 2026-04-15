package com.example.bigevent.mapper;

import org.apache.ibatis.jdbc.SQL;

import static org.apache.ibatis.jdbc.SqlBuilder.FROM;

public class ArticlePrivoder {
    public String list(
            Integer id,
            Integer categoryId,
             String state

    ) {
        return new SQL() {{
            SELECT("*");
            FROM("article");

            if (categoryId != null) {
                AND().WHERE("category_id = #{categoryId}");
            }

            if (state != null) {
                AND().WHERE("state = #{state}");
            }

            WHERE("create_user = #{id}");
        }}.toString();
    }

}
