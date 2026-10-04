package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.KpCategoryMapper;
import com.mo.corecraft.model.entity.KpCategory;
import com.mo.corecraft.model.query.KpCategoryQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KpCategoryRepository extends AbstractRepository<KpCategory, KpCategoryQuery, KpCategoryMapper> {

    public KpCategoryRepository(KpCategoryMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<KpCategoryQuery, LambdaQueryWrapper<KpCategory>> buildQueryWrapper(KpCategoryQuery query) {
        var wrapper = new LambdaQueryWrapper<KpCategory>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(KpCategory::getId, id))
                .ifNotNull(query.getUserId(), (w, uid) -> w.eq(KpCategory::getUserId, uid))
                .ifNotBlank(query.getName(), (w, n) -> w.eq(KpCategory::getName, n))
                .when(true, (w, q) -> w.orderByAsc(KpCategory::getId));
    }

    /** 按 (user, name) 找目录；用于建目录/改名时查重 */
    public Optional<KpCategory> findByKey(Long userId, String name) {
        KpCategoryQuery q = new KpCategoryQuery();
        q.setUserId(userId);
        q.setName(name);
        return find(q, e -> e);
    }

    /** 列出该用户的所有目录，按创建顺序 */
    public List<KpCategory> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<KpCategory>()
                .eq(KpCategory::getUserId, userId)
                .orderByAsc(KpCategory::getId));
    }

    /** 删目录前先把挂在它下面的知识点放回"未归类" */
    public void clearTopicCategory(Long categoryId) {
        mapper.clearTopicCategory(categoryId);
    }
}
