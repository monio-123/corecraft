package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpTopicQuizMapper;
import com.mo.corecraft.model.entity.KpTopicQuiz;
import com.mo.corecraft.model.query.KpTopicQuizQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class KpTopicQuizRepository extends AbstractRepository<KpTopicQuiz, KpTopicQuizQuery, KpTopicQuizMapper> {

    public KpTopicQuizRepository(KpTopicQuizMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpTopicQuizQuery, LambdaQueryWrapper<KpTopicQuiz>> buildQueryWrapper(KpTopicQuizQuery query) {
        var wrapper = new LambdaQueryWrapper<KpTopicQuiz>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpTopicQuiz::getId, id))
                .ifNotNull(query.getTopicId(), (w, topicId) -> w.eq(KpTopicQuiz::getTopicId, topicId))
                .when(true, (w, q) -> w.orderByAsc(KpTopicQuiz::getSort).orderByAsc(KpTopicQuiz::getId));
    }

    public List<KpTopicQuiz> listByTopicId(Long topicId) {
        return mapper.selectList(new LambdaQueryWrapper<KpTopicQuiz>()
                .eq(KpTopicQuiz::getTopicId, topicId)
                .orderByAsc(KpTopicQuiz::getSort)
                .orderByAsc(KpTopicQuiz::getId));
    }

    public List<KpTopicQuiz> listByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<KpTopicQuiz>()
                .in(KpTopicQuiz::getTopicId, topicIds)
                .orderByAsc(KpTopicQuiz::getSort)
                .orderByAsc(KpTopicQuiz::getId));
    }

    public void deleteByTopicId(Long topicId) {
        // 走物理删：纯关联表不留墓碑
        mapper.physicalDeleteByTopicId(topicId);
    }
}
