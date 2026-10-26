package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
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
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTopic::getId, id))
                .ifNotNull(query.getUserId(), (w, userId) -> w.eq(KpTopic::getUserId, userId))
                .ifNotBlank(query.getTitle(), (w, title) -> w.like(KpTopic::getTitle, title))
                .ifNotNull(query.getParentTopicId(), (w, pid) -> w.eq(KpTopic::getParentTopicId, pid))
                .ifNotNull(query.getCategoryId(), (w, cid) -> w.eq(KpTopic::getCategoryId, cid))
                .when(true, (w, q) -> w.orderByDesc(KpTopic::getCreateTime));
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
