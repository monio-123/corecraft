package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryRenameReq;
import com.mo.corecraft.model.resp.KpCategoryResp;

import java.util.List;

public interface KpCategoryService {

    List<KpCategoryResp> listCategories(Long userId);

    /** 新建目录；重名抛异常 */
    void createCategory(KpCategoryCreateReq req, Long userId);

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
     */
    void deleteCategory(Long categoryId, Long userId);
}
