package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicResp;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface KpTopicService {

    /**
     * 列表页的知识点：**分页**返回，**带正文**。
     *
     * <p>为什么不一次返回全部（2026-10-27 性能诊断）：content 是 TEXT 列，
     * 636 条加起来 90 万字符，**JDBC 逐行读回它们要 4.4 秒**（实测）——
     * 侧边栏和列表页都等不起。分页一页 20 条只要 0.34 秒。
     *
     * <p>筛选条件（目录范围 / 类型 / 标签 / 关键词）都在 {@link KpTopicQuery} 里，
     * **服务端筛** —— 浏览器那份轻量索引没有正文，筛不了正文。
     */
    IPage<KpTopicResp> selectTopicPage(Page<KpTopic> page, KpTopicQuery query);

    /**
     * 结构索引：全部知识点，**不含正文**（只有 id / 标题 / 目录 / 类型 / 标签 / 有无正文）。
     *
     * <p>目录树要在知识点那一层显示标题、各处要按类型/标签/有无正文分类计数 ——
     * 这些都要"全集"，但**一条正文都不需要**。实测读全文 4416ms、不读正文 78ms。
     *
     * @param keyword 关键词（标题 + 正文都搜）；空 = 不筛。
     *               **带关键词时返回的是"关键词下的全集"** —— 浏览器那边按它算分面计数，
     *               数字才和"点进去看到多少条"对得上。
     */
    List<KpTopicIndexResp> selectTopicIndex(Long userId, String keyword);

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
