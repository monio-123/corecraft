package com.mo.corecraft.service;

import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicResp;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface KpTopicService {

    List<KpTopicResp> selectTopicList(KpTopicQuery query);

    /**
     * 按 id 取一条知识点，**按 userId 收敛到本人**。
     *
     * <p>详情必须校验归属、列表已经校验（见 KpTopicController.list 里的 setUserId）——
     * 两条路径以前不一致，等于"知道 id 就能读别人的知识"。查不到一律 NOT_FOUND，
     * 不区分"不存在"和"不是你的"，避免泄露别人的数据存在性。
     */
    KpTopicResp selectTopicById(Long id, Long userId);

    /**
     * 按 id 批量取多条知识点，**按 userId 收敛到本人**（和 {@link #selectTopicById} 同一个规矩）。
     *
     * <p>考一个目录用这个：那个目录下的所有知识要一起交给模型出题。
     * 查不到的（不是你的）**直接查不出来**，不区分"不存在"和"不是你的"。
     */
    List<KpTopicResp> selectTopicsByIds(List<Long> ids, Long userId);

    /**
     * 这批知识各自的**类型名**（键 = 知识点 id，值 = 类型名，字典 {@code sys_dict_item.label}）。
     *
     * <p>出题要把材料**按类型分组**，组名模型得看得懂 ⇒ 用 label（"笔记""学习"），不能用 id。
     * 字典项 id 全局唯一，直接按 id 查 label 就行，不用先定位字典类型。
     *
     * <p>**typeId 为空 / 指向已删字典项的，按默认档算**（默认档 = 字典里排第一的那条，
     * 和前端 {@code defaultTopicTypeId()} 同一个口径——库里的注释也是"NULL=笔记，默认档"）。
     * 字典本身查不到时返回空串（那种情况下界面上的类型也是空的）。
     */
    Map<Long, String> typeNamesByTopic(Collection<KpTopicResp> topics);

    void createTopic(KpTopicCreateReq req, Long userId);

    void updateTopic(KpTopicUpdateReq req, Long userId);

    void deleteTopic(Long id, Long userId);

    void addTopicRelation(Long topicId, Long relatedTopicId);

    void removeTopicRelation(Long topicId, Long relatedTopicId);
}
