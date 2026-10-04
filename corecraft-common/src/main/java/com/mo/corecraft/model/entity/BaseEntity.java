package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BaseEntity {

    /**
     * 自动填充靠 {@code MybatisPlusConfig#metaObjectHandler}。
     *
     * <p>之前这两个字段是裸的，insert 靠 DDL 的 {@code DEFAULT CURRENT_TIMESTAMP} 兜住，
     * update 则是两头落空：MyBatis-Plus 把查出来的旧值原样写回 SET 子句，DDL 的
     * {@code ON UPDATE CURRENT_TIMESTAMP} 因为「已显式赋值」也不触发。收到 fill 上以后，
     * 继承 BaseEntity 的表自动带上时间戳，调用方不用记。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    @TableField("is_delete")
    private boolean deleted;
}
