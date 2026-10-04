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
        // 唯一键仍是 (user_id, name)，所以查重保持按名字。等唯一键换成
        // (user_id, parent_id, name) 之后，这里要同步改成按 (userId, parentId, name) 查。
        if (kpCategoryRepository.findByKey(userId, name).isPresent()) {
            throw new CoreCraftException(ResultCodeEnum.FAIL, "同名目录已存在");
        }
        Long parentId = req.getParentId() == null ? KpCategory.ROOT_ID : req.getParentId();
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
        KpCategory old = kpCategoryRepository.findByKey(userId, req.getOldName())
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "目录不存在"));
        String newName = req.getNewName().trim();
        if (old.getName().equals(newName)) {
            return;
        }
        if (kpCategoryRepository.findByKey(userId, newName).isPresent()) {
            throw new CoreCraftException(ResultCodeEnum.FAIL, "同名目录已存在");
        }
        old.setName(newName);
        kpCategoryRepository.update(old);
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
