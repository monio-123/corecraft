package com.mo.corecraft.service.support;

import com.mo.corecraft.model.entity.KpCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 目录树内存折叠/子树收集共用工具（统一防环，消除服务间重复 BFS）。 */
public final class CategoryTreeHelper {

    private CategoryTreeHelper() {
    }

    public static Map<Long, List<Long>> childrenByParent(List<KpCategory> categories) {
        return categories.stream()
                .filter(c -> !KpCategory.ROOT_ID.equals(c.getParentId()))
                .collect(Collectors.groupingBy(KpCategory::getParentId,
                        Collectors.mapping(KpCategory::getId, Collectors.toList())));
    }

    /** 收集以 {@code rootId} 为根的整棵子树 id（含自身，Set 去重防父子成环死循环）。 */
    public static List<Long> subtreeIdList(Map<Long, List<Long>> childrenOf, Long rootId) {
        List<Long> out = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
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

    public static Set<Long> subtreeIdSet(Map<Long, List<Long>> childrenOf, Long rootId) {
        return new HashSet<>(subtreeIdList(childrenOf, rootId));
    }
}
