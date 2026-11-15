package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KpTopicRepository extends AbstractRepository<KpTopic, KpTopicQuery, KpTopicMapper> {

    public KpTopicRepository(KpTopicMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpTopicQuery, LambdaQueryWrapper<KpTopic>> buildQueryWrapper(KpTopicQuery query) {
        var wrapper = new LambdaQueryWrapper<KpTopic>();
        // 未归类是更窄的条件：两个都传时以它为准（前端本来也只发一个）
        boolean onlyUncategorized = Boolean.TRUE.equals(query.getUncategorized());
        boolean hasCategoryIds = query.getCategoryIds() != null && !query.getCategoryIds().isEmpty();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTopic::getId, id))
                .ifNotNull(query.getUserId(), (w, userId) -> w.eq(KpTopic::getUserId, userId))
                .when(onlyUncategorized, (w, q) -> w.isNull(KpTopic::getCategoryId))
                .when(!onlyUncategorized && hasCategoryIds,
                        (w, q) -> w.in(KpTopic::getCategoryId, q.getCategoryIds()))
                .ifNotNull(query.getTypeId(), (w, typeId) -> w.eq(KpTopic::getTypeId, typeId))
                // 时间区间（列表页的「今天 / 本周 / 本月」范围）。
                // 起止由前端按本地时区算好传过来 ⇒ 两端口径一致，不会差一天。
                // ⚠️ **前端那边还有一份同口径的筛选**（结构索引上按 createTime 自己筛，
                // 用来算分面数字）⇒ 两边算出来的起止必须是同一对值，否则数字会和条数对不上。
                .ifNotNull(query.getCreatedFrom(), (w, from) -> w.ge(KpTopic::getCreateTime, from))
                .ifNotNull(query.getCreatedTo(), (w, to) -> w.le(KpTopic::getCreateTime, to))
                // 标签：EXISTS 子查询。关联表 + 标签表 join 一次，
                // 比"先查 tagId 再 IN 查 topic"少一次往返。
                .ifNotBlank(query.getTag(), (w, tag) -> w.exists(
                        "SELECT 1 FROM kp_topic_tag tt JOIN kp_tag g ON g.id = tt.tag_id"
                                + " WHERE tt.topic_id = kp_topic.id AND g.name = {0} AND g.user_id = {1}",
                        tag, query.getUserId()))
                // 关键词：**搜哪一块由 scope 决定**（标题 / 正文 / 都搜）。
                // ⚠️ **"这一档搜哪几块"的推导只有一处**（{@link #scopeKeywords}）——
                // 索引那条 SQL（手写 XML）和这条 wrapper 共用它
                // ⇒ 两边口径不可能漂（原来两边各写一遍 scope 的分支：
                // 改一边另一边就漂 ⇒ 侧边栏按正文计数、列表按标题筛，
                // 数字对不上，**而界面上什么异常都看不到**）。
                .ifNotBlank(query.getKeyword(), (w, kw) -> {
                    ScopeKeywords sk = scopeKeywords(query.getScope(), kw);
                    if (sk.title() != null && sk.content() != null) {
                        w.and(x -> x
                                .like(KpTopic::getTitle, sk.title())
                                .or()
                                .like(KpTopic::getContent, sk.content()));
                    } else if (sk.title() != null) {
                        w.like(KpTopic::getTitle, sk.title());
                    } else if (sk.content() != null) {
                        w.like(KpTopic::getContent, sk.content());
                    }
                })
                // 排序**必须带 id 兜底**：一批生成的条目 create_time 落在同一秒，
                // 只按 create_time 排的话次序是随机的 ⇒ **分页会漏条 / 重条**。
                .when(true, (w, q) -> w.orderByDesc(KpTopic::getCreateTime).orderByDesc(KpTopic::getId));
    }

    /**
     * 结构索引：全部知识点，**不含正文**（见 KpTopicMapper.xml 的说明）。
     *
     * @param keyword 关键词；空 = 不筛
     * @param scope   搜哪一块（标题 / 正文 / 都搜，见 {@link KpTopicQuery#normalizeScope}）
     */
    public List<KpTopicIndexResp> listIndex(Long userId, String keyword, String scope) {
        // ⚠️ **scope →「搜哪几块」的推导只有这一处**（scopeKeywords）——
        // 索引这条 SQL 和列表那条 wrapper 共用它 ⇒ 两边的口径不可能漂。
        ScopeKeywords sk = scopeKeywords(scope, keyword);
        return mapper.selectIndex(userId, sk.title(), sk.content());
    }

    /**
     * **这一档搜哪几块** → **要拿去 like 的关键词**（null = 这一块不搜）。
     *
     * <p>⚠️ **索引那条 SQL（手写 XML）和列表那条（MP wrapper）共用这一份推导** ——
     * 两边各写一遍 scope 的分支的话，改一边另一边就漂
     * （症状：侧边栏按正文计数、列表按标题筛 ⇒ 数字对不上，**而界面上什么异常都看不到**）。
     *
     * <p>⇒ 往下传的是**「搜哪几块」**（两个可空参数），不是 scope 本身 ——
     * XML 那边**不认得 scope 这个概念**，也就没有第二份分支可以漂。
     *
     * @param scope   这一档（标题 / 正文 / 都搜）
     * @param keyword 关键词（空 = 都不筛）
     */
    private static ScopeKeywords scopeKeywords(String scope, String keyword) {
        String sc = KpTopicQuery.normalizeScope(scope);
        String kw = KpTopicQuery.normalizeKeyword(keyword);
        boolean both = KpTopicQuery.SCOPE_BOTH.equals(sc);
        return new ScopeKeywords(
                (both || KpTopicQuery.SCOPE_TITLE.equals(sc)) ? kw : null,
                (both || KpTopicQuery.SCOPE_CONTENT.equals(sc)) ? kw : null);
    }

    /**
     * 一档搜索要 like 的关键词：**标题 / 正文**（各自 null = 这一块不搜）。
     */
    private record ScopeKeywords(String title, String content) {}

    /**
     * 按 id 批量取，**归属在 SQL 里一起收**（userId）。
     *
     * <p>为什么不用 {@code list(query)} + Java 过滤：那样"不是你的"会被查出来再丢掉，
     * 等于把它读进了内存。放在 where 里 ⇒ 不是你的根本查不到。
     *
     * <p>考一个目录（多条知识一起出题）用这个。
     */
    public List<KpTopic> listByIdsAndUserId(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KpTopic>()
                .in(KpTopic::getId, ids)
                .eq(KpTopic::getUserId, userId));
    }

    /**
     * 挂在这批目录下的知识点（**归属在 SQL 里一起收**，见 listByIdsAndUserId 的同一个理由）。
     *
     * <p>给「删目录时连知识点一起删」用：先按 (category_id ∈ 批次, user_id = 本人)
     * 把要删的那些 id 捞出来，再逐条走标签/关联的清理 + 软删。
     *
     * <p>⚠️ **只查要用的列**：这里要的是 id 一列，正文（TEXT）那几十 KB 一条都不需要。
     */
    public List<KpTopic> listByCategoryIdsAndUserId(List<Long> categoryIds, Long userId) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KpTopic>()
                .select(KpTopic::getId, KpTopic::getUserId, KpTopic::getCategoryId)
                .in(KpTopic::getCategoryId, categoryIds)
                .eq(KpTopic::getUserId, userId));
    }

    /**
     * 挂在这批目录下的知识点**条数**（**归属在 SQL 里一起收**）。
     *
     * <p>给「删目录会连带删掉多少条」那个确认框用 —— 数字必须是**服务端现查**的：
     * 前端那份结构索引是当前关键词下的缓存快照，还会过期，
     * 而这个数字要摆在**不可逆操作**的确认框里，数错了就是"以为删 3 条、实际删 30 条"。
     */
    public long countByCategoryIdsAndUserId(List<Long> categoryIds, Long userId) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0L;
        }
        return mapper.selectCount(new LambdaQueryWrapper<KpTopic>()
                .in(KpTopic::getCategoryId, categoryIds)
                .eq(KpTopic::getUserId, userId));
    }

    /**
     * 列表页的**一页**（轻量投影，**正文只取开头一段**当摘要）。
     *
     * <p>⚠️ **不走 {@code page(...)} 那条 MP 实体查询**：那条会把 content 整列取回来，
     * 而卡片只显示前 90 字的摘要 ⇒ 列表那次传输是白传输（用户 2026-11-09 报「接口有点慢」）。
     *
     * <p>⚠️ **过滤条件与 {@link #buildQueryWrapper} 逐条对齐**：目录范围 / 未归类 / 类型 /
     * 标签 / 关键词 / 时间区间，六项一模一样。关键词仍走
     * {@link #scopeKeywords}（**同一份推导**）⇒ 两边不可能漂。
     *
     * @param offset 从第几条起（0 起）
     * @param size  取几条
     */
    public List<KpTopicResp> listPageProjection(KpTopicQuery query, long offset, long size) {
        ScopeKeywords sk = scopeKeywords(query.getScope(), query.getKeyword());
        return mapper.selectPageProjection(
                query.getUserId(),
                query.getCategoryIds(),
                query.getUncategorized(),
                query.getTypeId(),
                query.getTag(),
                query.getKeyword(),
                sk.title(),
                sk.content(),
                query.getCreatedFrom(),
                query.getCreatedTo(),
                offset,
                size);
    }

    /**
     * 列表页的**总条数**（过滤条件与 {@link #listPageProjection} 完全一致 —— SQL 里共用同一个片段）。
     */
    public long countPageProjection(KpTopicQuery query) {
        ScopeKeywords sk = scopeKeywords(query.getScope(), query.getKeyword());
        return mapper.countPageProjection(
                query.getUserId(),
                query.getCategoryIds(),
                query.getUncategorized(),
                query.getTypeId(),
                query.getTag(),
                query.getKeyword(),
                sk.title(),
                sk.content(),
                query.getCreatedFrom(),
                query.getCreatedTo());
    }
}
