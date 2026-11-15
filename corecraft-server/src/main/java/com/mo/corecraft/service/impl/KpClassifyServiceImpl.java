package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpGenStepMapper;
import com.mo.corecraft.mapper.KpGenTaskMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.entity.KpTag;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicTag;
import com.mo.corecraft.model.req.KpClassifyAdoptReq;
import com.mo.corecraft.model.req.KpTopicUpdateReq;
import com.mo.corecraft.repository.KpCategoryRepository;
import com.mo.corecraft.repository.KpTagRepository;
import com.mo.corecraft.repository.KpTopicRepository;
import com.mo.corecraft.repository.KpTopicTagRepository;
import com.mo.corecraft.service.KpClassifyService;
import com.mo.corecraft.service.KpTopicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 归类的**采纳**：把人确认过的建议写进 kp_topic。
 *
 * <p>## 为什么采纳要单独一个 service
 *
 * <p>跑（生成建议）在 {@code KpGenTaskService} 里 —— 和写正文共用同一套任务中心；
 * 而采纳要写 kp_topic（标题 / 目录 / 标签），那是 {@code KpTopicService} 的地盘。
 * 两件事的时机也不同：跑是自动的，采纳是**人点了才做**。
 *
 * <p>## 采纳时**再校验一次**目录
 *
 * <p>任务跑完到人点采纳之间，目录可能变了（被删 / 改名 / 新建了同名的）。
 * ⇒ 前端在**采纳那一刻**拿着**当下的目录清单**重新解析一遍（见前端的 {@code aiAdopt.js}），
 * 把结果变成 id 传上来；这里再校验一次「这个 id 确实是本人的目录」。
 *
 * <p>两端各校验一次不是重复：前端那份是"按当下清单解析"，
 * 后端这份是"这个 id 归不归我"（那是服务端的知识，前端校验不了）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpClassifyServiceImpl implements KpClassifyService {

    private final KpGenStepMapper stepMapper;
    private final KpGenTaskMapper taskMapper;
    private final KpTopicRepository topicRepository;
    private final KpCategoryRepository categoryRepository;
    private final KpTopicService topicService;
    private final KpTopicTagRepository topicTagRepository;
    private final KpTagRepository tagRepository;

    @Override
    @Transactional
    public int adopt(KpClassifyAdoptReq req, Long userId) {
        // 步骤 → 采纳内容。**按 stepId 建表**（不靠顺序）：
        // 顺序错了就采纳到别的条目上去了，而界面上什么异常都看不出来。
        Map<Long, KpClassifyAdoptReq.Item> items = req.getItems().stream()
                .filter(i -> i.getStepId() != null)
                .collect(Collectors.toMap(KpClassifyAdoptReq.Item::getStepId,
                        Function.identity(), (a, b) -> a));

        // ⚠️ **归属在这一层收**：只取**本人**的、且属于本次采纳的那批步骤。
        // 不按 req.getStepIds() 逐个查 —— 一次 IN 把它们捞出来，
        // 顺便把"不是你的"直接挡在外面（不区分"不存在"和"不是你的"）。
        List<KpGenStep> steps = stepMapper.selectList(Wrappers.<KpGenStep>lambdaQuery()
                .select(KpGenStep::getId, KpGenStep::getTaskId, KpGenStep::getTopicId,
                        KpGenStep::getStatus)
                .in(KpGenStep::getId, items.keySet())
                .in(KpGenStep::getStatus, List.of("ok")));

        // 步骤 → 任务：用来确认这个步骤属于**本人的**某个任务。
        // ⚠️ **kp_gen_step 自己没有 user_id** —— 归属在 kp_gen_task 上。
        // 不校验的话，别人只要知道 stepId 就能改你的知识点归属。
        Map<Long, Long> taskIdOfStep = steps.stream()
                .collect(Collectors.toMap(KpGenStep::getId, KpGenStep::getTaskId, (a, b) -> a));
        if (!taskIdOfStep.isEmpty()) {
            long mine = taskMapper.selectCount(Wrappers.<KpGenTask>lambdaQuery()
                    .select(KpGenTask::getId)
                    .in(KpGenTask::getId, taskIdOfStep.values().stream().distinct().toList())
                    .eq(KpGenTask::getUserId, userId));
            if (mine < taskIdOfStep.values().stream().distinct().count()) {
                throw new CoreCraftException("任务不存在或已删除");
            }
        }

        int written = 0;
        for (KpGenStep step : steps) {
            KpClassifyAdoptReq.Item item = items.get(step.getId());
            if (item == null || step.getTopicId() == null) {
                continue;
            }
            // 知识点也得是**本人的**（步骤是本人的不代表它指向的知识点也是）。
            //
            // ⚠️ **必须用 `find` 而不是 `get`**：`get` 在查不到时是**抛异常**
            // （AbstractRepository#get → orElseThrow "数据不存在"），
            // 而这里要的语义是"**这一条不是我的 ⇒ 跳过这一条**"——
            // 用 get 的话，一条脏数据（步骤指向已删的知识点）会让**整批采纳回滚**，
            // 用户看到的是"采纳失败"，而其他十几条一条也没写进去。
            KpTopic topic = topicRepository.find(
                    topicQuery(step.getTopicId(), userId), e -> e).orElse(null);
            if (topic == null) {
                log.warn("采纳归类：步骤 {} 指向的知识点 {} 不是本人的，跳过", step.getId(), step.getTopicId());
                continue;
            }
            // 目录归属校验：id 必须是本人的目录（前端已经按当下清单解析过了，
            // 这里防的是"解析出来的 id 指向别人的目录"这种脏数据）
            if (item.getCategoryId() != null && !ownCategory(item.getCategoryId(), userId)) {
                throw new CoreCraftException("目录不存在或不属于当前用户");
            }

            KpTopicUpdateReq upd = new KpTopicUpdateReq();
            upd.setId(topic.getId());
            // ⚠️ **标题：空 = 不改**。AI 可能没给名字（那一条只解析出了目录），
            // 这时不能拿空串把用户原来起的标题抹掉。
            if (item.getTitle() != null && !item.getTitle().isBlank()) {
                upd.setTitle(item.getTitle().trim());
            }
            // **目录：无条件写**（null = 留在待归类，这是有意义的值）。
            // 前端传什么就是什么 —— 用户在采纳界面上可以改这一项。
            upd.setCategoryId(item.getCategoryId());
            // 标签：**传 null = 不动**。批量操作没人一条条看过，
            // 把用户已经打好的标签抹掉比多打几个更糟。
            if (item.getTags() != null && !item.getTags().isEmpty()) {
                // ⚠️ **写的是「原有 + 推荐」的合集**，不是推荐那几个。
                // `updateTopic` 对 tags 是 deleteByTopicId 后重插（**替换**），
                // 只写推荐的那几个 ⇒ 用户已经打好的标签全被抹掉。
                // 原有那份**必须在这儿查**（前端那份结构索引是按关键词筛过的，不全）。
                upd.setTags(mergeTags(step.getTopicId(), item.getTags(), userId));
            }
            topicService.updateTopic(upd, userId);
            written += 1;
        }

        // **人已经采纳过了** ⇒ 给这批任务记一笔（`confirmed_at`）：
        // 「确认 / 采纳」是**链尾**的人工节点，它没有后继节点可判"已越过"，
        // 不打这个标记节点会永远停在「待确认」（卡片一直催用户做已经做完的事）。
        //
        // ⚠️ 一条都没写进去也要打：用户点了确认、界面上也确认过了 ——
        // 那是"这一次确认动作"的事实，不是"写成功几条"的统计。
        if (!taskIdOfStep.isEmpty()) {
            taskMapper.update(null, Wrappers.<KpGenTask>lambdaUpdate()
                    .in(KpGenTask::getId, taskIdOfStep.values().stream().distinct().toList())
                    .eq(KpGenTask::getUserId, userId)
                    .set(KpGenTask::getConfirmedAt, java.time.LocalDateTime.now()));
        }
        return written;
    }

    /**
     * 标签合集：**用户原有的 + AI 推荐的**（保序去重）。
     *
     * <p>原有那份从 `kp_topic_tag` 现查（连 `kp_tag` 的名字）——
     * 前端那份是**结构索引**里的标签，而结构索引是**按关键词筛过**的，
     * 搜索态下它是命中集 ⇒ 拿它当"原有标签"会漏掉一部分（漏掉的那部分正好被替换掉）。
     *
     * @param topicId 知识点 id
     * @param recommended AI 推荐的标签名
     * @param userId 归属（防越权读别人的标签）
     * @return 合集（原有在前，推荐在后）
     */
    private List<String> mergeTags(Long topicId, List<String> recommended, Long userId) {
        // 关联行 → tag id → tag 名
        List<KpTopicTag> links = topicTagRepository.listByTopicId(topicId);
        List<String> existing = new ArrayList<>();
        if (!links.isEmpty()) {
            List<Long> tagIds = links.stream().map(KpTopicTag::getTagId).filter(Objects::nonNull).toList();
            if (!tagIds.isEmpty()) {
                // ⚠️ **只取本人的 tag**（tag 是按 user 隔离的）：库里有一条脏数据
                // 指向别人的 tag 时，宁可少写一个标签，也不能把别人的标签名搬过来。
                Map<Long, String> nameOf = new HashMap<>();
                for (KpTag t : tagRepository.listByIds(tagIds)) {
                    if (Objects.equals(t.getUserId(), userId)) {
                        nameOf.put(t.getId(), t.getName());
                    }
                }
                for (KpTopicTag link : links) {
                    String name = nameOf.get(link.getTagId());
                    if (name != null && !name.isBlank()) {
                        existing.add(name.trim());
                    }
                }
            }
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String n : existing) merged.add(n);
        for (String n : recommended) {
            if (n != null && !n.isBlank()) merged.add(n.trim());
        }
        return new ArrayList<>(merged);
    }

    /** 「按 id 取自己的那一条」的条件 */
    private com.mo.corecraft.model.query.KpTopicQuery topicQuery(Long id, Long userId) {
        var q = new com.mo.corecraft.model.query.KpTopicQuery();
        q.setId(id);
        q.setUserId(userId);
        return q;
    }

    /** 这个目录是不是本人的 */
    private boolean ownCategory(Long categoryId, Long userId) {
        return categoryRepository.find(categoryQuery(categoryId, userId), e -> e).isPresent();
    }

    private com.mo.corecraft.model.query.KpCategoryQuery categoryQuery(Long id, Long userId) {
        var q = new com.mo.corecraft.model.query.KpCategoryQuery();
        q.setId(id);
        q.setUserId(userId);
        return q;
    }
}
