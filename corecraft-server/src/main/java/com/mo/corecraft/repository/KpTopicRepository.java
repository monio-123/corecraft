package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
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
                // 标签：EXISTS 子查询。关联表 + 标签表 join 一次，
                // 比"先查 tagId 再 IN 查 topic"少一次往返。
                .ifNotBlank(query.getTag(), (w, tag) -> w.exists(
                        "SELECT 1 FROM kp_topic_tag tt JOIN kp_tag g ON g.id = tt.tag_id"
                                + " WHERE tt.topic_id = kp_topic.id AND g.name = {0} AND g.user_id = {1}",
                        tag, query.getUserId()))
                // 关键词：标题**和**正文都搜（和前端原来那条规则一样）
                .ifNotBlank(query.getKeyword(), (w, kw) -> w.and(x -> x
                        .like(KpTopic::getTitle, kw)
                        .or()
                        .like(KpTopic::getContent, kw)))
                // 排序**必须带 id 兜底**：一批生成的条目 create_time 落在同一秒，
                // 只按 create_time 排的话次序是随机的 ⇒ **分页会漏条 / 重条**。
                .when(true, (w, q) -> w.orderByDesc(KpTopic::getCreateTime).orderByDesc(KpTopic::getId));
    }

    /**
     * 结构索引：全部知识点，**不含正文**（见 KpTopicMapper.xml 的说明）。
     *
     * @param keyword 关键词（标题 + 正文都搜）；空 = 不筛
     */
    public List<KpTopicIndexResp> listIndex(Long userId, String keyword) {
        // 归一走 KpTopicQuery 那一份 —— 索引这条 SQL 和列表那条 wrapper
        // 必须拿到**同一个字符串**，否则两个接口的条数会漂（详见 KpTopicQuery#keyword）。
        return mapper.selectIndex(userId, KpTopicQuery.normalizeKeyword(keyword));
    }

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
}
