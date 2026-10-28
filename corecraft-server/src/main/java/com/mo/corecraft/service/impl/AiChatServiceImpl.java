package com.mo.corecraft.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiChatReq;
import com.mo.corecraft.model.resp.AiQuestionResp;
import com.mo.corecraft.model.resp.KpCategoryResp;
import com.mo.corecraft.model.resp.KpTopicResp;
import com.mo.corecraft.service.AiChatService;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.service.KpCategoryService;
import com.mo.corecraft.service.KpTagService;
import com.mo.corecraft.service.KpTopicService;
import com.mo.corecraft.service.SysConfigNodeService;
import com.mo.corecraft.service.SysConfigService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final AiDelta EMPTY_DELTA = AiDelta.of("", "", "");

    /**
     * 引导问题的键前缀：值在 sys_config（user_id=0 = 系统级），名字在配置树上。
     *
     * **为什么从字典搬到 sys_config**：提示词的本质是**一段系统级配置文本**，
     * 和界面偏好是同一类东西——管理员在「系统管理 → 系统配置」里改，改完不用发版。
     * 字典表没有 user_id，也放不下"每人一份"的分层。
     */
    private static final String QUESTION_KEY_PREFIX = "ai.prompt.question.";

    /**
     * 全局人设的键（sys_config，user_id=0 = 系统级）。
     *
     * **只有一个键，不再按场景分**：这段描述回答的是"这个系统是什么、回答长什么样"，
     * 所有任务（提问 / 命名 / 归类 / 出题）共用它；各任务自己的规则在引导问题里。
     *
     * ⚠️ 2026-10-21 之前这里是 `ai.prompt.system.<scene>`，全站却只传过 'chat' 一个值
     * ——一个只有一格的假轴，还因为那段文字是**问答口吻**（"用户会就一条知识提问"），
     * 被原样发给了命名、归类、出题这些根本不是问答的任务。砍掉了（FEATURES #239）。
     */
    private static final String SYSTEM_PROMPT_KEY = "ai.prompt.system";

    /**
     * 引导问题 remark 里的分节标记：**判据**（判断标准）和**格式**（输出成什么样）分开存。
     *
     * <p>【引用】取的是被引那条的**判据段**，格式段不取——单条问答要"原样输出目录名"，
     * 批量归类要"带前缀的三段键值行"，这两者本来就该各写各的，硬凑成一份只会互相打架。
     */
    private static final String MARK_CRITERIA = "【判据】";
    private static final String MARK_FORMAT = "【格式】";
    private static final String MARK_REFER = "【引用】";

    /** 一次最多带多少轮历史。**正文不占这个名额**——它固定钉在第一条，甩不掉 */
    private static final int HISTORY_TURNS = 5;

    /** 目录树输出时的深度上限。层级数据坏了（父子成环）时靠它收住 */
    private static final int MAX_TREE_DEPTH = 10;

    private final AiEndpointService aiEndpointService;
    private final KpTopicService kpTopicService;
    private final KpCategoryService kpCategoryService;
    private final KpTagService kpTagService;
    private final SysConfigService sysConfigService;
    private final SysConfigNodeService sysConfigNodeService;
    private final HttpExecutor httpExecutor;

    @Override
    public List<AiQuestionResp> questions() {
        // 引导问题是配置树上的一组叶子：短名 = 前端回传用的 key，显示名 = 按钮上的字
        return sysConfigNodeService.listLeaves(QUESTION_KEY_PREFIX).stream().map(node -> {
            var resp = new AiQuestionResp();
            resp.setKey(node.getCode());
            resp.setLabel(node.getName());
            return resp;
        }).toList();
    }

    @Override
    public void stream(Long userId, AiChatReq req, AiDeltaSink deltaSink) throws IOException {
        AiEndpoint endpoint = aiEndpointService.requireReady(userId, req.getEndpointId());

        // 上下文有档位：**用户自己敲的 material > 这次指定的几条知识**。
        // **给了 material 就不去捞知识**——判卷时带的是整卷，
        // 没必要为了它把那个目录里所有正文再捞一遍（考目录时那是几十 KB）。
        // 两个都没给 = 不知道拿谁的内容当上下文 ⇒ 明确报错，不猜。
        List<KpTopicResp> topics = StringUtils.isBlank(req.getMaterial())
                ? resolveTopics(req, userId)
                : List.of();

        Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + endpoint.getApiKey(),
                "Content-Type", "application/json"
        );

        List<Map<String, String>> messages = buildMessages(req, topics, userId);

        // 开流前先把"提交给模型的东西"原样发一次。**发出去的不能比送进去的少**——
        // 界面上要能核对 AI 到底看见了什么，那就得是这个 messages 本身，不是另拼一份。
        deltaSink.accept(AiDelta.ofPrompt(readable(messages)));

        httpExecutor.doPostStream(AiEndpoint.chatUrlOf(endpoint.getBaseUrl()), headers,
                JsonUtils.toJsonString(requestBody(endpoint, messages, req.getThinking())),
                line -> {
                    // **空帧不用在这里滤**：controller 逐字段判空
                    // （prompt / reasoning / content / finish 各判一次），
                    // 空帧到了那儿什么也发不出去。
                    // 原来这里还多包了一层 `AiDelta.of(...)` —— 三个参数逐一相同，
                    // 包出来的对象跟 `delta` 一模一样（prompt 恒为空串）。
                    deltaSink.accept(extractDelta(line));
                });
    }

    // ==================== 内部 ====================

    /**
     * 请求体：**先铺自定义参数，再压上三个固定键**。
     *
     * <p>顺序是刻意的 —— model / stream / messages 是这套调用自己的契约，
     * 用户在自定义参数里写它们也不算数（后写的盖掉先写的）。
     * 其余字段（temperature、思考开关……）原样带过去。
     *
     * @param thinking 这次要不要关掉模型的思考（{@code false} = 关；其它 = 不干预）
     */
    private Map<String, Object> requestBody(AiEndpoint endpoint, List<Map<String, String>> messages, Boolean thinking) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(endpoint.params().custom());
        // **关思考**：厂商无关的一个写法（2026-10-26 实测 deepseek / dashscope 都认它）。
        // 放在端点自定义参数**之后** ⇒ 这一次的勾选盖过端点自己配的那份。
        if (Boolean.FALSE.equals(thinking)) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        body.put("model", endpoint.getModel());
        body.put("stream", true);
        body.put("messages", messages);
        return body;
    }

    /**
     * 这次要处理的那几条知识。
     *
     * <p>**归属由服务端按登录态收**（不是你的查不到），查不到就报"知识点不存在或已删除"，
     * 不区分"不存在"和"不是你的"——免得泄露别人的数据存在性。
     */
    private List<KpTopicResp> resolveTopics(AiChatReq req, Long userId) {
        if (isByIds(req)) {
            List<Long> ids = req.getTopicIds();
            List<KpTopicResp> list = kpTopicService.selectTopicsByIds(ids, userId);
            if (list.isEmpty()) {
                throw new CoreCraftException("知识点不存在或已删除");
            }
            return list;
        }
        if (req.getTopicId() == null) {
            // 原来这里是 @NotNull 拦的；放开是为了支持多条，两条路径现在同一个报错
            throw new CoreCraftException("知识点ID不能为空!");
        }
        return List.of(kpTopicService.selectTopicById(req.getTopicId(), userId));
    }

    private List<Map<String, String>> buildMessages(AiChatReq req, List<KpTopicResp> topics, Long userId) {
        String questionKey = req.getQuestionKey();
        List<AiChatReq.Turn> history = req.getHistory();
        // 上下文：**用户自己敲的 material > 这次指定的多条知识 > 单条知识**。
        // 素材给了就用给的；没给就看这次要处理几条知识。
        // **两者不同时出现**——两个素材会把模型绕晕，它分不清题目该考哪一份。
        // 标签也跟着变：知识点自己的正文叫「内容」，用户粘进来的叫「材料」——
        // 另 5 条引导问题的正文里写的是"读一遍正文"，换个说法它们就对不上了。
        String material = req.getMaterial();
        StringBuilder ctx = new StringBuilder();
        if (StringUtils.isNotBlank(material)) {
            ctx.append("材料：\n").append(material.strip());
        } else if (isByIds(req)) {
            // **按"请求有没有传 ids"分，不按"解析出几条"分**（2026-10-23）：
            // 考目录时前端一律传 ids（哪怕那个目录只有一条知识）⇒ 走汇总路径、**带类型组头**。
            // 按条数分的话，"目录里只有一条"那种考试会掉进单条格式，
            // 材料里就没有组头 ⇒ 模型看不见这批材料是什么角色（笔记还是学习），
            // 而出卷规范里那条"材料按类型分组"的前提就塌了。
            // 单条格式是 **AI 问答抽屉** 用的（那条链路传 topicId，5 条引导问题都按它写的）。
            ctx.append(digestOf(topics));
        } else {
            KpTopicResp one = topics.get(0);
            ctx.append("标题：").append(StringUtils.defaultString(one.getTitle()))
                    .append("\n\n内容：\n").append(StringUtils.defaultString(one.getContent()));
        }

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
        // **正文不参与这个窗口**——它是这轮对话的前提（这次处理的是哪份内容），
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
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        int n = past.size();
        if (n == 0) {
            // 第一轮：素材和任务并进同一条 user —— 形态和加历史之前一模一样，
            // 也没有两条挨着的 user 消息（有些模型对那个处理得不好）。
            // ⚠️ **别在外面再补一条 current**：那会让任务描述**发两遍**
            // （出题那段 733 字白烧一倍 token，而界面上看着完全正常，
            // 只有「发给模型的内容」那栏能看出来）。
            messages.add(Map.of("role", "user", "content", ctx + "\n\n" + current));
        } else {
            // 有历史：素材钉在第一条，最早那一轮的问题跟在它后面
            messages.add(Map.of("role", "user", "content", ctx + "\n\n" + past.get(0).getQuestion()));
            for (int i = 1; i < n; i++) {
                messages.add(Map.of("role", "user", "content", past.get(i).getQuestion()));
                messages.add(Map.of("role", "assistant", "content", past.get(i).getAnswer()));
            }
            messages.add(Map.of("role", "user", "content", current));
        }
        return messages;
    }

    /**
     * 这次是不是"按 id 列表"来的（考目录那条路）。
     *
     * <p>**看请求，不看解析出几条**（2026-10-23）：考目录时前端一律传 `topicIds`，
     * 哪怕那个目录只有一条知识；而 AI 问答抽屉传的是单个 `topicId`。
     * 两条路各自的内容格式不一样，混了会出事 —— 单条格式没有类型组头，
     * 而出卷规范里"材料按类型分组"这条前提就塌了。
     */
    private boolean isByIds(AiChatReq req) {
        return req.getTopicIds() != null && !req.getTopicIds().isEmpty();
    }

    /**
     * 多条知识汇总成一段上下文（考一个目录时）。
     *
     * <p>**每条一段，标题带上**：模型要知道"哪句话讲的是哪个知识点"，
     * 不然几段内容糊在一起，它会把 A 知识的结论安到 B 知识头上。
     *
     * <p>**按类型分组，组头写类型名**（2026-10-23）：同一批材料不是一个性质——
     * 笔记是具体知识点，学习的材料是框架/体系。混在一起不给标记，模型会把它们当同一种东西使，
     * 于是出出一堆"这个体系包含什么"的综述题。
     *
     * <p>**代码只认"按类型分组"，不认"笔记=细节层"这种语义**：哪一类材料该怎么用，
     * 写在意图规范里（`ai.prompt.exam.paper`）；类型名取字典 label，字典改名只改数据。
     * ⇒ **加一种类型 = 加一条字典项 + 改提示词，这段代码不动。**
     */
    private String digestOf(List<KpTopicResp> topics) {
        Map<Long, String> typeNames = kpTopicService.typeNamesByTopic(topics);
        // 分组：同一类型的排一组，组内保持传入顺序；组序 = 类型在材料里第一次出现的顺序
        Map<String, List<KpTopicResp>> groups = new LinkedHashMap<>();
        for (KpTopicResp t : topics) {
            groups.computeIfAbsent(typeNames.getOrDefault(t.getId(), ""), k -> new ArrayList<>()).add(t);
        }
        List<String> blocks = new ArrayList<>();
        int no = 0;
        for (Map.Entry<String, List<KpTopicResp>> group : groups.entrySet()) {
            List<String> items = new ArrayList<>();
            for (KpTopicResp t : group.getValue()) {
                // 编号**跨组连续**：模型说的"第 3 条"指的是整份材料里的第 3 条
                items.add("知识点 " + (++no) + "：" + StringUtils.defaultString(t.getTitle())
                        + "\n" + StringUtils.defaultString(t.getContent()));
            }
            // 类型名取不到（字典空了）就不写组头，材料还是那几条
            blocks.add(group.getKey().isEmpty()
                    ? String.join("\n\n", items)
                    : "【" + group.getKey() + "】\n" + String.join("\n\n", items));
        }
        return String.join("\n\n", blocks);
    }

    /** messages 转成人能读的样子。JSON 转义过的 \n 在界面上是一行，反倒看不清发了什么 */
    private String readable(List<Map<String, String>> messages) {
        return messages.stream()
                .map(m -> "【" + m.get("role") + "】\n" + m.get("content"))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /**
     * 按引导问题的短名（`key:contexts`）把"这条问题该怎么答"的规则和要附的料拼出来。
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
        String instruction = sysConfigService.getSystemValue(promptKeyOf(questionKey));
        if (StringUtils.isBlank(instruction)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(composeInstruction(instruction, new LinkedHashSet<>()));
        for (String ctxName : contextsOf(questionKey)) {
            appendContext(ctx, ctxName, userId);
        }
        return sb.toString();
    }

    /**
     * 引导问题短名 → 它那条配置项的键。
     *
     * **两种写法**：
     * · 短名（`comb:none`）→ 拼上 {@code ai.prompt.question.} 前缀；
     * · 完整键（`ai.prompt.exam.grade`）→ 原样用。
     *
     * 完整键这条路是给「配置树上另开一组提示词」用的（比如考试出题/判分规范）：
     * 它不在 `ai.prompt.question.` 下面 ⇒ **不会变成抽屉里的引导问题按钮**，
     * 但"从配置取一段文本拼进指令"这个机制照样能用，**后端不用为它另开接口**。
     *
     * 判据是**有没有点**：短名是 code（`comb`），代码里不带点；
     * 完整键是几段 code 用点连起来。带点的一律当完整键。
     */
    private String promptKeyOf(String questionKey) {
        return questionKey.contains(".") ? questionKey : QUESTION_KEY_PREFIX + questionKey;
    }

    /**
     * 把一条引导问题的 remark 拼成最终指令。
     *
     * <p>remark 用三个行首标记组织：
     * <pre>
     * 【引用】pick_tags:tags   ← 引用另一条的**判据段**（格式段不取）
     * 【判据】……              ← 判断标准，只该存在一份
     * 【格式】……              ← 输出格式要求
     * </pre>
     *
     * **为什么要把判据和格式拆开**：目录判据原来在 `pick_category` 和 `organize` 里各抄了一份，
     * 改的时候得记得同时改两处——而且**已经漏过**：现值里 `organize` 没跟上 `pick_category` 的
     * 三个正例，也没跟上 `drop_tech_stack_enumeration.sql` 当天加的「清单只是已用叫法、
     * 不是推荐清单」和「凑出来的标签是垃圾」。结果是批量归类拿到的标签判据比单条问答弱一档，
     * 症状还完全看不出来。拆开之后判据只有一个归属，引用方自动跟随。
     *
     * <p>**没标记就整段原样用**（向后兼容）：`comb` / `organize` 这类本来就没有可复用的判据，
     * 不该为了格式统一给它们硬套标记；存量 remark 也因此不用一次性改完。
     */
    private String composeInstruction(String remark, Set<String> visited) {
        if (StringUtils.isBlank(remark)) {
            return "";
        }
        if (!hasSectionMark(remark)) {
            return remark;
        }
        String[] s = splitSections(remark, visited);
        return joinSections(s[0], s[1]);
    }

    /** @return {@code {判据, 格式}}。没标记时判据是整段、格式为空串 */
    private String[] splitSections(String remark, Set<String> visited) {
        StringBuilder criteria = new StringBuilder();
        StringBuilder format = new StringBuilder();
        // 没有标记的普通行归到"当前段"：标记只切段，不自己成行
        StringBuilder sink = criteria;
        for (String line : remark.split("\\R")) {
            String t = line.strip();
            if (t.startsWith(MARK_REFER)) {
                appendReferenced(criteria, t.substring(MARK_REFER.length()), visited);
                continue;
            }
            if (t.startsWith(MARK_CRITERIA)) {
                sink = criteria;
                appendSection(criteria, t.substring(MARK_CRITERIA.length()));
                continue;
            }
            if (t.startsWith(MARK_FORMAT)) {
                sink = format;
                appendSection(format, t.substring(MARK_FORMAT.length()));
                continue;
            }
            appendSection(sink, line);
        }
        return new String[]{criteria.toString(), format.toString()};
    }

    /**
     * 【引用】只取被引那条的**判据段**。
     *
     * <p>**引用断了就抛，不静默跳过**：组织好后漏建一条配置项，症状会是"AI 少了条约束但看起来
     * 正常"——正是这个方法要根除的那类静默降级。
     */
    private void appendReferenced(StringBuilder criteria, String ref, Set<String> visited) {
        String key = ref.strip();
        if (key.isEmpty()) {
            throw new CoreCraftException("引导问题的【引用】后面没写配置项名");
        }
        if (!visited.add(key)) {
            throw new CoreCraftException("引导问题的【引用】成环：" + String.join(" -> ", visited) + " -> " + key);
        }
        var target = sysConfigService.getSystemValue(QUESTION_KEY_PREFIX + key);
        if (StringUtils.isBlank(target)) {
            throw new CoreCraftException("引导问题引用了不存在的配置项：" + key);
        }
        appendSection(criteria, splitSections(target, visited)[0]);
    }

    private boolean hasSectionMark(String remark) {
        return remark.contains(MARK_CRITERIA) || remark.contains(MARK_FORMAT) || remark.contains(MARK_REFER);
    }

    private void appendSection(StringBuilder sb, String text) {
        if (StringUtils.isNotBlank(text)) {
            sb.append(text.strip()).append('\n');
        }
    }

    private String joinSections(String criteria, String format) {
        if (StringUtils.isBlank(criteria)) {
            return StringUtils.defaultString(format).strip();
        }
        if (StringUtils.isBlank(format)) {
            return criteria.strip();
        }
        return criteria.strip() + "\n\n" + format.strip();
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
                if (!list.isEmpty()) {
                    ctx.append("\n\n现有目录清单（缩进表示层级，越深离顶层越远）：\n")
                            .append(categoryTree(list));
                }
            }
            case "tags" -> {
                var list = kpTagService.listTags(userId);
                if (!list.isEmpty()) {
                    ctx.append("\n\n现有标签清单：\n").append(
                            list.stream().map(t -> t.getName()).filter(StringUtils::isNotBlank)
                                    .map(n -> "- " + n).reduce((a, b) -> a + "\n" + b).orElse(""));
                }
            }
            default -> {
                // 配置里写了一个后端不认识的附料标记。忽略它，只用那一条配置里的规则——
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
     * 全局人设，从 sys_config 里按固定键 {@code ai.prompt.system} 取。
     *
     * **读不到就抛，不退回某一段固定的描述**：没配的时候如果悄悄换一段上去，
     * 症状是"AI 答非所问"——**看起来完全正常**，比报错难查一个数量级。
     */
    private String systemPrompt() {
        String value = sysConfigService.getSystemValue(SYSTEM_PROMPT_KEY);
        if (StringUtils.isBlank(value)) {
            throw new CoreCraftException("AI 没有配置全局人设（配置项 " + SYSTEM_PROMPT_KEY + "）");
        }
        return value;
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
     * **还读 `choices[0].finish_reason`**：上游为什么停。`length` = 撞了输出上限 ⇒
     * **内容是截断的** —— 这个信息必须送到前端去，否则用户拿到的是一段"看着完整"的残篇。
     * （2026-10-27 加：之前静默截断，界面和库里都看不出少了东西。）
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
        JsonNode choice = JsonUtils.readTree(payload).path("choices").path(0);
        JsonNode delta = choice.path("delta");
        // 字段没有 / 是 null ⇒ JsonUtils.text 给 null，这里落成空串（这一层要的是"没有增量"）
        return AiDelta.of(
                StringUtils.defaultString(JsonUtils.text(delta, "reasoning_content")),
                StringUtils.defaultString(JsonUtils.text(delta, "content")),
                StringUtils.defaultString(JsonUtils.text(choice, "finish_reason")));
    }
}