package com.mo.corecraft.service.impl;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpCategory;
import com.mo.corecraft.model.query.KpCategoryQuery;
import com.mo.corecraft.model.req.KpCategoryCreateReq;
import com.mo.corecraft.model.req.KpCategoryImportNode;
import com.mo.corecraft.model.req.KpCategoryImportReq;
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

    /** 一次最多导多少个目录。防的是"贴了一整本书进来"那种量。 */
    private static final int MAX_IMPORT_NODES = 300;

    private final KpCategoryRepository kpCategoryRepository;

    @Override
    public List<KpCategoryResp> listCategories(Long userId) {
        return kpCategoryRepository.listByUserId(userId).stream()
                .map(c -> toResp(c.getId(), c.getName(), c.getParentId()))
                .toList();
    }

    /**
     * 目录 → 响应。**映射只存这一份**（列表和导入原来各手写一遍同样的三个 setter）。
     *
     * <p>⚠️ {@code parentId} 传的是**实体的 getParentId()**（已把历史 NULL 归一成 0），
     * 不是裸字段 ⇒ 前端只面对一种"顶层"表示。
     */
    private KpCategoryResp toResp(Long id, String name, Long parentId) {
        KpCategoryResp r = new KpCategoryResp();
        r.setId(id);
        r.setName(name);
        r.setParentId(parentId);
        return r;
    }

    @Override
    @Transactional
    public void createCategory(KpCategoryCreateReq req, Long userId) {
        createOne(req.getName(), req.getParentId(), userId, false);
    }

    @Override
    @Transactional
    public List<KpCategoryResp> importTree(KpCategoryImportReq req, Long userId) {
        Long mountId = req.getParentId() == null ? KpCategory.ROOT_ID : req.getParentId();
        int total = countNodes(req.getNodes());
        if (total > MAX_IMPORT_NODES) {
            throw new CoreCraftException(ResultCodeEnum.FAIL,
                    "一次最多创建 " + MAX_IMPORT_NODES + " 个目录，这批有 " + total + " 个");
        }
        // 挂载点必须在本人的目录树里（否则能把目录挂到别人的树上去）。
        requireMount(mountId, userId);
        // 建好的目录（含合并复用的已有目录）—— 调用方靠 id 把知识点挂上去。
        List<KpCategoryResp> built = new ArrayList<>();
        for (KpCategoryImportNode node : req.getNodes()) {
            importNode(node, mountId, userId, built);
        }
        return built;
    }

    /**
     * 挂载点必须在本人的目录树里。**顶层（ROOT）没有归属问题**，直接过。
     *
     * <p>不属于本人的 id 一律当"不存在"—— 不报错说"那是别人的目录"，
     * 那种报错等于告诉别人"这个 id 存在"。
     *
     * <p>⚠️ 归属校验**只有 {@link #require} 这一处**：挂载点、父目录、改名、移动、删除
     * 问的都是同一句话——"这个目录存在且是本人的"。
     * 原来这里自己又实现了一遍（全量捞出来扫一遍 + 错误码还是 FAIL），
     * 改归属规则时要记得改两个地方。
     */
    private void requireMount(Long categoryId, Long userId) {
        if (categoryId == null || KpCategory.ROOT_ID.equals(categoryId)) {
            return;
        }
        require(categoryId, userId, "挂载目录不存在");
    }

    /**
     * 建一个目录，返回它的 id。**单个新建和批量导入共用这一处** ——
     * 同层重名、父目录归属、名字 trim 这些规则只有一份，改的时候不会只改一半。
     *
     * @param mergeIfExists 同层已经有同名目录时：true = 沿用已有的那个（导入用），
     *                      false = 报错（手工建用）
     */
    private Long createOne(String rawName, Long rawParentId, Long userId, boolean mergeIfExists) {
        String name = rawName.trim();
        // parentId 必须**先归一再查重**：查重用的是唯一键那三列，
        // 拿没归一的 parentId 去查会漏——库里历史数据有 parent_id 为 NULL 的。
        Long parentId = rawParentId == null ? KpCategory.ROOT_ID : rawParentId;
        // 唯一键是 (user_id, parent_id, name)，所以只有**同一层**才不许重名。
        // 不同父目录下的同名目录（操作系统/内存 和 计算机网络/内存）是允许的。
        var existing = kpCategoryRepository.findByKey(userId, parentId, name);
        if (existing.isPresent()) {
            if (!mergeIfExists) {
                throw new CoreCraftException(ResultCodeEnum.FAIL, "同一层下已有同名目录");
            }
            // 导入：**沿用已有的那个**，把下级挂进去。
            // 导入是"补全"——用户本来就有一棵树，贴进来的是同一套体系的另一版，撞名是常事。
            return existing.get().getId();
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
        return entity.getId();
    }

    /** 导入树里一共有多少个节点（用来卡上限） */
    private int countNodes(List<KpCategoryImportNode> nodes) {
        if (nodes == null) {
            return 0;
        }
        int n = 0;
        for (KpCategoryImportNode node : nodes) {
            n += 1 + countNodes(node.getChildren());
        }
        return n;
    }

    /**
     * 递归建：先建自己拿到 id，再建下级（挂在这个 id 底下）。
     *
     * <p>每建一个就记进 {@code built}：调用方要拿 id 把知识点挂到具体目录下。
     * **合并复用的已有目录也记**（它的 id 就是知识点要挂的地方）。
     */
    private void importNode(KpCategoryImportNode node, Long parentId, Long userId,
                            List<KpCategoryResp> built) {
        Long id = createOne(node.getName(), parentId, userId, true);
        KpCategoryResp row = toResp(id, node.getName().trim(), parentId);
        built.add(row);
        if (node.getChildren() == null) {
            return;
        }
        for (KpCategoryImportNode child : node.getChildren()) {
            importNode(child, id, userId, built);
        }
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
