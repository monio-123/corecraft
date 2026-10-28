package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicRelation;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.query.KpCategoryQuery;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.model.resp.SysDictItemResp;
import com.mo.corecraft.model.resp.Taggable;
import com.mo.corecraft.repository.KpCategoryRepository;
import com.mo.corecraft.repository.KpTagRepository;
import com.mo.corecraft.repository.KpTopicRelationRepository;
import com.mo.corecraft.repository.KpTopicRepository;
import com.mo.corecraft.repository.KpTopicTagRepository;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.service.SysDictItemService;
import com.mo.corecraft.service.SysDictTypeService;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpTopicServiceImpl implements KpTopicService {

    /**
     * 知识点类型所在的字典编码。
     *
     * <p>前端 store 加载的是**同一个字典**（那边的 {@code TOPIC_TYPE_DICT}）——
     * 字典是"知识点类型"这个概念唯一的权威定义，两端要指名同一份字典，
     * 字典编码就是它的名字（和表名一样，是共享契约不是各写一遍的规则）。
     */
    private static final String TOPIC_TYPE_DICT = "kp_topic_type";

    private final KpTopicRepository kpTopicRepository;
    private final KpTopicTagRepository kpTopicTagRepository;
    private final KpTagRepository kpTagRepository;
    private final KpTopicRelationRepository kpTopicRelationRepository;
    private final KpCategoryRepository kpCategoryRepository;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;

    @Override
    public IPage<KpTopicResp> selectTopicPage(Page<KpTopic> page, KpTopicQuery query) {
        IPage<KpTopicResp> result = kpTopicRepository.page(page, query, BeanUtils.converter(KpTopicResp.class));
        enrichTopicResps(result.getRecords());
        return result;
    }

    @Override
    public List<KpTopicIndexResp> selectTopicIndex(Long userId, String keyword) {
        List<KpTopicIndexResp> list = kpTopicRepository.listIndex(userId, keyword);
        applyTags(list);
        return list;
    }

    @Override
    public KpTopicResp selectTopicById(Long id, Long userId) {
        // **归属校验**：原来只按 id 查，等于"知道 id 就能读别人的知识"。
        // 列表路径早就 setUserId 了（controller 的 list），详情这条路漏了。
        KpTopicResp resp = kpTopicRepository.get(ownedTopicQuery(id, userId), BeanUtils.converter(KpTopicResp.class));
        // get 查不到会 orElseThrow，所以这里 resp 必然非 null，List.of(null) 那个 NPE 到不了
        enrichTopicResps(List.of(resp));
        return resp;
    }

    @Override
    public List<KpTopicResp> selectTopicsByIds(List<Long> ids, Long userId) {
        // 归属在 SQL 的 where 里（见 KpTopicRepository.listByIdsAndUserId）：
        // 不是你的**根本查不出来**，不是查出来再丢掉。
        // 空入参的兜底在仓储层（那里才真的要发查询）——这层不重复判一遍。
        List<KpTopicResp> list = kpTopicRepository.listByIdsAndUserId(ids, userId).stream()
                .map(BeanUtils.converter(KpTopicResp.class))
                .toList();
        if (list.isEmpty()) {
            return list;
        }
        enrichTopicResps(list);
        // **按传入顺序排回去**：`IN` 查询的返回顺序是不确定的，
        // 而调用方给的顺序是有意义的（页面上卡片看到的顺序）⇒ 同样的集合每次汇总成同样的一段。
        Map<Long, KpTopicResp> byId = list.stream()
                .collect(Collectors.toMap(KpTopicResp::getId, t -> t));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Map<Long, String> typeNamesByTopic(Collection<KpTopicResp> topics) {
        Map<Long, String> labels = dictTypeLabels();
        // 默认档 = 字典里排第一的那条（和前端 defaultTopicTypeId() 同一个口径）。
        // 字典本身查不到时没有默认档，类型名就是空串。
        String fallback = labels.isEmpty() ? "" : labels.values().iterator().next();
        Map<Long, String> names = new HashMap<>();
        for (KpTopicResp topic : topics) {
            String label = topic.getTypeId() == null ? null : labels.get(topic.getTypeId());
            // typeId 为空 / 指向已删或已停用的字典项 ⇒ 当默认档（和前端 resolveTopicTypeId 一样）
            names.put(topic.getId(), label == null ? fallback : label);
        }
        return names;
    }

    /**
     * 知识点类型字典：字典项 id → label。**按字典顺序**（排第一的那条是默认档）。
     *
     * <p>只取启用中的字典项 —— 停用的类型在界面上也不出现（前端那份清单同样过滤 enabled）。
     */
    private Map<Long, String> dictTypeLabels() {
        SysDictTypeQuery typeQuery = new SysDictTypeQuery();
        typeQuery.setCode(TOPIC_TYPE_DICT);
        var dictType = sysDictTypeService.selectSysDictTypeList(typeQuery).stream().findFirst().orElse(null);
        if (dictType == null) {
            return Map.of();
        }
        SysDictItemQuery itemQuery = new SysDictItemQuery();
        itemQuery.setDictTypeId(dictType.getId());
        itemQuery.setEnabled(true);
        Map<Long, String> labels = new LinkedHashMap<>();
        for (SysDictItemResp item : sysDictItemService.selectSysDictItemList(itemQuery)) {
            labels.put(item.getId(), item.getLabel());
        }
        return labels;
    }

    @Override
    @Transactional
    public void createTopic(KpTopicCreateReq req, Long userId) {
        KpTopic entity = BeanUtils.createFrom(req, KpTopic.class);
        entity.setUserId(userId);
        requireOwnCategory(entity.getCategoryId(), userId);
        entity.setTitle(StringUtils.defaultIfBlank(req.getTitle(), ""));
        kpTopicRepository.insert(entity);
        saveTopicTags(entity.getId(), req.getTags(), userId);
    }

    @Override
    @Transactional
    public void updateTopic(KpTopicUpdateReq req, Long userId) {
        // categoryId 是无条件覆盖写（下面第 81 行），传错就会造出"指向不存在目录"的脏数据。
        // 归类这一点没有外键兜着，所以归属校验放在这条入口上——不放在那儿，
        // 一个手滑的 id 就进库了，界面上只表现为"这条知识点在哪个目录都找不到"。
        requireOwnCategory(req.getCategoryId(), userId);
        kpTopicRepository.update(req.getId(), req, (r, e) -> {
            if (r.getTitle() != null) {
                e.setTitle(r.getTitle());
            }
            if (r.getContent() != null) {
                e.setContent(r.getContent());
            }
            if (r.getParentTopicId() != null) {
                e.setParentTopicId(r.getParentTopicId());
            }
            // 无条件写 categoryId：null = 未归类，是有意义的值，不能当"不修改"跳过。
            // KpTopic.categoryId 上标了 updateStrategy = ALWAYS，null 也会进 UPDATE 语句。
            e.setCategoryId(r.getCategoryId());
            // 类型反过来：没传就当"不修改"跳过。typeId 没有"清空=某个含义"的语义，
            // 所以**不能**学 categoryId 那样无条件写。
            if (r.getTypeId() != null) {
                e.setTypeId(r.getTypeId());
            }
        });
        if (req.getTags() != null) {
            kpTopicTagRepository.deleteByTopicId(req.getId());
            saveTopicTags(req.getId(), req.getTags(), userId);
        }
    }

    @Override
    @Transactional
    public void deleteTopic(Long id, Long userId) {
        // 按 (id, userId) 取实体：既用上了 userId，也挡住越权删除别人的知识点
        KpTopic topic = kpTopicRepository.get(ownedTopicQuery(id, userId), e -> e);
        kpTopicTagRepository.deleteByTopicId(topic.getId());
        kpTopicRelationRepository.deleteByTopicId(topic.getId());
        kpTopicRepository.delete(topic.getId());
    }

    @Override
    public void addTopicRelation(Long topicId, Long relatedTopicId) {
        KpTopicRelation entity = new KpTopicRelation();
        entity.setTopicId(topicId);
        entity.setRelatedTopicId(relatedTopicId);
        kpTopicRelationRepository.insert(entity);
    }

    @Override
    public void removeTopicRelation(Long topicId, Long relatedTopicId) {
        kpTopicRelationRepository.deleteByTopicIdAndRelatedTopicId(topicId, relatedTopicId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 「按 id 取自己的那一条」的条件。**详情和删除两处共用** ——
     * 同一句归属校验写两遍的话，将来加一条条件（比如按空间过滤）要记得改两处，
     * 而漏一处就是"详情能看、删除却删不掉"这种半通不通的状态。
     */
    private KpTopicQuery ownedTopicQuery(Long id, Long userId) {
        KpTopicQuery query = new KpTopicQuery();
        query.setId(id);
        query.setUserId(userId);
        return query;
    }

    /**
     * 这批知识点的标签：topicId → **标签名的 JSON 字符串**。
     *
     * <p>**批量查，不是一条一条查**（避免 N+1）。**索引和详情共用这一份** ——
     * 两边要的标签是同一件事，各写一遍的话改了一处另一处就对不上。
     *
     * <p>**没有标签的 id 不出现在结果里**（而不是给个空数组）——
     * 「没有标签」和「标签是空数组」对前端不是一回事，写回规则因此只有一条。
     */
    private Map<Long, String> tagJsonByTopic(List<Long> topicIds) {
        Map<Long, String> out = new HashMap<>();
        // 空入参：仓储层自己就挡了（listByTopicIds 返回空列表），这里不重复判
        List<KpTopicTag> links = kpTopicTagRepository.listByTopicIds(topicIds);
        if (links.isEmpty()) {
            return out;
        }
        List<Long> tagIds = links.stream().map(KpTopicTag::getTagId).distinct().toList();
        Map<Long, KpTag> tagById = new HashMap<>();
        for (KpTag tag : kpTagRepository.listByIds(tagIds)) {
            tagById.put(tag.getId(), tag);
        }
        Map<Long, List<String>> names = new HashMap<>();
        for (KpTopicTag link : links) {
            KpTag tag = tagById.get(link.getTagId());
            if (tag == null) continue;
            names.computeIfAbsent(link.getTopicId(), k -> new ArrayList<>()).add(tag.getName());
        }
        for (Map.Entry<Long, List<String>> entry : names.entrySet()) {
            out.put(entry.getKey(), JsonUtils.toJsonString(entry.getValue()));
        }
        return out;
    }

    /**
     * 把这批知识点的标签写回去。**索引和详情共用这一句**。
     *
     * <p>行 id 从 {@link Taggable} 拿（索引行和详情行都实现了它）⇒
     * 调用方不用各写一遍"取 id → 查标签 → 写回标签"这三步。
     *
     * <p>没有标签的**不写**（字段留 null）——「没有标签」和「标签是空数组」不是一回事。
     *
     * @return 这批行的 id —— 调用方还要拿它查关联，不用再数一遍
     */
    private <T extends Taggable> List<Long> applyTags(List<T> rows) {
        List<Long> ids = rows.stream().map(Taggable::getId).toList();
        Map<Long, String> tagJson = tagJsonByTopic(ids);
        for (T row : rows) {
            String tags = tagJson.get(row.getId());
            if (tags != null) {
                row.setTags(tags);
            }
        }
        return ids;
    }

    /**
     * 批量填充 resp 的 tags/relatedTopicIds（避免 N+1）。
     * 入参 list 可变，结果就地修改。
     */
    private void enrichTopicResps(List<KpTopicResp> resps) {
        if (resps == null || resps.isEmpty()) {
            return;
        }

        // tags：批量查关联 + tag 详情（顺带把这批 id 拿回来，relations 用同一份）
        List<Long> topicIds = applyTags(resps);

        // relations
        Map<Long, List<KpTopicRelation>> relationsByTopic = new HashMap<>();
        for (KpTopicRelation r : kpTopicRelationRepository.listByTopicIds(topicIds)) {
            relationsByTopic.computeIfAbsent(r.getTopicId(), k -> new ArrayList<>()).add(r);
        }

        for (KpTopicResp resp : resps) {
            List<KpTopicRelation> relations = relationsByTopic.getOrDefault(resp.getId(), List.of());
            if (!relations.isEmpty()) {
                List<Long> relatedIds = relations.stream().map(KpTopicRelation::getRelatedTopicId).toList();
                resp.setRelatedTopicIds(relatedIds);
            }
        }
    }

    /**
     * 目录必须是这个人自己的、且真的存在。**null 放行**——null = 未归类，是有意义的值。
     *
     * 这是外部输入边界上的基本校验（HTTP 入口），不是给内部调用兜底：
     * updateTopic 对 categoryId 是无条件覆盖写、库里又没有外键，不在这里挡一下，
     * 传错一个 id 就是一条永远归不了类的脏数据，而且界面上完全看不出发生了什么。
     */
    private void requireOwnCategory(Long categoryId, Long userId) {
        if (categoryId == null) {
            return;
        }
        var query = new KpCategoryQuery();
        query.setId(categoryId);
        query.setUserId(userId);
        if (kpCategoryRepository.find(query, e -> e).isEmpty()) {
            throw new CoreCraftException("目录不存在或不属于当前用户");
        }
    }

    /**
     * 入参 tag 字符串 → upsert kp_tag 主表 → 批量插入 kp_topic_tag 关联。
     * 入参做 dedup（LinkedHashSet 保序）：同一字符串重复时只插一次，避免撞 (topic_id, tag_id) 唯一键。
     * 空字符串 / 纯空白跳过；同 (user, name) 复用同一 kp_tag。
     */
    private void saveTopicTags(Long topicId, List<String> tags, Long userId) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        // 排重：保序去重（前端可能传重复，这是双保险）
        Set<String> deduped = new LinkedHashSet<>();
        for (String raw : tags) {
            if (raw != null && !raw.isBlank()) {
                deduped.add(raw.trim());
            }
        }
        for (String name : deduped) {
            if (name.isEmpty()) continue;
            KpTag tag = kpTagRepository.findByKey(userId, name)
                    .orElseGet(() -> {
                        KpTag t = new KpTag();
                        t.setUserId(userId);
                        t.setName(name);
                        kpTagRepository.insert(t);
                        return t;
                    });
            KpTopicTag link = new KpTopicTag();
            link.setTopicId(topicId);
            link.setTagId(tag.getId());
            kpTopicTagRepository.insert(link);
        }
    }
}
