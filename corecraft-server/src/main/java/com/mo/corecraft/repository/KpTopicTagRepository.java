package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicTagMapper;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.query.KpTopicTagQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KpTopicTagRepository extends AbstractRepository<KpTopicTag, KpTopicTagQuery, KpTopicTagMapper> {

    public KpTopicTagRepository(KpTopicTagMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpTopicTagQuery, LambdaQueryWrapper<KpTopicTag>> buildQueryWrapper(KpTopicTagQuery query) {
        var wrapper = new LambdaQueryWrapper<KpTopicTag>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTopicTag::getId, id))
                .ifNotNull(query.getTopicId(), (w, tid) -> w.eq(KpTopicTag::getTopicId, tid))
                .ifNotNull(query.getTagId(), (w, tagId) -> w.eq(KpTopicTag::getTagId, tagId));
    }

    public List<KpTopicTag> listByTopicId(Long topicId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTopicTag>()
                .eq(KpTopicTag::getTopicId, topicId));
    }

    public List<KpTopicTag> listByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KpTopicTag>()
                .in(KpTopicTag::getTopicId, topicIds));
    }

    public List<KpTopicTag> listByTagId(Long tagId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTopicTag>()
                .eq(KpTopicTag::getTagId, tagId));
    }

    public void deleteByTopicId(Long topicId) {
        // 走物理删：纯关联表不留墓碑，否则重新插入相同 (topic_id, tag_id) 会撞唯一键
        mapper.physicalDeleteByTopicId(topicId);
    }

    public void deleteByTagId(Long tagId) {
        mapper.physicalDeleteByTagId(tagId);
    }
}
