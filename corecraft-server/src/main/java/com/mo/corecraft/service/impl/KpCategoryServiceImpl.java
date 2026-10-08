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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            require(parentId, userId, "父目录不存在");
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
        KpCategory category = require(req.getId(), userId);
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
        KpCategory category = require(categoryId, userId);
        List<Long> subtree = subtreeIds(userId, category.getId());
        // 子树里**每一层**的知识点都要回未归类。原来只清直接下级，是因为删除语义是
        // "子目录上提"、那些下级还活着；现在下级跟着一起删了，只清一层就会让挂在更深
        // 一处的知识点留下 category_id 指向一个**已经物理删掉**的目录——那些知识在界面上
        // 查不到、数据却还在。所以一次 IN 把整棵子树清干净。
        kpCategoryRepository.clearTopicCategory(subtree);
        kpCategoryRepository.deleteBatch(subtree);
    }

    @Override
    @Transactional
    public void moveCategory(Long categoryId, Long newParentId, Long userId) {
        KpCategory category = require(categoryId, userId);
        Long target = newParentId == null ? KpCategory.ROOT_ID : newParentId;

        if (target.equals(category.getParentId())) {
            return;
        }
        if (KpCategory.ROOT_ID.equals(target)) {
            // 移到顶层没有环的问题，也没有"父目录得存在"的问题
        } else {
            require(target, userId);
            // 移到自己的下级 = 成环，之后这棵树再也遍历不出根（flattenCategoryTree 那类
            // 遍历会无限下去）。这里挡的是**数据层面**的坏，界面上的禁用只是提前告知。
            if (subtreeIds(userId, category.getId()).contains(target)) {
                throw new CoreCraftException("不能移到自己的下级目录下");
            }
        }
        // 撞的是唯一键 (user_id, parent_id, name)。**不替用户改名**——自动加个 "Git2"
        // 是替他做决定，而且他没要求过。真冲突就报出来，本来就是他能看见、能改的事。
        if (kpCategoryRepository.findByKey(userId, target, category.getName()).isPresent()) {
            throw new CoreCraftException("该目录下已有同名目录");
        }
        category.setParentId(target);
        kpCategoryRepository.update(category);
    }

    /**
     * 按 id 取一个目录，顺带挡住"操作别人的目录"。取不到就抛，不是返回 null 让调用方自己判。
     *
     * **四个入口共用它**（建 / 改名 / 移动 / 删除都要先确认"这个目录存在且是本人的"）。
     * 之前 `createCategory` 和 `renameCategory` 各自手写了一遍 query，前者写成
     * `find(...).isEmpty()` 抛异常、后者用 `get` 拿到 null 往下走——**后者一旦目录
     * 不存在就是 NPE**，不是人话错误。消息可传是因为"父目录不存在"和"目录不存在"
     * 对用户是两件事。
     */
    private KpCategory require(Long categoryId, Long userId) {
        return require(categoryId, userId, "目录不存在");
    }

    private KpCategory require(Long categoryId, Long userId, String notFoundMessage) {
        KpCategoryQuery q = new KpCategoryQuery();
        q.setId(categoryId);
        q.setUserId(userId);
        return kpCategoryRepository.find(q, e -> e)
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, notFoundMessage));
    }

    /**
     * 这个目录往下**整棵**子树的 id，**含自己**。
     *
     * 一次把全表捞出来在内存里走，比递归查库省事：用户的目录是几十级别，
     * 每层发一次 SELECT 才是真浪费。
     *
     * visited 收住**父子成环**的数据（移动功能加上之后，环理论上不再会产生，
     * 但库里可能已经有脏的）——没它就是死循环。
     */
    private List<Long> subtreeIds(Long userId, Long rootId) {
        List<KpCategory> all = kpCategoryRepository.listByUserId(userId);
        Map<Long, List<Long>> childrenOf = all.stream()
                .filter(c -> !KpCategory.ROOT_ID.equals(c.getParentId()))
                .collect(Collectors.groupingBy(KpCategory::getParentId,
                        Collectors.mapping(KpCategory::getId, Collectors.toList())));
        List<Long> out = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            if (!visited.add(id)) {
                continue;
            }
            out.add(id);
            childrenOf.getOrDefault(id, List.of()).forEach(stack::push);
        }
        return out;
    }
}
