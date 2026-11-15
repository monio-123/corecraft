package com.mo.corecraft.service.support;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 归类材料（标题 + 正文截断）、出题材料（前端指令 + 后端现查正文）、
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
     * 归类时送进模型的正文**上限**（字符）。
     *
     * <p>归类要回答的只是"这条该归到哪、叫什么"，读几百字足够；
     * 而待归类里混着几万字的长条目，全量塞进去就是几万 token 的输入 ——
     * 又慢又贵，还可能把模型的注意力带偏。
     */
    private static final int CLASSIFY_BODY_LIMIT = 2000;

    private final KpTopicMapper topicMapper;
    private final AiChatService aiChatService;

    /**
     * 归类这一步的材料：标题 + 正文（**后端现查**）。
     *
     * <p>⚠️ **为什么不让前端传**：前端在"待归类"那一屏拿到的是**结构索引**，
     * 而结构索引**刻意不含正文**（列表页读全文实测 4.4 秒，索引 78 毫秒），
     * 前端传过来的只能是个空壳（只有标题）⇒ 模型判不了该归哪。
     * ⇒ 和"目录清单由后端现查"同一个道理：**这次调用要用到的知识，后端自己捞**。
     *
     * <p>⚠️ **归属按登录态收**：这条知识点不是本人的就当它没正文（返回空材料），
     * 不报错 —— 一个步骤失败不该把整个任务带崩（任务里其余条目照常跑）。
     *
     * <p>⚠️ **正文只取前一段**：有一批条目是几万字的（首页随手记里混过一条 3 万字的），
     * 全量塞进去就是几万 token 的输入，模型判个目录绰绰有余 ⇒ 截断。
     * 截断长度和摘要口径对齐（服务端列表投影的 200 字）—— 同一份"看一眼"的标准。
     */
    public String classifyMaterial(KpGenTask task, KpGenStep step) {
        Long topicId = step.getTopicId();
        if (topicId == null) {
            return step.getMaterial() == null ? "" : step.getMaterial();
        }
        KpTopic topic = topicMapper.selectById(topicId);
        if (topic == null || !Objects.equals(topic.getUserId(), task.getUserId())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String title = StringUtils.hasText(topic.getTitle()) ? topic.getTitle().trim() : "";
        if (!title.isEmpty()) {
            sb.append("标题：").append(title);
        }
        String body = topic.getContent() == null ? "" : topic.getContent().strip();
        if (body.length() > CLASSIFY_BODY_LIMIT) {
            body = body.substring(0, CLASSIFY_BODY_LIMIT);
        }
        if (!body.isEmpty()) {
            sb.append(title.isEmpty() ? "内容：\n" : "\n\n内容：\n").append(body);
        }
        return sb.toString();
    }

    /**
     * 出题/考试步骤的材料 = **前端拼的出题指令 + 后端现查的知识正文**。
     *
     * <p>知识 id 来自参数快照的 {@code topicIds}（出题范围就是那几条）；
     * 正文按类型分组（{@code AiChatService#digestFor}，与聊天那条路同一份格式）。
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
