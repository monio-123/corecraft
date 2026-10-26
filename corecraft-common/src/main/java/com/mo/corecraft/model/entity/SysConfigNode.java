package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 配置树节点：一个分组（容器），或者一个 KV（叶子）。
 *
 * **这张表存的是"说明书"，值在 sys_config 里**：
 *   - 容器：value_type 为空，没有对应的键值对；
 *   - 叶子：value_type = number | text，值是一行 sys_config（config_key = 本节点的 config_key）。
 *
 * **code 是短名，config_key 是全名**：
 *   code       同一父下唯一（"这一项叫什么"的机器名）
 *   config_key 全树唯一 = 从根到本节点的 code 用 "." 连起来（"这一项的键"）
 *   根节点的 code 就是命名空间（ui / ai），所以 ui.side.sideWidth 天然就是"命名空间.组.项"。
 *
 * **code 和 config_key 建好之后不可改**：改了键就变了，已经存进去的值会变成孤儿。
 * 能改的只有名字、说明、排序（都不影响键）。
 *
 * **不继承 BaseEntity**：这张表没有 is_delete（节点删了再建即可），
 * create_time / update_time 交给 DDL 的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP。
 */
@Getter
@Setter
@TableName("sys_config_node")
public class SysConfigNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父节点；0 = 顶层（列是 NOT NULL，用基本类型省掉各处判空） */
    @TableField("parent_id")
    private long parentId;

    /** 显示名（界面上的名字） */
    private String name;

    /** 短名，同一父下唯一 */
    private String code;

    /** 完整配置键 = 从根到本节点的 code 用 "." 连起来 */
    @TableField("config_key")
    private String configKey;

    /** number | text；容器节点为空 */
    @TableField("value_type")
    private String valueType;

    /** 同级排序 */
    private Integer sort;

    /** 说明（给人看的）。范围、步长这类信息写在这儿 */
    @TableField(value = "remark", updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
