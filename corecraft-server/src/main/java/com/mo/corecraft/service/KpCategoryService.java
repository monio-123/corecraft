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

    /** 删除目录；其下知识点回到"未归类"（不删知识点） */
    void deleteCategory(Long categoryId, Long userId);
}
