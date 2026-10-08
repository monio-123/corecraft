package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpTagRenameReq;
import com.mo.corecraft.model.resp.KpTagResp;

import java.util.List;

public interface KpTagService {

    /** 列出当前用户的所有 tag（按 id 升序；含孤儿） */
    List<KpTagResp> listTags(Long userId);

    /** 重命名一个 tag：把 (oldCategory, oldName) 改为 (newCategory, newName)；所有持有该 tag 的 topic 自动跟着改 */
    void renameTag(KpTagRenameReq req, Long userId);

    /** 删除一个 tag：同时清理 kp_topic_tag 关联（k_topic 不会被级联删除，关联断了而已） */
    void deleteTag(Long tagId, Long userId);
}
