package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTagMapper;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.query.KpTagQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KpTagRepository extends AbstractRepository<KpTag, KpTagQuery, KpTagMapper> {

    public KpTagRepository(KpTagMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpTagQuery, LambdaQueryWrapper<KpTag>> buildQueryWrapper(KpTagQuery query) {
        var wrapper = new LambdaQueryWrapper<KpTag>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTag::getId, id))
                .ifNotNull(query.getUserId(), (w, uid) -> w.eq(KpTag::getUserId, uid))
                .ifNotBlank(query.getName(), (w, n) -> w.eq(KpTag::getName, n))
                .when(true, (w, q) -> w.orderByAsc(KpTag::getId));
    }

    /** 按 (user, name) 找 tag；用于 saveTopicTags 时 upsert 查重 */
    public Optional<KpTag> findByKey(Long userId, String name) {
        KpTagQuery q = new KpTagQuery();
        q.setUserId(userId);
        q.setName(name);
        return find(q, e -> e);
    }

    /** 列出该用户的所有 tag（含不被任何 topic 引用的"孤儿"） */
    public List<KpTag> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTag>()
                .eq(KpTag::getUserId, userId)
                .orderByAsc(KpTag::getId));
    }

    /** 批量按 id 查 tag（enrichTopicResps 用；标签是按 user 隔离的，调用方需保证 tagIds 来自同一 user） */
    public List<KpTag> listByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectBatchIds(tagIds);
    }
}
