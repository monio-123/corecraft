package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpTopicLearningMapper;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicLearning;
import com.mo.corecraft.model.entity.KpTopicRelation;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.query.KpCategoryQuery;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicMasteryReq;
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
import com.mo.corecraft.service.support.KpDictCodes;
import com.mo.corecraft.service.support.MasteryLevelResolver;
import com.mo.corecraft.service.support.MasteryScope;
import com.mo.corecraft.service.support.MasteryScopeResolver;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.ContentHash;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class KpTopicServiceImpl implements KpTopicService {

    /**
     * 知识点类型所在的字典编码：{@link com.mo.corecraft.service.support.KpDictCodes#TOPIC_TYPE}
     * （后端唯一一份；前端 store 加载的是**同一个字典**——字典是"知识点类型"这个概念
     * 唯一的权威定义，字典编码就是它的名字，是共享契约不是各写一遍的规则）。
     */

    private final KpTopicRepository kpTopicRepository;
    private final KpTopicTagRepository kpTopicTagRepository;
    private final KpTagRepository kpTagRepository;
    private final KpTopicRelationRepository kpTopicRelationRepository;
    private final KpCategoryRepository kpCategoryRepository;
    private final KpTopicLearningMapper kpTopicLearningMapper;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;
    private final MasteryScopeResolver masteryScopeResolver;

    @Override
    public IPage<KpTopicResp> selectTopicPage(Page<KpTopic> page, KpTopicQuery query) {
        // ⚠️ **列表页走轻量投影**（2026-11-09 用户报「/kp/topic/list 有点慢」）：
        // 原来这条走 MP 的实体分页（selectPage），**content 整列被取回来**
        // （实测一页 20 条 = 29KB，正文占绝大多数），而卡片上只显示**前 90 字的摘要**
        // ⇒ 列表那次传输是白传输；点进详情页另有详情接口取全文。
        // 现在只取 SUBSTRING(content, 1, 200) 当摘要，**响应结构一个字没变**（content 字段还在）。
        long current = page.getCurrent();
        long size = page.getSize();
        long offset = (current - 1) * size;
        List<KpTopicResp> records = kpTopicRepository.listPageProjection(query, offset, size);
        long total = kpTopicRepository.countPageProjection(query);
        // 手工拼 Page：分页是自己 limit 出来的（MP 的 PaginationInnerInterceptor 只管它自己生成的 SQL），
        // 但**返回类型仍是 IPage** ⇒ controller / 前端的分页契约一个字没动。
        Page<KpTopicResp> result = new Page<>(current, size, total);
        result.setRecords(records);
        enrichTopicResps(result.getRecords(), query.getUserId());
        return result;
    }

    @Override
    public List<KpTopicIndexResp> selectTopicIndex(Long userId, String keyword, String scope) {
        List<KpTopicIndexResp> list = kpTopicRepository.listIndex(userId, keyword, scope);
        applyTags(list);
        // 索引也要把「是否参与掌握度」解析好（目录树/概览要按参与项过滤，
        // 同一个判据必须在这一层也能读到，前端不自己判一遍）
        Map<Long, Boolean> typeDefaults = masteryScopeResolver.typeDefaults();
        for (KpTopicIndexResp row : list) {
            row.setMasteryEnabled(MasteryScope.participates(
                    row.getMasteryOverride(), typeDefaults, row.getTypeId()));
        }
        return list;
    }

    @Override
    public KpTopicResp selectTopicById(Long id, Long userId) {
        // **归属校验**：原来只按 id 查，等于"知道 id 就能读别人的知识"。
        // 列表路径早就 setUserId 了（controller 的 list），详情这条路漏了。
        KpTopicResp resp = kpTopicRepository.get(ownedTopicQuery(id, userId), BeanUtils.converter(KpTopicResp.class));
        // get 查不到会 orElseThrow，所以这里 resp 必然非 null，List.of(null) 那个 NPE 到不了
        enrichTopicResps(List.of(resp), userId);
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
        enrichTopicResps(list, userId);
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
        typeQuery.setCode(KpDictCodes.TOPIC_TYPE);
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
        // 内容版本锚点（实施计划 §1.3）：hash 每次内容变化都算；epoch 走 DB 默认 1（新建不可能有旧证据）
        entity.setContentHash(ContentHash.ofTopic(entity.getTitle(), entity.getContent()));
        entity.setMasteryOverride(req.getMasteryEnabled());
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
            // 掌握度单条覆盖：**只在显式给了才写**。
            // ⚠️ 这里和 KpTopic.masteryOverride 的 ALWAYS 策略是一对：实体那一侧保证
            // "null 能写进库"（null = 继承类型是有意义的值），但**这个入口**的 null
            // 是"这次请求没带这个字段"（保存正文时前端不会带）⇒ 无条件写会把用户
            // 显式设过的覆盖悄悄抹回继承。
            // ⇒ 显式改覆盖走专门的一步（PUT 时带 masteryEnabled），
            //    这里只在有值时写，"恢复继承"由那一步显式传 null 完成。
            if (r.getMasteryEnabled() != null) {
                e.setMasteryOverride(r.getMasteryEnabled());
            }
            // 内容版本锚点（实施计划 §1.3）：一般编辑只更新 hash、**不动 epoch**——
            // epoch 只有「大幅重写/重新生成」才递增（AI 写正文那条路），设计 §9.2。
            e.setContentHash(ContentHash.ofTopic(e.getTitle(), e.getContent()));
        });
        if (req.getTags() != null) {
            kpTopicTagRepository.deleteByTopicId(req.getId());
            saveTopicTags(req.getId(), req.getTags(), userId);
        }
    }

    @Override
    @Transactional
    public void setTopicMasteryEnabled(KpTopicMasteryReq req, Long userId) {
        // 归属校验：不是自己的改不了（和 updateTopic 同一条规矩）
        KpTopic topic = kpTopicRepository.get(ownedTopicQuery(req.getId(), userId), e -> e);
        if (topic == null) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "知识点不存在");
        }
        // ⚠️ **无条件写**（包括 null）：这个端点的 null 是"恢复继承类型默认"，
        // 是一个**明确的三态**。KpTopic.masteryOverride 标了 ALWAYS ⇒ null 会进 UPDATE。
        topic.setMasteryOverride(req.getEnabled());
        kpTopicRepository.update(topic);
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
    @Transactional
    public int deleteTopicsByCategoryIds(List<Long> categoryIds, Long userId) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0;
        }
        // **只取本人挂在这批目录下的**：目录本身已按 userId 校验过，但知识点还得再收一遍
        // ——万一库里有一条 category_id 指向别人的目录（脏数据），那条不该被连带删掉。
        List<KpTopic> victims = kpTopicRepository.listByCategoryIdsAndUserId(categoryIds, userId);
        if (victims.isEmpty()) {
            return 0;
        }
        List<Long> ids = victims.stream().map(KpTopic::getId).toList();
        // 标签引用和关联关系**物理删**（关联表是纯关系，关系断了不留墓碑，
        // 否则重新插 (topic_id, tag_id) 会撞唯一键 uk_topic_tag）——
        // 和单条删除（deleteTopic）走的是同一条路。
        ids.forEach(kpTopicTagRepository::deleteByTopicId);
        ids.forEach(kpTopicRelationRepository::deleteByTopicId);
        // 知识点本体是**软删**（@TableLogic）：数据还在库里，将来想做回收站/自动清理有底。
        ids.forEach(kpTopicRepository::delete);
        return ids.size();
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
    private void enrichTopicResps(List<KpTopicResp> resps, Long userId) {
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

        // 掌握度：参与判据 + 档位（2026-11-12）
        enrichMastery(resps, userId);
    }

    /**
     * 批量补「是否参与掌握度」与「展示档位」。
     *
     * <p>⚠️ **判据只有一处**（{@link MasteryScope}）：单条显式值 &gt; 类型默认值 &gt; 安全兜底。
     * 前端拿到的是**已解析的最终值**，不自己再判一遍。
     *
     * <p>⚠️ **档位为 null = 没有有效评估**：前端据此**完全不渲染**掌握度标记
     * （"从未考过"表现为空白，不是画一个「未评估」）。
     *
     * <p>不参与掌握度的条目：{@code masteryLevel} 一律留 null（哪怕它有学习投影）。
     */
    private void enrichMastery(List<KpTopicResp> resps, Long userId) {
        if (resps == null || resps.isEmpty()) {
            return;
        }
        Map<Long, Boolean> typeDefaults = masteryScopeResolver.typeDefaults();
        LocalDateTime now = LocalDateTime.now();

        // 学习投影一次性捞这批 id（避免 N+1）。
        // ⚠️ user_id 必须带上：其它所有 kp_topic_learning 访问都带 eq(userId)，
        // 这里靠"topic 私有 ⇒ 学习行必属本人"的间接不变式护着，口径一漂就查不出错。
        List<Long> ids = resps.stream().map(KpTopicResp::getId).toList();
        Map<Long, KpTopicLearning> learningByTopic = kpTopicLearningMapper
                .selectList(Wrappers.<KpTopicLearning>lambdaQuery()
                        .eq(KpTopicLearning::getUserId, userId)
                        .in(KpTopicLearning::getTopicId, ids))
                .stream().collect(Collectors.toMap(KpTopicLearning::getTopicId, e -> e, (a, b) -> a));

        for (KpTopicResp resp : resps) {
            boolean participates = MasteryScope.participates(
                    resp.getMasteryOverride(), typeDefaults, resp.getTypeId());
            resp.setMasteryEnabled(participates);
            if (!participates) {
                resp.setMasteryLevel(null);
                continue;
            }
            resp.setMasteryLevel(MasteryLevelResolver.levelOf(learningByTopic.get(resp.getId()), now));
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
