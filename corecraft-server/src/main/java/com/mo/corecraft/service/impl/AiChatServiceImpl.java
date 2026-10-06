package com.mo.corecraft.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.resp.AiQuestionResp;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.service.KpCategoryService;
import com.mo.corecraft.service.KpTagService;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.service.SysDictItemService;
import com.mo.corecraft.service.SysDictTypeService;
import com.mo.corecraft.utils.HttpExecutor;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.mo.corecraft.service.AiChatService.AiDelta;
import com.mo.corecraft.service.AiChatService.AiDeltaSink;

/**
 * 调 OpenAI 兼容的 /chat/completions。
 *
 * **不引 provider 字段**：OpenAI 兼容协议下厂商由 base_url 决定，换厂商只改 URL 和模型名。
 * 碰到不兼容的（星火 / 百度）才写适配器，那时再加字段。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final AiDelta EMPTY_DELTA = new AiDelta("", "", "");

    /** 引导问题所在的字典编码 */
    private static final String QUESTION_DICT = "ai_topic_question";

    /**
     * 上下文只喂这一条（2026-10-07 定：标题 + 正文）。
     *
     * 两条约束写进 system prompt：一是**只根据这条知识回答**，二是**内容里没有的直接说没有**。
     * 不写第二条，模型会拿训练时的通识来补，答得看着像模像样、实际在编。
     */
    private static final String SYSTEM_PROMPT =
            "你是一个学习助手。用户会就一条知识提问。\n"
            + "只根据下面这条知识的内容回答，不要引入这条知识之外的信息。\n"
            + "内容里没有提到的，直接说「这条知识里没有提到」，不要推测、不要补充你记忆里的版本。\n"
            + "直接给结果：不要复述任务、不要解释你正在做什么、不要写「好的」这类开场白。";

    private final AiEndpointService aiEndpointService;
    private final KpTopicService kpTopicService;
    private final KpCategoryService kpCategoryService;
    private final KpTagService kpTagService;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;
    private final HttpExecutor httpExecutor;

    @Override
    public List<AiQuestionResp> questions() {
        var type = sysDictTypeService.selectSysDictType(
                new SysDictTypeQuery() {{ setCode(QUESTION_DICT); setEnabled(true); }});
        if (type == null) {
            return List.of();
        }
        var itemQuery = new SysDictItemQuery();
        itemQuery.setDictTypeId(type.getId());
        itemQuery.setEnabled(true);
        return sysDictItemService.selectSysDictItemList(itemQuery).stream().map(item -> {
            var resp = new AiQuestionResp();
            resp.setKey(item.getValue());
            resp.setLabel(item.getLabel());
            return resp;
        }).toList();
    }

    @Override
    public void stream(Long userId, Long endpointId, Long topicId, String question, String questionKey,
                       AiDeltaSink deltaSink) throws IOException {
        AiEndpoint endpoint = aiEndpointService.requireReady(userId, endpointId);

        KpTopicResp topic = kpTopicService.selectTopicById(topicId);
        if (topic == null) {
            throw new CoreCraftException("知识点不存在或已删除");
        }

        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + endpoint.getApiKey(),
                "Content-Type", "application/json"
        );

        List<Map<String, String>> messages = buildMessages(topic, question, questionKey, userId);

        // 开流前先把"提交给模型的东西"原样发一次。**发出去的不能比送进去的少**——
        // 界面上要能核对 AI 到底看见了什么，那就得是这个 messages 本身，不是另拼一份。
        deltaSink.accept(AiDelta.ofPrompt(readable(messages)));

        httpExecutor.doPostStream(url(endpoint.getBaseUrl()), headers,
                JsonUtils.toJsonString(Map.of(
                        "model", endpoint.getModel(),
                        "stream", true,
                        "messages", messages
                )),
                line -> {
                    AiDelta delta = extractDelta(line);
                    if (StringUtils.isNotEmpty(delta.reasoning()) || StringUtils.isNotEmpty(delta.content())) {
                        deltaSink.accept(AiDelta.of(delta.reasoning(), delta.content()));
                    }
                });
    }

    // ==================== 内部 ====================

    /**
     * baseUrl 允许两种填法：只到版本段（https://x/v1）或直接填到端点（https://x/v1/chat/completions）。
     * 后者太常见了——各家文档给的都是完整 URL，硬拼会 404，而 404 在这里看不出是"地址填错"还是别的。
     * 这是外部输入边界上的规整，不是给内部调用兜底。
     */
    private String url(String baseUrl) {
        String base = baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    private List<Map<String, String>> buildMessages(KpTopicResp topic, String question,
                                                    String questionKey, Long userId) {
        StringBuilder ctx = new StringBuilder()
                .append("标题：").append(StringUtils.defaultString(topic.getTitle()))
                .append("\n\n内容：\n").append(StringUtils.defaultString(topic.getContent()));

        String instruction = appendInstruction(ctx, questionKey, userId);
        // ⚠️ 有 questionKey 时**只传 remark，不传 label**。
        // label（"给这个知识点命名"）是给用户看的按钮文字，不是给模型的 prompt——
        // 两者一起发，模型会把 label 当成要回应的话头，原样复述一遍当答案
        // （2026-10-07 实测："给这个知识点命名"这个提问，AI 答"给这个知识点命名"）。
        // remark 本身就是完整的任务描述，label 多余。
        ctx.append("\n\n").append(instruction.isEmpty() ? question.trim() : instruction);

        return List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", ctx.toString())
        );
    }

    /** messages 转成人能读的样子。JSON 转义过的 \n 在界面上是一行，反倒看不清发了什么 */
    private String readable(List<Map<String, String>> messages) {
        return messages.stream()
                .map(m -> "【" + m.get("role") + "】\n" + m.get("content"))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /**
     * 按字典项的 value（`key:contexts`）把"这条问题该怎么答"的规则和要附的料拼出来。
     *
     * **questionKey 为空 = 用户自己敲的问题**，那就没有额外描述也没有额外料，
     * 只给正文——这是默认路径，不是兜底。
     *
     * 附料一共三种（none / categories / tags），**这是一张三行的表，不是流程**：
     * 将来要加第四种，改的也只是这里多一个 if，不存在"每加一个问题就要动一遍流程定义"。
     */
    private String appendInstruction(StringBuilder ctx, String questionKey, Long userId) {
        if (StringUtils.isBlank(questionKey)) {
            return "";
        }
        var item = findQuestionItem(questionKey);
        if (item == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(item.getRemark());
        for (String ctxName : contextsOf(item.getValue())) {
            appendContext(ctx, ctxName, userId);
        }
        return sb.toString();
    }

    /** `pick_category:categories,tags` → [categories, tags]。`none` / 空 → 空列表 */
    private List<String> contextsOf(String value) {
        int i = value.indexOf(':');
        if (i < 0) {
            return List.of();
        }
        return List.of(value.substring(i + 1).split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"none".equals(s))
                .toList();
    }

    private void appendContext(StringBuilder ctx, String ctxName, Long userId) {
        switch (ctxName) {
            case "categories" -> {
                var list = kpCategoryService.listCategories(userId);
                if (list != null && !list.isEmpty()) {
                    ctx.append("\n\n现有目录清单：\n").append(
                            list.stream().map(KpCategoryResp::getName).filter(StringUtils::isNotBlank)
                                    .map(n -> "- " + n).reduce((a, b) -> a + "\n" + b).orElse(""));
                }
            }
            case "tags" -> {
                var list = kpTagService.listTags(userId);
                if (list != null && !list.isEmpty()) {
                    ctx.append("\n\n现有标签清单：\n").append(
                            list.stream().map(t -> t.getName()).filter(StringUtils::isNotBlank)
                                    .map(n -> "- " + n).reduce((a, b) -> a + "\n" + b).orElse(""));
                }
            }
            default -> {
                // 字典里写了一个后端不认识的附料标记。忽略它，只用 remark 里的规则——
                // 但要留下痕迹，否则界面上什么都不会显示、也没人知道为什么。
                log.warn("[ai] 忽略未知的附料标记: {}", ctxName);
            }
        }
    }

    /** 按 value 精确找字典项。前端回传的就是 value 原样，所以精确匹配就够 */
    private com.mo.corecraft.model.resp.SysDictItemResp findQuestionItem(String value) {
        var type = sysDictTypeService.selectSysDictType(
                new SysDictTypeQuery() {{ setCode(QUESTION_DICT); setEnabled(true); }});
        if (type == null) {
            return null;
        }
        var q = new SysDictItemQuery();
        q.setDictTypeId(type.getId());
        q.setValue(value);
        q.setEnabled(true);
        return sysDictItemService.selectSysDictItem(q);
    }

    /**
     * SSE 的一行 → 一段增量。
     *
     * **同时读 `reasoning_content` 和 `content` 两个字段**。思考过程在 OpenAI 兼容协议里
     * 是独立的 `delta.reasoning_content`，只读 content 就等于把它丢掉了（2026-10-07 实测）。
     * 不过**能不能拿到取决于模型**：deepseek-flash 这类非思考模型压根不给这个字段，
     * 要换 deepseek-reasoner / o 系列 / qwen-thinking 才有。拿不到就是空串，不补、不伪造。
     *
     * 空行、`[DONE]`、只带 role 的首帧都返回空增量。
     *
     * **解析失败让它抛出去**：流已经开始了，悄悄吞掉只会让用户对着一个说到一半突然停住的
     * 回答发呆。抛出去由 controller 发一个 error 事件，前端能明确显示"出错了"。
     */
    private AiDelta extractDelta(String line) {
        if (line == null || !line.startsWith("data:")) {
            return EMPTY_DELTA;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return EMPTY_DELTA;
        }
        JsonNode delta = JsonUtils.readTree(payload).path("choices").path(0).path("delta");
        return new AiDelta("", text(delta, "reasoning_content"), text(delta, "content"));
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : "";
    }
}