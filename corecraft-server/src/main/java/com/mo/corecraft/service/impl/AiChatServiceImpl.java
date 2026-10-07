package com.mo.corecraft.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.model.resp.SysDictItemResp;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /** AI 统一描述所在的字典编码；项的 value 就是场景标识（chat / 将来还有别的） */
    private static final String SYSTEM_PROMPT_DICT = "ai_system_prompt";

    /** 一次最多带多少轮历史。**正文不占这个名额**——它固定钉在第一条，甩不掉 */
    private static final int HISTORY_TURNS = 5;

    /** 目录树输出时的深度上限。层级数据坏了（父子成环）时靠它收住 */
    private static final int MAX_TREE_DEPTH = 10;

    private final AiEndpointService aiEndpointService;
    private final KpTopicService kpTopicService;
    private final KpCategoryService kpCategoryService;
    private final KpTagService kpTagService;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictItemService sysDictItemService;
    private final HttpExecutor httpExecutor;

    @Override
    public List<AiQuestionResp> questions() {
        Long dictTypeId = dictTypeId(QUESTION_DICT);
        if (dictTypeId == null) {
            return List.of();
        }
        var itemQuery = new SysDictItemQuery();
        itemQuery.setDictTypeId(dictTypeId);
        itemQuery.setEnabled(true);
        return sysDictItemService.selectSysDictItemList(itemQuery).stream().map(item -> {
            var resp = new AiQuestionResp();
            resp.setKey(item.getValue());
            resp.setLabel(item.getLabel());
            return resp;
        }).toList();
    }

    @Override
    public void stream(Long userId, AiChatReq req, AiDeltaSink deltaSink) throws IOException {
        AiEndpoint endpoint = aiEndpointService.requireReady(userId, req.getEndpointId());

        KpTopicResp topic = kpTopicService.selectTopicById(req.getTopicId());
        if (topic == null) {
            throw new CoreCraftException("知识点不存在或已删除");
        }

        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + endpoint.getApiKey(),
                "Content-Type", "application/json"
        );

        List<Map<String, String>> messages = buildMessages(req, topic, userId);

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

    private List<Map<String, String>> buildMessages(AiChatReq req, KpTopicResp topic, Long userId) {
        String questionKey = req.getQuestionKey();
        List<AiChatReq.Turn> history = req.getHistory();
        StringBuilder ctx = new StringBuilder()
                .append("标题：").append(StringUtils.defaultString(topic.getTitle()))
                .append("\n\n内容：\n").append(StringUtils.defaultString(topic.getContent()));

        String instruction = appendInstruction(ctx, questionKey, userId);
        // ⚠️ 有 questionKey 时**只传 remark，不传 label**。
        // label（"给这个知识点命名"）是给用户看的按钮文字，不是给模型的 prompt——
        // 两者一起发，模型会把 label 当成要回应的话头，原样复述一遍当答案
        // （2026-10-07 实测："给这个知识点命名"这个提问，AI 答"给这个知识点命名"）。
        // remark 本身就是完整的任务描述，label 多余。
        String current = instruction.isEmpty() ? req.getQuestion().trim() : instruction;

        // 协议无状态，所以"模型记得上一句"只能靠前端把历史回传（AiChatReq.history）。
        // 后端不落库也拿不到，问答记录本来就不存库。
        //
        // **正文不参与这个窗口**——它是这轮对话的前提（"用户会就这条知识提问"），
        // 固定钉在第一条。⚠️ 别图省事把正文也当成历史的第一轮跟着一起截：
        // 那样一超过窗口，正文就从请求里彻底消失，模型会拿训练时的通识瞎编，
        // 而界面上看不出任何异常——比"没有历史"隐蔽得多。
        //
        // 只取尾部：前面的老轮次对当前问题基本没帮助，但每轮都要重发一遍（上游按
        // input token 计费）。先剔掉缺问或缺答的轮次——Map.of 不接受 null 值，
        // 混进来会变成一个看不懂的 NPE。
        List<AiChatReq.Turn> all = history == null ? List.<AiChatReq.Turn>of() : history;
        List<AiChatReq.Turn> valid = all.stream()
                .filter(t -> StringUtils.isNotBlank(t.getQuestion()) && StringUtils.isNotBlank(t.getAnswer()))
                .toList();
        List<AiChatReq.Turn> past = valid.size() > HISTORY_TURNS
                ? valid.subList(valid.size() - HISTORY_TURNS, valid.size())
                : valid;

        var messages = new ArrayList<Map<String, String>>();
        messages.add(Map.of("role", "system", "content", systemPrompt(req.getScene())));
        // 最早那一轮的问题跟正文并进同一条 user：这样第一轮的形态和加历史之前一模一样，
        // 也没有两条挨着的 user 消息（有些模型对那个处理得不好）。
        int n = past.size();
        messages.add(Map.of("role", "user", "content",
                ctx + "\n\n" + (n == 0 ? current : past.get(0).getQuestion())));
        for (int i = 1; i < n; i++) {
            messages.add(Map.of("role", "user", "content", past.get(i).getQuestion()));
            messages.add(Map.of("role", "assistant", "content", past.get(i).getAnswer()));
        }
        messages.add(Map.of("role", "user", "content", current));
        return messages;
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
        var item = findItem(QUESTION_DICT, questionKey);
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
                    ctx.append("\n\n现有目录清单（缩进表示层级，越深离顶层越远）：\n")
                            .append(categoryTree(list));
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

    /**
     * 目录清单输出成**带缩进的树**。
     *
     * 原来是一行一个平铺名字（`- 数据库` `- 持久化` `- Mybatis` …），模型完全看不出
     * 它们有父子关系——以为「数据库」和「Mybatis」是并列的。后果实测有两个：
     * 把一条知识分到了 `Mybatis`（实际在「数据库/持久化」下**第三层**），
     * 以及把新提议的 `MySQL` 建到了顶层，跟「持久化」「Mybatis」这些明明挂在
     * 「数据库」下面的不在一层。**缩进就是层级。**
     *
     * 父节点不在清单里时**照样打印，但当顶层并留一条日志**：都是自己的数据，
     * 真出现这种行说明有脏数据，**让它可见比让它凭空消失好**，但也不假装没看见。
     */
    private String categoryTree(List<KpCategoryResp> list) {
        var byId = list.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(KpCategoryResp::getId, Function.identity(), (a, b) -> a));
        var childrenOf = new HashMap<Long, List<KpCategoryResp>>();
        var roots = new ArrayList<KpCategoryResp>();
        for (KpCategoryResp c : list) {
            if (StringUtils.isBlank(c.getName())) {
                continue;
            }
            Long pid = c.getParentId();
            if (pid == null || pid == 0L || !byId.containsKey(pid)) {
                if (pid != null && pid != 0L) {
                    log.warn("[ai] 目录 {} 的父级 {} 不在清单里，按顶层输出", c.getName(), pid);
                }
                roots.add(c);
            } else {
                childrenOf.computeIfAbsent(pid, k -> new ArrayList<>()).add(c);
            }
        }
        // 按 id 排，输出稳定——不然每次调用的清单顺序都可能不一样，模型没法比对
        roots.sort(Comparator.comparing(KpCategoryResp::getId));
        var sb = new StringBuilder();
        appendTree(sb, roots, childrenOf, 0);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, List<KpCategoryResp> nodes,
                            Map<Long, List<KpCategoryResp>> childrenOf, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            // 层级循环（a 的父是 b、b 的父是 a）时靠它收住，不然直接栈溢出
            log.warn("[ai] 目录层级超过 {} 层，截断输出", MAX_TREE_DEPTH);
            return;
        }
        for (KpCategoryResp c : nodes) {
            sb.append("  ".repeat(depth)).append("- ").append(c.getName()).append('\n');
            var kids = childrenOf.get(c.getId());
            if (kids != null) {
                kids.sort(Comparator.comparing(KpCategoryResp::getId));
                appendTree(sb, kids, childrenOf, depth + 1);
            }
        }
    }

    /**
     * 这个场景的统一描述，从字典 {@code ai_system_prompt} 里按 value 取 remark。
     *
     * **按场景取，而不是取固定一条**：场景是配置。加一个新场景 = 字典加一行
     * + 前端多传一个 scene，**后端这里一个字都不用改**。
     * ⚠️ 前提是那个场景用的还是已有那几种上下文形状（就一条知识）。要引入新形状
     * （零条、多条知识）那还是得发版——字典能免掉的是"改措辞"，不是"写新行为"。
     *
     * **读不到就抛，不退回某一段固定的描述**：场景写错时如果悄悄换一段上去，
     * 症状是"AI 答非所问"——**看起来完全正常**，比报错难查一个数量级。
     */
    private String systemPrompt(String scene) {
        var item = findItem(SYSTEM_PROMPT_DICT, scene);
        if (item == null || StringUtils.isBlank(item.getRemark())) {
            throw new CoreCraftException("AI 场景 " + scene + " 没有配置统一描述（字典 " + SYSTEM_PROMPT_DICT + "）");
        }
        return item.getRemark();
    }

    /** 字典类型 id，按 code 精确找。没建 / 停用 / 删了都返回 null */
    private Long dictTypeId(String code) {
        var type = sysDictTypeService.selectSysDictType(
                new SysDictTypeQuery() {{ setCode(code); setEnabled(true); }});
        return type == null ? null : type.getId();
    }

    /**
     * 按 value 精确找字典项，**找不到返回 null**。
     *
     * 调用方手里就是 value 原样，所以精确匹配就够。`(dict_type_id, value)` 上有唯一键，
     * 命中最多一条，所以 list 的第一条就是它。
     *
     * **走 list 而不是 get**：get 查不到会 orElseThrow 抛「数据不存在」，
     * 而这里要的正是 null——调用方分两种情况处理（systemPrompt 当配置错误抛出去、
     * appendInstruction 当"没有额外描述"退回原问题），拿到一个无信息的通用异常
     * 就没法区分了。
     */
    private SysDictItemResp findItem(String dictCode, String value) {
        Long dictTypeId = dictTypeId(dictCode);
        if (dictTypeId == null) {
            return null;
        }
        var q = new SysDictItemQuery();
        q.setDictTypeId(dictTypeId);
        q.setValue(value);
        q.setEnabled(true);
        return sysDictItemService.selectSysDictItemList(q).stream().findFirst().orElse(null);
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