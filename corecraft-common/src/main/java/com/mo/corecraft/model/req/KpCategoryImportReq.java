package com.mo.corecraft.model.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量导入目录树：一棵（或几棵）目录结构，一次建完。
 *
 * <p>**为什么要有这个接口**：目录原来只能一个一个建（{@code POST /kp/category}），
 * 导入一棵几十个节点的树就是几十次请求 —— 中途失败留半棵树，幂等也做不了。
 * 这个接口**一次提交整棵树、服务端在一个事务里建完**，要么全成、要么全回滚。
 *
 * <p>节点结构见 {@link KpCategoryImportNode}：**只导目录，不带知识点**。
 */
@Getter
@Setter
public class KpCategoryImportReq {

    /** 顶层节点（一个顶层节点 = 一棵树的根） */
    @NotEmpty
    @Valid
    private List<KpCategoryImportNode> nodes;

    /**
     * 挂在哪个已有目录下面（0 = 挂在根下）。
     *
     * <p>有了它就能"在 语言/java 下补一片"——树的第一层建在这个目录底下，
     * 深度也按**真实层数**算（挂在第三层下面 = 新目录是第四层）。
     *
     * <p>不传（null）当 0 处理：前端没挂载点时就不传这个字段。
     */
    private Long parentId;
}
