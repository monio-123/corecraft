package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryImportReq;
import com.mo.corecraft.model.req.KpCategoryRenameReq;
import com.mo.corecraft.model.resp.KpCategoryDeletePreviewResp;
import com.mo.corecraft.model.resp.KpCategoryResp;

import java.util.List;

public interface KpCategoryService {

    List<KpCategoryResp> listCategories(Long userId);

    /** 新建目录；重名抛异常 */
    void createCategory(KpCategoryCreateReq req, Long userId);

    /**
     * **「笔记」目录**（随手记的家）的 id，**没有就懒建一个**（2026-11-16 D3）。
     *
     * <p>它是**唯一内置**的目录，但**没有系统级保护**：普通身份——用户可以改名、可以删；
     * 删了下次再记一条就懒建回来（名字固定「笔记」）。
     *
     * <p>⚠️ **为什么要有这个**：随手记要有个"家"，而不是落进"待归类"那个债桶里
     * （那里的语义是"欠着的"，实际内容是删树遗留的孤儿）。
     * 有了家之后，「笔记」就是**顶层叶子目录** ⇒ 它的内容天然是平铺的一层，
     * 不参与书里的层级。
     *
     * <p>⚠️ **懒建而非预置**：预置意味着每个新用户都要插一行、还要处理"用户删了它怎么办"，
     * 而懒建只在**真的记第一条**时才建 ⇒ 没有用户用就一行都没有。
     */
    Long ensureNotesCategory(Long userId);

    /**
     * 批量导入目录树：**一次提交整棵树，一个事务里建完**。
     *
     * <p>目录原来只能一个一个建，导入一棵几十个节点的树就是几十次请求 ——
     * 中途失败留半棵树，重复导入也做不了幂等。
     *
     * <p>**只导目录**（节点里没有知识点正文）。同层重名由唯一键挡着，
     * 报的是"同一层下已有同名目录"（和单个建目录同一句话）。
     *
     * <p>**返回这批目录的 id**（新建的和合并复用的都算）：调用方要拿 id
     * 把知识点挂到具体目录下 —— 目录名会变、还可能撞名，**id 才是身份**。
     *
     * @return 本次涉及的所有目录 `{ id, name, parentId }`（前序顺序）
     */
    List<KpCategoryResp> importTree(KpCategoryImportReq req, Long userId);

    /** 重命名目录；新名被占用抛异常 */
    void renameCategory(KpCategoryRenameReq req, Long userId);

    /**
     * 移动目录到新父级（newParentId 传 null / 0 = 移到顶层）。
     *
     * **只是改一个 parent_id 字段**：下级目录靠 parent_id 引用、知识点靠 category_id 引用，
     * 所以"整棵树跟着移动"是自动成立的，不用搬任何数据。落库就是一行 UPDATE。
     *
     * @throws CoreCraftException 目录不存在 / 移到自己的下级（成环）/ 目标下已有同名
     */
    void moveCategory(Long categoryId, Long newParentId, Long userId);

    /**
     * 删除目录，**整棵子树一并删**；子树里每一层的知识点都回到"未归类"（不删知识点）。
     *
     * <p>2026-10-07 之前的行为是"子目录上提到被删节点的父级"，用户明确否掉了：
     * 删一个目录不该顺手留下"它原来那些孩子跑到同级了"这种结果。
     *
     * @param cascadeTopics **true = 连这棵子树里的知识点一起删**（软删）。
     *        false = 知识点回到"未归类"，一条不删（默认行为）。
     *        <p>为什么要有这个开关：删目录后的待归类是**堆积源** —— 目录树改了，
     *        里面的知识点却全部掉进"待归类"桶里，越整理越脏。而批量清理待归类
     *        在服务端分页下语义不清（"全选"是当前页还是全部？）。
     *        <p>⇒ 从**源头**堵：删目录时让用户明确选择"这些知识点一起删掉"。
     *        默认不删 —— 目录只是归类的壳，整理结构不该默认丢知识。
     */
    void deleteCategory(Long categoryId, Long userId, boolean cascadeTopics);

    /**
     * 「删这个目录会连带删掉什么」的**预检**：数清楚整棵子树里的目录数和知识点数。
     *
     * <p>这两个数字要摆在**不可逆操作**的确认框里 ⇒ 必须来自服务端现查，
     * 不能用前端的缓存（那份是当前关键词下的快照，还会过期）。
     */
    KpCategoryDeletePreviewResp previewDelete(Long categoryId, Long userId);
}
