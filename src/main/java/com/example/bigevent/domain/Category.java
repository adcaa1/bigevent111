package com.example.bigevent.domain;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    @NotNull(groups = Update.class)
//    只用更新组，才调用这个notnull
    private Integer id;//主键ID
    @NotEmpty
    //@NotEmpty(groups = {Add.class, Update.class})
//    这种没加group的，表明是默认的参数校验
    private String categoryName;//分类名称
    @NotEmpty
    private String categoryAlias;//分类别名
    private Integer createUser;//创建人ID
    private LocalDateTime createTime;//创建时间
    private LocalDateTime updateTime;//更新时间
    //如果说某个校验项没有指定分组,默认属于Default分组
    //分组之间可以继承, A extends B  那么A中拥有B中所有的校验项
    //这样就不用一个一个加了@NotEmpty(groups = {Add.class, Update.class})
    // 新增组
    public interface Add extends Default {

    }
    // 更新组
    public interface Update extends Default{

    }
}
