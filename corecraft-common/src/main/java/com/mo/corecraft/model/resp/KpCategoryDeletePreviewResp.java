package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 「删这个目录会连带删掉什么」的**预检结果**。
 *
 * <p>为什么要有这个接口（而不是前端自己数）：
 * <ul>
 *   <li>前端那份结构索引是**缓存快照**，而且是**当前关键词下**的 ——
 *       用户搜着词的时候数出来的条数偏小；</li>
 *   <li>而且它可能**过期**（刚建完目录、刚加的知识点还没进缓存）。</li>
 * </ul>
 *
 * <p>这个数字要摆在**不可逆操作**的确认框里 —— 数错了用户就是"以为删 3 条、实际删 30 条"。
 * ⇒ 数字必须来自**服务端按 userId 现查**，不能是前端缓存里的一份。
 */
@Getter
@Setter
public class KpCategoryDeletePreviewResp {

    /** 要删的目录 id（回显用） */
    private Long categoryId;

    /** 目录名（回显用） */
    private String categoryName;

    /** **整棵子树**里除自己以外的目录数 */
    private Integer subCategoryCount;

    /** **整棵子树**里挂着的知识点条数（**所有层级**，不只是直接下级） */
    private Integer topicCount;
}
