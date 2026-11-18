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

    /**
     * 按 (user, parent, name) 找目录 —— 这三个字段就是唯一键 uk_user_parent_name。
     * 只在**同一层**找：不同父目录下的同名目录（操作系统/内存 和 计算机网络/内存）互不冲突。
     * 用于建目录/改名时的同级重名检查。
     *
     * 直接走 mapper 而不是复用 find(query)：parentId 只是这个查询的一个条件，
     * 为它给 KpCategoryQuery 加字段会让"全量查询"这个通用入口多出一个
     * 只在这里有意义的字段。LIMIT 1 是防 selectOne 在有重复时抛异常——
     * 唯一键在正常情况下保证不会重复，但改键的迁移窗口内可能短暂存在。
     */
    public Optional<KpCategory> findByKey(Long userId, Long parentId, String name) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KpCategory>()
                .eq(KpCategory::getUserId, userId)
                .eq(KpCategory::getParentId, parentId)
                .eq(KpCategory::getName, name)
                .last("LIMIT 1")));
    }

    /** 列出该用户的所有目录，按创建顺序 */
    public List<KpCategory> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<KpCategory>()
                .eq(KpCategory::getUserId, userId)
                .orderByAsc(KpCategory::getId));
    }

    /** 该目录下是否已有子目录（非叶子判据） */
    public boolean hasChildren(Long categoryId, Long userId) {
        if (categoryId == null || KpCategory.ROOT_ID.equals(categoryId)) {
            return false;
        }
        return mapper.selectCount(new LambdaQueryWrapper<KpCategory>()
                .eq(KpCategory::getUserId, userId)
                .eq(KpCategory::getParentId, categoryId)) > 0;
    }

    /**
     * 删目录前把挂在**这一批**目录下面的知识点放回"未归类"。
     * 传整棵子树的 id —— 见 KpCategoryMapper#clearTopicCategory 的注释。
     */
    public void clearTopicCategory(List<Long> categoryIds) {
        if (!categoryIds.isEmpty()) {
            mapper.clearTopicCategory(categoryIds);
        }
    }

    /**
     * **「笔记」目录**（随手记的家）：顶层同名，没有就建一个（返回 id）。
     *
     * <p>⚠️ **名字只有这一处**（常量在下面）——两个调用方（目录服务对外提供出口、
     * 知识点服务新建时兜底落点）都走这一个方法 ⇒ 改名字不会只改一边。
     *
     * <p>⚠️ **为什么在仓储层而不是 service 层**：**目录服务已经依赖知识点服务**
     * （删目录要清知识点）⇒ 知识点服务再依赖目录服务就成环了（Spring 启动直接失败）。
     * 下沉到仓储层，两边都只依赖它 ⇒ 没有环。
     */
    public Long ensureNotesCategory(Long userId) {
        var existing = findByKey(userId, KpCategory.ROOT_ID, NOTES_CATEGORY_NAME);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        KpCategory entity = new KpCategory();
        entity.setUserId(userId);
        entity.setName(NOTES_CATEGORY_NAME);
        entity.setParentId(KpCategory.ROOT_ID);
        insert(entity);
        return entity.getId();
    }

    /**
     * 「笔记」目录的名字（随手记的家，2026-11-16 D3）。
     *
     * <p>⚠️ **只是名字，不是系统标记**：这个目录没有特殊待遇——可改名、可删，
     * 删了下次记一条再懒建回来（名字固定「笔记」）。
     * 真正的"内置"是**它是唯一默认落点**：随手记进它，其余内容由用户自己归置。
     */
    private static final String NOTES_CATEGORY_NAME = "笔记";
}
