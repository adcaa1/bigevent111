package com.example.bigevent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门列表展示对象，包含部门人数
 */
@Data
public class DepartmentVO {

    private Integer id;
    private String name;
    private LocalDateTime createTime;

    /**
     * 该部门下的用户数量
     */
    private Long memberCount;
}
