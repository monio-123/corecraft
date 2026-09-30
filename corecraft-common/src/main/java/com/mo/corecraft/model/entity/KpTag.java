package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 标签主数据（独立实体；按 user 隔离；同 (user, name) 唯一）。
 * <p>
 * 改 tag 名 = UPDATE kp_tag 一行；不要把 name 重复存到关联表里。
 */
@Getter
@Setter
@TableName("kp_tag")
public class KpTag extends BaseEntity {

    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;
}
