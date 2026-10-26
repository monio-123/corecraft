package com.mo.corecraft.service.impl;

import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.query.KpTagQuery;
import com.mo.corecraft.model.req.KpTagRenameReq;
import com.mo.corecraft.model.resp.KpTagResp;
import com.mo.corecraft.repository.KpTagRepository;
import com.mo.corecraft.repository.KpTopicTagRepository;
import com.mo.corecraft.service.KpTagService;
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
    }

    /**
     * 统计一组 tagId 的引用数。**一次查完**（原来每个 tag 查一次 = N+1）。
     *
     * <p>⚠️ 注释原来写着"UI 还没做，不是热路径" —— **与现实不符**：
     * 前端 store 明确消费 {@code /kp/tag/list} 返回的 {@code refCount}（标签筛选器要显示每个标签的条数），
     * 标签一多就是几十次查询。
     */
    private Map<Long, Long> countRefsByTagIds(List<Long> tagIds) {
        return kpTopicTagRepository.listByTagIds(tagIds).stream()
                .collect(Collectors.groupingBy(KpTopicTag::getTagId, Collectors.counting()));
    }
}
