package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicMasteryReq;
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
     * @param keyword 关键词；空 = 不筛。
     *               **带关键词时返回的是"关键词下的全集"** —— 浏览器那边按它算分面计数，
     *               数字才和"点进去看到多少条"对得上。
     * @param scope   搜哪一块（标题 / 正文 / 都搜，见 {@link KpTopicQuery#normalizeScope}）。
     */
    List<KpTopicIndexResp> selectTopicIndex(Long userId, String keyword, String scope);

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
     * 新建一条知识点，**返回它的 id**。
     *
     * <p>id 是给「命名任务」用的：随手记建完要立刻交给后台命名（生成标题），
     * 那条路得知道是哪一条 ⇒ id 由这一层给，不让前端从刷新后的列表里 diff。
     */
    Long createTopic(KpTopicCreateReq req, Long userId);

    void updateTopic(KpTopicUpdateReq req, Long userId);

    /**
     * 设置这条内容的**掌握度参与开关**（单条覆盖）。
     *
     * <p>三值语义：{@code true}=参与 / {@code false}=不参与 / {@code null}=恢复继承类型默认。
     * 详见 {@link KpTopicMasteryReq}（那里解释了为什么单独一个端点）。
     */
    void setTopicMasteryEnabled(KpTopicMasteryReq req, Long userId);

    void deleteTopic(Long id, Long userId);

    /**
     * 删掉挂在**这一批目录**下面的全部知识点（软删）。
     *
     * <p>给「删目录时连知识点一起删」用：目录树改了，里面的知识点不该整批掉进
     * "待归类"桶里——那是**堆积源**，越整理越脏。
     *
     * <p>⚠️ **归属校验在这一层做**：只删 {@code user_id = 本人} 的那些。
     * 目录本身已经按 userId 校验过（{@code KpCategoryService#deleteCategory}），
     * 但知识点还得**再收一遍** —— 万一库里有一条 category_id 指向别人的目录
     * （脏数据），那条不该被连带删掉。
     *
     * @param categoryIds 目录 id 的一批（整棵子树）
     * @return 实际删掉的条数
     */
    int deleteTopicsByCategoryIds(List<Long> categoryIds, Long userId);

    void addTopicRelation(Long topicId, Long relatedTopicId, Long userId);

    void removeTopicRelation(Long topicId, Long relatedTopicId, Long userId);
}
