package com.mo.corecraft.service.impl;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpCategory;
import com.mo.corecraft.model.query.KpCategoryQuery;
import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryRenameReq;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.repository.KpCategoryRepository;
import com.mo.corecraft.service.KpCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KpCategoryServiceImpl implements KpCategoryService {

    private final KpCategoryRepository kpCategoryRepository;

    @Override
    public List<KpCategoryResp> listCategories(Long userId) {
        return kpCategoryRepository.listByUserId(userId).stream().map(c -> {
            KpCategoryResp r = new KpCategoryResp();
            r.setId(c.getId());
            r.setName(c.getName());
            // getParentId() 已把历史 NULL 归一成 0，前端只面对一种"顶层"表示
            r.setParentId(c.getParentId());
            return r;
        }).toList();
    }

    @Override
    @Transactional
    public void createCategory(KpCategoryCreateReq req, Long userId) {
        String name = req.getName().trim();
        // parentId 必须**先归一再查重**：查重用的是唯一键那三列，
        // 拿没归一的 parentId 去查会漏——库里历史数据有 parent_id 为 NULL 的。
        Long parentId = req.getParentId() == null ? KpCategory.ROOT_ID : req.getParentId();
        // 唯一键是 (user_id, parent_id, name)，所以只有**同一层**才不许重名。
        // 不同父目录下的同名目录（操作系统/内存 和 计算机网络/内存）是允许的。
        if (kpCategoryRepository.findByKey(userId, parentId, name).isPresent()) {
            throw new CoreCraftException(ResultCodeEnum.FAIL, "同一层下已有同名目录");
        }
        if (!KpCategory.ROOT_ID.equals(parentId)) {
            // 父目录必须存在且是本人的，否则能把目录挂到别人的树上去
            KpCategoryQuery pq = new KpCategoryQuery();
            pq.setId(parentId);
            pq.setUserId(userId);
            if (kpCategoryRepository.find(pq, e -> e).isEmpty()) {
                throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "父目录不存在");
            }
        }
        KpCategory entity = new KpCategory();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setParentId(parentId);
        kpCategoryRepository.insert(entity);
    }

    @Override
    @Transactional
    public void renameCategory(KpCategoryRenameReq req, Long userId) {
        // 身份是 id，不是名字。名字是可变的：拿它当定位键，改名那一刻"它是谁"就变了；
        // 允许同名之后更糟——同一层两个同名目录，按名字根本认不出改的是哪一个。
        KpCategoryQuery q = new KpCategoryQuery();
        q.setId(req.getId());
        q.setUserId(userId);
        KpCategory category = kpCategoryRepository.get(q, e -> e);
        String newName = req.getNewName().trim();
        if (category.getName().equals(newName)) {
            return;
        }
        // 同级重名检查。真正的保证在 DB 的 uk_user_parent_name 上，这一步只是为了
        // 给人话错误——让唯一键直接抛 DuplicateKeyException 的话用户看到的是 SQL 异常。
        // 排除自己：改到同名（换个说法但同层唯一）不算撞。
        KpCategory dup = kpCategoryRepository
                .findByKey(userId, category.getParentId(), newName)
                .orElse(null);
        if (dup != null && !dup.getId().equals(category.getId())) {
            throw new CoreCraftException(ResultCodeEnum.FAIL, "同一层下已有同名目录");
        }
        category.setName(newName);
        kpCategoryRepository.update(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long categoryId, Long userId) {
        KpCategoryQuery q = new KpCategoryQuery();
        q.setId(categoryId);
        q.setUserId(userId);
        KpCategory category = kpCategoryRepository.get(q, e -> e);
        // 知识点不跟着删：把目录下的知识点放回"未归类"，用户随时能重新归类
        kpCategoryRepository.clearTopicCategory(category.getId());
        // 子目录上提到被删节点的父级，不连坐删除：分组结构是用户手建的，
        // 删一个文件夹不该顺手毁掉它里面的分组。留下的子节点若同名会撞唯一键，
        // 那是用户重名造成的，属于他能看见、能改的显式冲突，不替他默默改名。
        Long deletedId = category.getId();
        Long parentId = category.getParentId();
        for (KpCategory child : kpCategoryRepository.listByUserId(userId)) {
            // 判据是"父级是**被删的那个**"。写成跟 parentId 比就全找错了：
            // 那样挑出来的是被删节点的兄弟，上提等于把它们设成原来的父级，纯空转。
            if (!deletedId.equals(child.getParentId())) {
                continue;
            }
            child.setParentId(parentId);
            kpCategoryRepository.update(child);
        }
        kpCategoryRepository.delete(category.getId());
    }
}
