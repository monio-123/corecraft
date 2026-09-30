package com.mo.corecraft.service.impl;

import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicQuiz;
import com.mo.corecraft.model.entity.KpTopicRelation;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.query.KpTopicQuery;
import com.mo.corecraft.model.req.KpTopicCreateReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.repository.KpTagRepository;
import com.mo.corecraft.repository.KpTopicQuizRepository;
import com.mo.corecraft.repository.KpTopicRelationRepository;
import com.mo.corecraft.repository.KpTopicRepository;
import com.mo.corecraft.repository.KpTopicTagRepository;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class KpTopicServiceImpl implements KpTopicService {

    private final KpTopicRepository kpTopicRepository;
    private final KpTopicTagRepository kpTopicTagRepository;
    private final KpTagRepository kpTagRepository;
    private final KpTopicRelationRepository kpTopicRelationRepository;
    private final KpTopicQuizRepository kpTopicQuizRepository;

    @Override
    public List<KpTopicResp> selectTopicList(KpTopicQuery query) {
        List<KpTopicResp> list = kpTopicRepository.list(query, BeanUtils.converter(KpTopicResp.class));
        if (!list.isEmpty()) {
            enrichTopicResps(list);
        }
        return list;
    }

    @Override
    public KpTopicResp selectTopicById(Long id) {
        KpTopicQuery query = new KpTopicQuery();
        query.setId(id);
        KpTopicResp resp = kpTopicRepository.get(query, BeanUtils.converter(KpTopicResp.class));
        enrichTopicResps(List.of(resp));
        return resp;
    }

    @Override
    @Transactional
    public void createTopic(KpTopicCreateReq req, Long userId) {
        KpTopic entity = BeanUtils.createFrom(req, KpTopic.class);
        entity.setUserId(userId);
        entity.setTitle(StringUtils.defaultIfBlank(req.getTitle(), ""));
        kpTopicRepository.insert(entity);
        saveTopicTags(entity.getId(), req.getTags(), userId);
        saveTopicQuizzes(entity.getId(), req.getQuizQuestions());
        // 标签驱动：回填每个 topic 的 treeId（一个 tag = 一棵树）
        rebuildAutoTrees(userId);
    }

    @Override
    @Transactional
    public void updateTopic(KpTopicUpdateReq req, Long userId) {
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
            if (r.getTreeId() != null) {
                e.setTreeId(r.getTreeId());
            }
        });
        if (req.getTags() != null) {
            kpTopicTagRepository.deleteByTopicId(req.getId());
            saveTopicTags(req.getId(), req.getTags(), userId);
            rebuildAutoTrees(userId);
        }
        if (req.getQuizQuestions() != null) {
            kpTopicQuizRepository.deleteByTopicId(req.getId());
            saveTopicQuizzes(req.getId(), req.getQuizQuestions());
        }
    }

    @Override
    @Transactional
    public void deleteTopic(Long id, Long userId) {
        kpTopicTagRepository.deleteByTopicId(id);
        kpTopicRelationRepository.deleteByTopicId(id);
        kpTopicQuizRepository.deleteByTopicId(id);
        kpTopicRepository.delete(id);
        // 删除后重建剩余 topic 的 treeId 归属
        rebuildAutoTrees(userId);
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

    // ==================== 标签驱动的 treeId 回填 ====================

    /**
     * 一个 tag = 一棵树：相同 tagKey 必同组（不复用 hashCode + 偏移，避免把同 tag 拆开）。
     * 给所有 topic 回填 treeId，前端可按 treeId 过滤。
     * <p>
     * tagKey 用 kp_tag.id（不取 category+name 字符串），保证 rename 不影响 treeId 稳定性。
     */
    @Override
    @Transactional
    public void rebuildAutoTrees(Long userId) {
        KpTopicQuery query = new KpTopicQuery();
        query.setUserId(userId);
        List<KpTopic> topics = kpTopicRepository.list(query, e -> e);
        if (topics == null || topics.isEmpty()) {
            return;
        }
        // 一次性查所有 topic 的 tag 关联
        List<Long> topicIds = topics.stream().map(KpTopic::getId).toList();
        List<KpTopicTag> allLinks = kpTopicTagRepository.listByTopicIds(topicIds);
        Map<Long, List<Long>> topicIdToTagIds = new HashMap<>();
        for (KpTopicTag link : allLinks) {
            topicIdToTagIds.computeIfAbsent(link.getTopicId(), k -> new ArrayList<>()).add(link.getTagId());
        }

        Map<Long, Long> tagIdToTreeId = new HashMap<>();
        for (KpTopic t : topics) {
            List<Long> tagIds = topicIdToTagIds.getOrDefault(t.getId(), List.of());
            Long treeId = null;
            if (!tagIds.isEmpty()) {
                // 取第一个 tag 的 id 当 key（保证同 topic 同一棵树）
                Long firstTagId = tagIds.get(0);
                treeId = tagIdToTreeId.computeIfAbsent(firstTagId, k -> (long) k.hashCode());
            }
            kpTopicRepository.update(t.getId(), treeId, (newTreeId, e) -> e.setTreeId(newTreeId));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 批量填充 resp 的 tags/quizQuestions/relatedTopicIds（避免 N+1）。
     * 入参 list 可变，结果就地修改。
     */
    private void enrichTopicResps(List<KpTopicResp> resps) {
        if (resps == null || resps.isEmpty()) {
            return;
        }
        List<Long> topicIds = resps.stream().map(KpTopicResp::getId).toList();

        // tags：批量查关联 + tag 详情
        Map<Long, List<KpTag>> tagsByTopic = new HashMap<>();
        List<KpTopicTag> links = kpTopicTagRepository.listByTopicIds(topicIds);
        if (!links.isEmpty()) {
            List<Long> tagIds = links.stream().map(KpTopicTag::getTagId).distinct().toList();
            Map<Long, KpTag> tagById = new HashMap<>();
            for (KpTag tag : kpTagRepository.listByIds(tagIds)) {
                tagById.put(tag.getId(), tag);
            }
            for (KpTopicTag link : links) {
                KpTag tag = tagById.get(link.getTagId());
                if (tag == null) continue;
                tagsByTopic.computeIfAbsent(link.getTopicId(), k -> new ArrayList<>()).add(tag);
            }
        }

        // quizzes
        Map<Long, List<KpTopicQuiz>> quizzesByTopic = new HashMap<>();
        for (KpTopicQuiz q : kpTopicQuizRepository.listByTopicIds(topicIds)) {
            quizzesByTopic.computeIfAbsent(q.getTopicId(), k -> new ArrayList<>()).add(q);
        }

        // relations
        Map<Long, List<KpTopicRelation>> relationsByTopic = new HashMap<>();
        for (KpTopicRelation r : kpTopicRelationRepository.listByTopicIds(topicIds)) {
            relationsByTopic.computeIfAbsent(r.getTopicId(), k -> new ArrayList<>()).add(r);
        }

        for (KpTopicResp resp : resps) {
            List<KpTag> tags = tagsByTopic.getOrDefault(resp.getId(), List.of());
            if (!tags.isEmpty()) {
                List<String> encodedTags = tags.stream()
                        .map(KpTag::getName)
                        .toList();
                resp.setTags(JsonUtils.toJsonString(encodedTags));
            }
            List<KpTopicQuiz> quizzes = quizzesByTopic.getOrDefault(resp.getId(), List.of());
            if (!quizzes.isEmpty()) {
                List<String> questions = quizzes.stream().map(KpTopicQuiz::getQuestion).toList();
                resp.setQuizQuestions(JsonUtils.toJsonString(questions));
            }
            List<KpTopicRelation> relations = relationsByTopic.getOrDefault(resp.getId(), List.of());
            if (!relations.isEmpty()) {
                List<Long> relatedIds = relations.stream().map(KpTopicRelation::getRelatedTopicId).toList();
                resp.setRelatedTopicIds(relatedIds);
            }
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

    private void saveTopicQuizzes(Long topicId, List<String> quizQuestions) {
        if (quizQuestions == null || quizQuestions.isEmpty()) {
            return;
        }
        for (int i = 0; i < quizQuestions.size(); i++) {
            KpTopicQuiz quiz = new KpTopicQuiz();
            quiz.setTopicId(topicId);
            quiz.setQuestion(quizQuestions.get(i));
            quiz.setSort(i);
            kpTopicQuizRepository.insert(quiz);
        }
    }
}
