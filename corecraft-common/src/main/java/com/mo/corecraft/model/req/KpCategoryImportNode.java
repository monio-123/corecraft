package com.mo.corecraft.model.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 导入树里的一个节点 = 一个目录。
 *
 * <p>**这次只导目录**（知识点是另一件事，另说）。所以节点里只有名字和下级，
 * 没有正文 —— 以后要"往目录里灌知识"，给这个类加个可选字段就行，
 * 前端的协议不用改（多传一个字段，后端多认一个字段）。
 */
@Getter
@Setter
public class KpCategoryImportNode {

    /** 目录名。同层不许重名（唯一键 user_id + parent_id + name） */
    @NotBlank
    @Size(max = 50, message = "目录名最长 50 个字")
    private String name;

    /** 下级目录 */
    @Valid
    private List<KpCategoryImportNode> children;
}
