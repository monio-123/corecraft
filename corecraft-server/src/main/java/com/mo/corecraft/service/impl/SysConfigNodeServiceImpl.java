package com.mo.corecraft.service.impl;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.SysConfigNode;
import com.mo.corecraft.model.req.SysConfigNodeSaveReq;
import com.mo.corecraft.model.resp.SysConfigNodeResp;
import com.mo.corecraft.repository.SysConfigNodeRepository;
import com.mo.corecraft.repository.SysConfigRepository;
import com.mo.corecraft.service.SysConfigNodeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置树：KV 的分组与说明书。
 *
 * **键 = 从根到本节点的 code 连起来的路径**（config_key 直接存表里，不在读的时候算）：
 * 存下来而不是每次算，是因为它还要当 sys_config 的关联键用 —— 算出来的键没法建索引。
 */
@Service
@RequiredArgsConstructor
public class SysConfigNodeServiceImpl implements SysConfigNodeService {

    /** 值的类型就这两种：数值和文本 */
    private static final String TYPE_NUMBER = "number";
    private static final String TYPE_TEXT = "text";

    private final SysConfigNodeRepository nodeRepository;
    private final SysConfigRepository configRepository;

    @Override
    public List<SysConfigNodeResp> listAll() {
        return nodeRepository.listAll().stream().map(this::toResp).toList();
    }

    @Override
    public List<SysConfigNodeResp> listLeaves(String keyPrefix) {
        // keyPrefix **自带结尾的点**（例 "ai.prompt.question."）—— 调用方给的就是
        // 它自己那个键前缀常量，别在这儿再拼一次（两处拼法迟早会不一致）。
        return nodeRepository.listAll().stream()
                .filter(n -> n.getValueType() != null && n.getConfigKey().startsWith(keyPrefix))
                .map(this::toResp)
                .toList();
    }

    @Override
    public Long create(SysConfigNodeSaveReq req) {
        String code = req.getCode().trim();
        if (code.contains(".")) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "短名里不能有「.」——键是用它拼出来的!");
        }
        long parentId = req.getParentId();
        SysConfigNode parent = parentId == 0L ? null : nodeRepository.getById(parentId);
        if (parentId != 0L && parent == null) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "父节点不存在!");
        }
        // 顶层节点的 code 就是命名空间，它自己就是键
        String configKey = parent == null ? code : parent.getConfigKey() + "." + code;
        if (nodeRepository.getByKey(configKey) != null) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "已经有键是「" + configKey + "」的了!");
        }

        SysConfigNode node = new SysConfigNode();
        node.setParentId(parentId);
        node.setName(req.getName().trim());
        node.setCode(code);
        node.setConfigKey(configKey);
        node.setValueType(normalizeType(req.getValueType()));
        // 排序：接在同一组的最后面。**不用前端传** —— 通用配置项不该为了
        // "排在第几"这种界面上的事多一个输入框。
        node.setSort(nextSort(parentId));
        node.setRemark(req.getRemark());
        nodeRepository.insert(node);
        return node.getId();
    }

    @Override
    public void update(Long id, SysConfigNodeSaveReq req) {
        SysConfigNode old = nodeRepository.getById(id);
        if (old == null) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "节点不存在!");
        }
        // 键 = 路径。短名或父节点一改，键就变了，已经存进去的值会变成孤儿。
        if (!old.getCode().equals(req.getCode().trim())) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "短名不可改——改了键就变了；要换请删了重建!");
        }
        if (old.getParentId() != req.getParentId()) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "父节点不可改——改了键就变了；要换请删了重建!");
        }
        // 值类型也不可改：它决定这一项有没有值。改成组的话，它原来那个值就没人认领了。
        if (!StringUtils.equals(old.getValueType(), normalizeType(req.getValueType()))) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "值类型不可改——它决定这一项有没有值；要改请删了重建!");
        }

        // 补丁实体：只带 id + 要改的列（remark 标了 ALWAYS，所以"清空说明"能写进去）
        SysConfigNode patch = new SysConfigNode();
        patch.setId(id);
        patch.setName(req.getName().trim());
        patch.setRemark(req.getRemark());
        nodeRepository.update(patch);
    }

    @Override
    public void delete(Long id) {
        // 连子树一起删：父节点没了，子节点就是孤儿。
        // 节点不存在时子树是空的 ⇒ 什么都不做（删两次也不报错）。
        for (SysConfigNode n : subtreeOf(id)) {
            nodeRepository.deleteById(n.getId());
            // 它的值也一起删：节点没了，键就没人认领，留着只是看不见的垃圾
            configRepository.deleteByKeyOrPrefix(n.getConfigKey());
        }
    }

    // ==================== 内部 ====================

    /** 同一组里下一个排序号：接在最后一个后面 */
    private int nextSort(long parentId) {
        return nodeRepository.listAll().stream()
                .filter(n -> n.getParentId() == parentId)
                .mapToInt(n -> n.getSort() == null ? 0 : n.getSort())
                .max()
                .orElse(0) + 1;
    }

    /** 自己 + 所有后代（树里父子关系就是 parent_id，从自己往下找） */
    private List<SysConfigNode> subtreeOf(Long id) {
        List<SysConfigNode> all = nodeRepository.listAll();
        Map<Long, List<SysConfigNode>> childrenOf = new HashMap<>();
        for (SysConfigNode n : all) {
            childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        List<SysConfigNode> result = new ArrayList<>();
        Deque<SysConfigNode> stack = new ArrayDeque<>();
        for (SysConfigNode n : all) {
            if (id.equals(n.getId())) {
                stack.push(n);
            }
        }
        while (!stack.isEmpty()) {
            SysConfigNode n = stack.pop();
            result.add(n);
            List<SysConfigNode> kids = childrenOf.get(n.getId());
            if (kids != null) {
                stack.addAll(kids);
            }
        }
        return result;
    }

    /** 值类型：空 = 容器；其余只认 number / text */
    private String normalizeType(String valueType) {
        if (StringUtils.isBlank(valueType)) {
            return null;
        }
        String t = valueType.trim();
        if (!TYPE_NUMBER.equals(t) && !TYPE_TEXT.equals(t)) {
            throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "值类型只能是 number 或 text!");
        }
        return t;
    }

    private SysConfigNodeResp toResp(SysConfigNode n) {
        SysConfigNodeResp resp = new SysConfigNodeResp();
        resp.setId(n.getId());
        resp.setParentId(n.getParentId());
        resp.setName(n.getName());
        resp.setCode(n.getCode());
        resp.setConfigKey(n.getConfigKey());
        resp.setValueType(n.getValueType());
        resp.setRemark(n.getRemark());
        return resp;
    }
}
