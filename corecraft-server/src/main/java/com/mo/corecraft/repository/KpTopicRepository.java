package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

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
                .ifNotNull(query.getTreeId(), (w, treeId) -> w.eq(KpTopic::getTreeId, treeId))
                .when(true, (w, q) -> w.orderByDesc(KpTopic::getCreateTime));
    }
}
