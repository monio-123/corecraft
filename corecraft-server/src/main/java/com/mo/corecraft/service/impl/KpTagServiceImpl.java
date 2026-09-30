package com.mo.corecraft.service.impl;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.query.KpTagQuery;
import com.mo.corecraft.model.req.KpTagRenameReq;
import com.mo.corecraft.model.resp.KpTagResp;
import com.mo.corecraft.repository.KpTagRepository;
import com.mo.corecraft.repository.KpTopicTagRepository;
import com.mo.corecraft.service.KpTagService;
import com.mo.corecraft.service.KpTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpTagServiceImpl implements KpTagService {

    private final KpTagRepository kpTagRepository;
    private final KpTopicTagRepository kpTopicTagRepository;
    private final KpTopicService kpTopicService;

    @Override
    public List<KpTagResp> listTags(Long userId) {
        List<KpTag> tags = kpTagRepository.listByUserId(userId);
        if (tags.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = tags.stream().map(KpTag::getId).toList();
        Map<Long, Long> refCountMap = countRefsByTagIds(tagIds);

        return tags.stream().map(t -> {
            KpTagResp r = new KpTagResp();
            r.setId(t.getId());
            r.setName(t.getName());
            r.setRefCount(refCountMap.getOrDefault(t.getId(), 0L));
            return r;
        }).toList();
    }

    @Override
    @Transactional
    public void renameTag(KpTagRenameReq req, Long userId) {
        KpTag old = kpTagRepository.findByKey(userId, req.getOldName())
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "标签不存在"));
        // 新 name 与旧 name 相同 = no-op
        if (old.getName().equals(req.getNewName())) {
            return;
        }
        // 校验新 name 未被该 user 占用
        if (kpTagRepository.findByKey(userId, req.getNewName()).isPresent()) {
            throw new CoreCraftException(ResultCodeEnum.FAIL, "目标标签已存在");
        }
        old.setName(req.getNewName());
        kpTagRepository.update(old);
        // 改名后 topic 树归属可能漂，rebuild 一次
        kpTopicService.rebuildAutoTrees(userId);
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId, Long userId) {
        KpTagQuery q = new KpTagQuery();
        q.setId(tagId);
        q.setUserId(userId);
        KpTag tag = kpTagRepository.get(q, e -> e);
        // 先删关联（持有该 tag 的 topic 不再引用此 tag；topic 本身不删）
        kpTopicTagRepository.deleteByTagId(tag.getId());
        kpTagRepository.delete(tag.getId());
        // tag 删了 → topic 树归属可能空掉，rebuild 一次
        kpTopicService.rebuildAutoTrees(userId);
    }

    /**
     * 统计一组 tagId 的引用数。
     * 简化实现：单 tag 逐次 count（早期项目数据量小；UI 还没做，不是热路径）。
     * 如未来 N+1 明显，加 {@code @Select("SELECT tag_id, COUNT(*) FROM kp_topic_tag WHERE tag_id IN (...) GROUP BY tag_id")}
     */
    private Map<Long, Long> countRefsByTagIds(List<Long> tagIds) {
        return tagIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> (long) kpTopicTagRepository.listByTagId(id).size()
        ));
    }
}
