package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicRelationMapper;
import com.mo.corecraft.model.entity.KpTopicRelation;
import com.mo.corecraft.model.query.KpTopicRelationQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KpTopicRelationRepository extends AbstractRepository<KpTopicRelation, KpTopicRelationQuery, KpTopicRelationMapper> {

    public KpTopicRelationRepository(KpTopicRelationMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpTopicRelationQuery, LambdaQueryWrapper<KpTopicRelation>> buildQueryWrapper(KpTopicRelationQuery query) {
        var wrapper = new LambdaQueryWrapper<KpTopicRelation>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTopicRelation::getId, id))
                .ifNotNull(query.getTopicId(), (w, topicId) -> w.eq(KpTopicRelation::getTopicId, topicId))
                .ifNotNull(query.getRelatedTopicId(), (w, rid) -> w.eq(KpTopicRelation::getRelatedTopicId, rid))
                .when(true, (w, q) -> w.orderByAsc(KpTopicRelation::getId));
    }

    public List<KpTopicRelation> listByTopicId(Long topicId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTopicRelation>()
                .eq(KpTopicRelation::getTopicId, topicId));
    }

    public List<KpTopicRelation> listByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KpTopicRelation>()
                .in(KpTopicRelation::getTopicId, topicIds));
    }

    public void deleteByTopicId(Long topicId) {
        // 走物理删：纯关联表不留墓碑
        mapper.physicalDeleteByTopicId(topicId);
    }

    public void deleteByTopicIdAndRelatedTopicId(Long topicId, Long relatedTopicId) {
        // 走物理删：纯关联表不留墓碑（避免撞 uk_topic_relation 唯一键）
        mapper.physicalDeleteByTopicIdAndRelatedTopicId(topicId, relatedTopicId);
    }

    public List<KpTopicRelation> listByRelatedTopicId(Long relatedTopicId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTopicRelation>()
                .eq(KpTopicRelation::getRelatedTopicId, relatedTopicId));
    }
}
