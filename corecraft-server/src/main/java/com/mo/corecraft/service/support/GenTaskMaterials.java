package com.mo.corecraft.service.support;

import tools.jackson.databind.JsonNode;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpGenStep;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 生成任务的**材料拼装**（从 `KpGenTaskServiceImpl` 拆出，2026-11-15）：
 * 命名材料（后端现查正文）、出题材料（前端指令 + 后端现查正文）、
 * 参数快照里的 topicIds。
 *
 * <p>共通的规矩：**正文一律现查、不进快照**——任务跑的时候正文可能已经改了，
 * 快照里存一份旧的会与界面看到的对不上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenTaskMaterials {

    /**
     * 命名时送进模型的正文**上限**（字符）。
     *
     * <p>命名要回答的只是"这段内容在讲什么"，读几百字足够；
     * 而随手记里混过一条 3 万字的，全量塞进去就是几万 token 的输入 ——
     * 又慢又贵，还可能把模型的注意力带偏。
     */
    private static final int NAME_BODY_LIMIT = 2000;

    private final KpTopicMapper topicMapper;
    private final AiChatService aiChatService;

    /**
     * 命名这一步的材料：**只有正文**（**后端现查**）。
     *
     * <p>⚠️ **标题不给**：这条内容此刻还没有标题（那正是要模型给的东西），
     * 给了旧的（空串）只会让模型困惑。
     *
     * <p>⚠️ **归属按登录态收**：这条知识点不是本人的就当它没正文（返回空材料），
     * 不报错 —— 一个步骤失败不该把整个任务带崩。
     */
    public String nameMaterial(KpGenTask task, KpGenStep step) {
        Long topicId = step.getTopicId();
        if (topicId == null) {
            return step.getMaterial() == null ? "" : step.getMaterial();
        }
        KpTopic topic = topicMapper.selectById(topicId);
        if (topic == null || !Objects.equals(topic.getUserId(), task.getUserId())) {
            return "";
        }
        String body = topic.getContent() == null ? "" : topic.getContent().strip();
        if (body.length() > NAME_BODY_LIMIT) {
            body = body.substring(0, NAME_BODY_LIMIT);
        }
        return body;
    }

    /**
     * 出题/考试步骤的材料 = **前端拼的出题指令 + 后端现查的知识正文**。
     *
     * <p>知识 id 来自参数快照的 {@code topicIds}（出题范围就是那几条）；
     * 正文逐条编号（{@code AiChatService#digestFor}，与聊天那条路同一份格式）。
     */
    public String quizMaterial(KpGenTask task, KpGenStep step) {
        String instruction = step.getMaterial() == null ? "" : step.getMaterial();
        List<Long> topicIds = topicIdsOf(task);
        if (topicIds.isEmpty()) {
            return instruction;
        }
        String digest = aiChatService.digestFor(topicIds, task.getUserId());
        if (!StringUtils.hasText(digest)) {
            return instruction;
        }
        return instruction + "\n\n" + digest;
    }

    /** 参数快照里的 topicIds（出题范围） */
    public List<Long> topicIdsOf(KpGenTask task) {
        if (!StringUtils.hasText(task.getParams())) {
            return List.of();
        }
        try {
            JsonNode p = JsonUtils.readTree(task.getParams());
            JsonNode ids = p.get("topicIds");
            if (ids == null || !ids.isArray()) {
                return List.of();
            }
            List<Long> out = new ArrayList<>();
            for (JsonNode n : ids) {
                if (n.isNumber()) {
                    out.add(n.asLong());
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("任务 {} 的 topicIds 解析失败：{}", task.getId(), e.getMessage());
            return List.of();
        }
    }
}
