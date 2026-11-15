package com.mo.corecraft.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * **任务的节点链**：任务 → 主节点 → 步骤组 → 步骤。
 *
 * <p>## 为什么节点定义放在后端
 *
 * <p>节点链是**任务类型的属性**（生成主题 = 生成目录 → 确认 → 生成正文；
 * AI 归类 = 归类 → 确认）。前端若也维护一份，两处必然漂 ——
 * 症状是"卡片上写着三个节点，点进去只有一个有内容"，而且很难查。
 *
 * <p>⇒ **只有这一份定义**，后端算好节点状态随任务一起下发，
 * 前端只负责渲染 ⇒ **加一种任务类型不用改前端一行**。
 *
 * <p>## 节点有三种
 *
 * <ul>
 *   <li><b>模型节点</b>：有步骤（生成目录 / 生成正文 / 归类 / 出题）。</li>
 *   <li><b>人工节点</b>：没有步骤，人要动手（确认 / 采纳）⇒ 单独一档「待确认」。</li>
 *   <li>模型节点之间**允许跳过**（老任务只有正文没目录）⇒ 节点链按类型定，不按"跑到哪"定。</li>
 * </ul>
 *
 * <p>## 状态口径（和步骤同一套）
 *
 * <p>{@code pending} 空心灰 / {@code running} 蓝（呼吸）/ {@code ok} 绿 /
 * {@code failed} 红 / {@code awaiting} 琥珀（等人）/ {@code skipped} 灰（被跳过）/
 * {@code paused} 琥珀（**人停下来的**，不呼吸）。
 *
 * <p>⚠️ **{@code running} 的前提是"任务还活着"**：执行线程已经没了（服务重启 / 已暂停 / 已失败）
 * 的任务，任何节点都不许画成进行中 —— 否则界面上那条节点会一直闪，
 * 用户看到的是"明明暂停了，进度还在跑"。
 *
 * <p>⚠️ **人工节点的「已越过」判据**：**后继节点已经有步骤** ——
 * 「确认」被越过，意味着正文步骤已经建出来了（前端建完结构才提交正文）。
 * 比"看任务状态"可靠：任务 succeeded 之后如果又要重跑正文，
 * 「确认」仍然是 ok（它确实过了），而不是被打回 pending。
 */
public final class GenTaskNodes {

    private GenTaskNodes() {
    }

    /** 任务类型 → 节点链 */
    public static final String TYPE_THEME = "theme";
    public static final String TYPE_PLAN = "plan";
    public static final String TYPE_CONTENT = "content";
    public static final String TYPE_CLASSIFY = "classify";
    public static final String TYPE_EXAM = "exam";
    public static final String TYPE_QUIZ = "quiz";
    public static final String TYPE_GRADE = "grade";

    /** 节点 key（**不是 phase** —— phase 是节点内部的步骤组） */
    public static final String NODE_OUTLINE = "outline";
    public static final String NODE_CONFIRM = "confirm";
    /** 建目录：把人确认过的树落进知识库（2026-11-13 从前端搬进任务链） */
    public static final String NODE_BUILD = "build";
    public static final String NODE_BODY = "body";
    public static final String NODE_CLASSIFY = "classify";
    public static final String NODE_ADOPT = "adopt";
    public static final String NODE_EXAM = "exam";
    public static final String NODE_QUIZ = "quiz";
    public static final String NODE_GRADE = "grade";
    /** 出题后的人工节点（随堂测验 / 章节考试共用）：点「作答」才解析建卷 */
    public static final String NODE_ANSWER = "answer";

    /** 步骤组（phase）key */
    public static final String PH_SKELETON = "skeleton";
    public static final String PH_EXPAND = "expand";
    public static final String PH_VERIFY = "verify";
    public static final String PH_BUILD = "build";
    public static final String PH_WRITE = "write";
    public static final String PH_CLASSIFY = "classify";
    public static final String PH_EXAM = "exam";
    public static final String PH_QUIZ = "quiz";
    public static final String PH_GRADE = "grade";

    /** 步骤组定义（key + 人话名） */
    public record PhaseDef(String key, String name) {
    }

    /**
     * 节点定义。
     *
     * <p>{@code desc} 是**给人看的**（「AI 把主题拆成目录树」）—— 卡片上节点旁要能
     * "秒懂这一步是干嘛的"（2026-11-13 用户要求），它属于协议、跟着节点走，
     * 不能由前端按类型猜（猜就是第二份定义，必漂）。
     */
    public record NodeDef(String key, String name, String desc, List<PhaseDef> phases, boolean manual) {
    }

    /** 节点下的一个步骤组（一行小结） */
    public record Group(String key, String name, int done, int total, int running, int failed) {
    }

    /**
     * 节点状态（随任务下发）。
     *
     * <p>{@code elapsedMs} = 这个节点**已经花掉的时间**（它名下所有步骤的时长和）。
     * 用户要看的是"每一段各花了多久"（2026-11-13），不是只有一个总数。
     * 人工节点（确认）名下没有步骤 ⇒ 恒 0，前端不显示。
     */
    public record Node(String key, String name, String desc, String status, int done, int total,
                       long elapsedMs, String summary, List<Group> groups) {
    }

    private static final List<PhaseDef> PHASES_OUTLINE = List.of(
            new PhaseDef(PH_SKELETON, "骨架"),
            new PhaseDef(PH_EXPAND, "逐章展开"),
            new PhaseDef(PH_VERIFY, "校验"));

    /** 建目录节点名下只有一组（2026-11-13 从「生成正文」里拆出来单独成节点） */
    private static final List<PhaseDef> PHASES_BUILD = List.of(
            new PhaseDef(PH_BUILD, "建目录"));

    private static final List<PhaseDef> PHASES_BODY = List.of(
            new PhaseDef(PH_WRITE, "写正文"));

    private static final List<PhaseDef> PHASES_CLASSIFY = List.of(
            new PhaseDef(PH_CLASSIFY, "归类"));

    private static final List<PhaseDef> PHASES_EXAM = List.of(new PhaseDef(PH_EXAM, "出题"));
    private static final List<PhaseDef> PHASES_QUIZ = List.of(new PhaseDef(PH_QUIZ, "出题"));
    private static final List<PhaseDef> PHASES_GRADE = List.of(new PhaseDef(PH_GRADE, "判分"));

    private static final List<NodeDef> DEFS_THEME = List.of(
            new NodeDef(NODE_OUTLINE, "生成目录", "AI 把主题拆成目录树", PHASES_OUTLINE, false),
            new NodeDef(NODE_CONFIRM, "确认", "人工核对这棵树，改完再建", List.of(), true),
            new NodeDef(NODE_BUILD, "建目录", "把确认过的树落进知识库", PHASES_BUILD, false),
            new NodeDef(NODE_BODY, "生成正文", "逐条写知识点正文", PHASES_BODY, false));

    private static final List<NodeDef> DEFS_PLAN = List.of(
            new NodeDef(NODE_OUTLINE, "生成目录", "AI 把主题拆成目录树", PHASES_OUTLINE, false),
            new NodeDef(NODE_CONFIRM, "确认", "人工核对这棵树，改完再建", List.of(), true));

    private static final List<NodeDef> DEFS_BODY = List.of(
            new NodeDef(NODE_BODY, "生成正文", "逐条写知识点正文", PHASES_BODY, false));

    private static final List<NodeDef> DEFS_CLASSIFY = List.of(
            new NodeDef(NODE_CLASSIFY, "归类", "AI 给每条建议归属目录", PHASES_CLASSIFY, false),
            new NodeDef(NODE_ADOPT, "确认", "人工核对建议，挑着采纳", List.of(), true));

    /** 出题（章节考试）：出题（模型）→ 作答（人工，点「作答」才解析建卷——归类同构） */
    private static final List<NodeDef> DEFS_EXAM = List.of(
            new NodeDef(NODE_EXAM, "出题", "按范围和难度出题", PHASES_EXAM, false),
            new NodeDef(NODE_ANSWER, "作答", "人工开卷作答，答完才入档", List.of(), true));

    /** 出题（随堂测验）：出题（模型）→ 作答（人工，点「作答」才解析建卷——归类同构） */
    private static final List<NodeDef> DEFS_QUIZ = List.of(
            new NodeDef(NODE_QUIZ, "出题", "就这条知识出几道题", PHASES_QUIZ, false),
            new NodeDef(NODE_ANSWER, "作答", "人工开卷作答，答完才入档", List.of(), true));

    private static final List<NodeDef> DEFS_GRADE = List.of(
            new NodeDef(NODE_GRADE, "判分", "AI 逐题给分和讲评", PHASES_GRADE, false));

    /**
     * 这个任务类型的节点链。
     *
     * <p>⚠️ **认不出来给一个空链**（不是兜底节点）—— 前端拿到空链就只渲染标题行，
     * 不会凭空造一个"执行"节点骗人。
     */
    public static List<NodeDef> defsOf(String type) {
        return switch (type == null ? "" : type) {
            case TYPE_THEME -> DEFS_THEME;
            case TYPE_PLAN -> DEFS_PLAN;
            case TYPE_CONTENT -> DEFS_BODY;
            case TYPE_CLASSIFY -> DEFS_CLASSIFY;
            case TYPE_EXAM -> DEFS_EXAM;
            case TYPE_QUIZ -> DEFS_QUIZ;
            case TYPE_GRADE -> DEFS_GRADE;
            default -> List.of();
        };
    }

    /**
     * 老任务的 {@code stage}（是 phase key）→ 当前节点 key。
     *
     * <p>⚠️ **老数据不能直接当节点 key 用**：{@code stage='skeleton'} 是"在骨架这一步"，
     * 而节点是"生成目录"（骨架/展开/校验合起来才是它）。⇒ 映射到节点。
     */
    public static String currentNode(KpGenTask task) {
        String stage = task == null ? null : task.getStage();
        if (stage == null || stage.isBlank() || "done".equals(stage)) {
            return null;
        }
        return switch (stage) {
            case PH_SKELETON, PH_EXPAND, PH_VERIFY -> NODE_OUTLINE;
            case PH_BUILD -> NODE_BUILD;
            case PH_WRITE -> NODE_BODY;
            case PH_CLASSIFY -> NODE_CLASSIFY;
            case PH_EXAM -> NODE_EXAM;
            case PH_QUIZ -> NODE_QUIZ;
            case PH_GRADE -> NODE_GRADE;
            default -> stage;
        };
    }

    /**
     * 算节点状态。
     *
     * @param task  任务（要它的 status / stage / result）
     * @param agg   phase → status → 条数（**调用方查一次全量的步骤聚合**，别逐任务查）
     */
    public static List<Node> states(KpGenTask task, Map<String, Map<String, Integer>> agg,
                                     Map<String, Long> msByPhase) {
        List<NodeDef> defs = defsOf(task == null ? null : task.getType());
        List<Node> out = new ArrayList<>(defs.size());
        for (int i = 0; i < defs.size(); i++) {
            NodeDef d = defs.get(i);
            List<Group> groups = new ArrayList<>();
            int total = 0;
            int ok = 0;
            int running = 0;
            int failed = 0;
            for (PhaseDef p : d.phases()) {
                Map<String, Integer> m = agg == null ? null : agg.get(p.key());
                int t = sum(m);
                int o = get(m, "ok");
                int r = get(m, "running");
                int f = get(m, "failed");
                total += t;
                ok += o;
                running += r;
                failed += f;
                // **没有步骤的组不显示**（老任务的 phase 是空串，聚合里也没有它）
                if (t > 0) {
                    groups.add(new Group(p.key(), p.name(), o, t, r, f));
                }
            }
            String status;
            if (d.manual()) {
                status = manualStatus(task, defs, i, agg);
            } else if (total == 0) {
                status = "pending";
            } else if (running > 0) {
                // ⚠️ **任务不在了就没有"进行中"**（2026-11-12 用户实测）：
                // 进程重启会留下卡在 `running` 的步骤，`canceled` / `failed` 的任务也一样。
                // 只看步骤的话，节点会**永远呼吸** —— 用户明明暂停了，
                // 界面上「生成正文 72/291」还在闪，像还在跑。
                status = alive(task) ? "running" : (paused(task) ? "paused" : "pending");
            } else if (failed > 0) {
                status = "failed";
            } else if (ok == total) {
                status = "ok";
            } else {
                // 有成功也有没跑的 ⇒ 还活着就是在进行，停了就是暂停 / 待跑
                status = alive(task) ? "running" : (paused(task) ? "paused" : "pending");
            }
            // **每段各自的已用时**（它名下所有步骤的时长和）。人工节点没有步骤 ⇒ 0。
            long elapsedMs = 0;
            for (PhaseDef p : d.phases()) {
                Long v = msByPhase == null ? null : msByPhase.get(p.key());
                if (v != null) {
                    elapsedMs += v;
                }
            }
            out.add(new Node(d.key(), d.name(), d.desc(), status, ok, total, elapsedMs,
                    summaryOf(task, d, status, ok, total), groups));
        }
        return out;
    }

    /**
     * 任务是不是**还活着**（有线程在替它干活）。
     *
     * <p>只有这三个状态会有执行线程 —— 其余（待确认 / 已完成 / 失败 / 已暂停）
     * 都意味着"此刻不会有人推进它"，节点也就不能画成进行中。
     */
    private static boolean alive(KpGenTask task) {
        String st = task == null ? null : task.getStatus();
        return "queued".equals(st) || "running".equals(st) || "canceling".equals(st);
    }

    /** 任务是被**人**停下来的（不是跑完、也不是跑坏）*/
    private static boolean paused(KpGenTask task) {
        String st = task == null ? null : task.getStatus();
        return "canceled".equals(st) || "canceling".equals(st);
    }

    /**
     * 人工节点的状态（**人工节点 = "人要动手"的那一段**）。
     *
     * <p>顺序不能乱，每一条都有它存在的理由：
     * <ol>
     *   <li><b>已越过</b>（后继节点已经有步骤）⇒ {@code ok}。
     *       判据是**后继有步骤**，不是"任务成功了" —— 任务成功之后如果又要重跑正文，
     *       「确认」仍然是 ok（它确实过了），不该被打回 pending。</li>
     *   <li><b>人已经办过</b>（{@code confirmed_at} 有值）⇒ {@code ok}。
 *       专门给**链尾**的人工节点用 —— 它没有后继，判不出「已越过」。</li>
 *   <li><b>任务在等确认</b>（awaiting）⇒ {@code awaiting}。</li>
     *   <li><b>任务中止</b>（failed / canceled）⇒ {@code skipped}（没走到，不是失败）。</li>
     *   <li><b>前一个模型节点已经跑完</b> ⇒ {@code awaiting}（模型干完了，轮到人）。
     *       这条覆盖"归类任务跑完了等人采纳"—— 任务状态是 succeeded，但人还没采纳。</li>
     *   <li>其余 ⇒ {@code pending}（还没轮到）。</li>
     * </ol>
     *
     * <p>⚠️ **没有"链尾 + 任务成功 ⇒ ok"这条规则**：
     * 任务状态 succeeded 只说明**模型那一段**跑完了，人工节点的事还没办 ——
     * 有这条规则时，归类任务（部分步骤失败 ⇒ 任务仍可能是 succeeded）
     * 的「确认」节点会显示绿色，看着像已经采纳完了，实际一个字都没落库。
     */
    private static String manualStatus(KpGenTask task, List<NodeDef> defs, int idx,
                                       Map<String, Map<String, Integer>> agg) {
        if (passed(defs, idx, agg)) {
            return "ok";
        }
        // **人已经办过了**（采纳 / 确认都写这一个时间）。
        // ⚠️ 链尾的人工节点（AI 归类 → 确认/采纳）**没有后继**，上面那条「已越过」永远不成立
        // ⇒ 只能靠这个显式标记，否则卡片会一直停在「待确认」。
        if (task != null && task.getConfirmedAt() != null) {
            return "ok";
        }
        String st = task == null ? null : task.getStatus();
        if ("awaiting".equals(st)) {
            return "awaiting";
        }
        if ("canceled".equals(st)) {
            // ⚠️ **暂停 ≠ 跳过**：跳过是"再也不会走到这一步"，暂停是"停在这儿、之后接着走"。
            // 判成 skipped 的话，暂停的任务上「确认」节点写着"已跳过"，
            // 用户会以为这一步被放弃了（恢复之后它其实还会回来）。
            return "paused";
        }
        if ("failed".equals(st)) {
            return "skipped";
        }
        // ⚠️ **前一个模型节点跑完了 ⇒ 等人**：
        // 归类任务跑完（succeeded）之后，"确认 / 采纳"这一段是**人**的事，
        // 不是"已经完成"。判成 ok 的话，用户看到三个绿点，以为已经全部落库了
        // —— 而实际上一个字都还没写进知识库。
        if (predecessorDone(defs, idx, agg)) {
            return "awaiting";
        }
        return "pending";
    }

    /**
     * 前一个模型节点是不是已经跑完（全部步骤 ok）。
     *
     * <p>⚠️ **只看紧邻的前一个**：中间隔着别的节点时，那一段还没轮到这个人工节点。
     */
    private static boolean predecessorDone(List<NodeDef> defs, int idx,
                                           Map<String, Map<String, Integer>> agg) {
        if (idx <= 0) {
            return false;
        }
        NodeDef prev = defs.get(idx - 1);
        if (prev.manual()) {
            return false;
        }
        int total = 0;
        int ok = 0;
        for (PhaseDef p : prev.phases()) {
            Map<String, Integer> m = agg == null ? null : agg.get(p.key());
            total += sum(m);
            ok += get(m, "ok");
        }
        // ⚠️ **必须有步骤且全部成功**：没有步骤（total=0）不算"跑完了"
        // （那只是"这一步还没开始"）。
        return total > 0 && ok == total;
    }

    /** 这个人工节点是不是已经被越过了（后继节点已有步骤） */
    private static boolean passed(List<NodeDef> defs, int idx, Map<String, Map<String, Integer>> agg) {
        for (int j = idx + 1; j < defs.size(); j++) {
            NodeDef nd = defs.get(j);
            if (nd.manual()) {
                continue;
            }
            int t = 0;
            for (PhaseDef p : nd.phases()) {
                t += sum(agg == null ? null : agg.get(p.key()));
            }
            if (t > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 节点的一句话摘要。
     *
     * <p>只讲**事实**（多少条 / 多少点 / 什么提示），不写解释性长句 ——
     * 卡片上已经有状态颜色，再加一句"模型已跑完，下一步是你"就是啰嗦。
     */
    private static String summaryOf(KpGenTask task, NodeDef d, String status, int ok, int total) {
        if (d.manual()) {
            String s = confirmSummary(task);
            return s == null ? "" : s;
        }
        if (total == 0) {
            return "";
        }
        if (ok == total && total > 0) {
            return total + " 项已完成";
        }
        return ok + " / " + total;
    }

    /**
     * 「确认」节点的摘要：目录的规模 + 校验提示。
     *
     * <p>⚠️ **只取数字，不带树**：树有十几 KB，塞进任务列表接口会把 payload 撑大，
     * 而列表上只需要"8 模块 · 344 点"这种规模感。
     */
    private static String confirmSummary(KpGenTask task) {
        if (task == null || task.getResult() == null || task.getResult().isBlank()) {
            return null;
        }
        try {
            JsonNode r = JsonUtils.readTree(task.getResult());
            int modules = r.path("modules").asInt(0);
            int leaves = r.path("leaves").asInt(0);
            int issues = r.path("issues").size();
            StringBuilder sb = new StringBuilder();
            if (modules > 0) {
                sb.append(modules).append(" 模块");
            }
            if (leaves > 0) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(leaves).append(" 点");
            }
            if (issues > 0) {
                sb.append(" · ").append(issues).append(" 个提示");
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static int sum(Map<String, Integer> m) {
        if (m == null) {
            return 0;
        }
        int n = 0;
        for (Integer v : m.values()) {
            if (v != null) {
                n += v;
            }
        }
        return n;
    }

    private static int get(Map<String, Integer> m, String k) {
        if (m == null) {
            return 0;
        }
        Integer v = m.get(k);
        return v == null ? 0 : v;
    }

    /**
     * 把一批步骤（只有 taskId / type / phase / status 四列）聚合成 {@code phase → status → 条数}。
     *
     * <p>**入参按任务分组**：一个任务一张表，key 是 taskId。
     *
     * <p>⚠️ **老数据的 phase 是空串**（阶段化之前建的步骤）——
     * 直接当成 {@code write} 的话，**归类任务的 45 条步骤会算到「生成正文」节点上**，
     * 于是「归类」节点永远是 pending（看着像一步没跑），而「确认」节点却是绿的
     * （实际一条都没落库）。⇒ 空 phase 要**按任务类型回落**到该类型唯一的 phase。
     */
    public static Map<String, Map<String, Map<String, Integer>>> aggregate(
            List<StepRow> rows) {
        Map<String, Map<String, Map<String, Integer>>> out = new LinkedHashMap<>();
        for (StepRow r : rows) {
            String phase = r.phase() == null || r.phase().isBlank()
                    ? legacyPhaseOf(r.type())
                    : r.phase();
            out.computeIfAbsent(String.valueOf(r.taskId()), k -> new LinkedHashMap<>())
                    .computeIfAbsent(phase, k -> new LinkedHashMap<>())
                    .merge(r.status() == null ? "pending" : r.status(), 1, Integer::sum);
        }
        return out;
    }

    /**
     * 老步骤（phase 为空）按任务类型回落到哪个 phase。
     *
     * <p>只给**该类型只有一组步骤**的类型回落 —— 目录类型（skeleton/expand/verify）
     * 老数据本来就没有阶段划分，回落到骨架是错的（会让展开步骤显示在骨架里），
     * 所以目录类型**不回落**：宁可空着（节点显示 pending），也不要显示错误的位置。
     *
     * <p>⚠️ **列表聚合和详情聚合必须用同一个回落口径** ——
     * 两边不一致的话，节点链上有「归类 45/45」而点进去一步都没有
     * （前端按 {@code node.groups} 的 key 去分组，key 对不上就全被过滤掉）。
     */
    public static String legacyPhaseOf(String type) {
        if (type == null) {
            return PH_WRITE;
        }
        return switch (type) {
            case TYPE_CLASSIFY -> PH_CLASSIFY;
            case TYPE_EXAM -> PH_EXAM;
            case TYPE_QUIZ -> PH_QUIZ;
            case TYPE_GRADE -> PH_GRADE;
            default -> PH_WRITE;
        };
    }

    /** 聚合用的一行（只要四列，别把材料查出来） */
    public record StepRow(Long taskId, String type, String phase, String status, long durationMs) {
    }

    /**
     * 每个任务、每个步骤组**各自花掉的时间**（毫秒）。
     *
     * <p>⚠️ 和 {@link #aggregate} 用同一套 phase 回落口径（老步骤空 phase 按类型回落）——
     * 否则节点链上显示 45 条、时长却是 0（key 对不上）。
     */
    public static Map<String, Map<String, Long>> durations(List<StepRow> rows) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (StepRow r : rows) {
            String phase = r.phase() == null || r.phase().isBlank()
                    ? legacyPhaseOf(r.type())
                    : r.phase();
            out.computeIfAbsent(String.valueOf(r.taskId()), k -> new LinkedHashMap<>())
                    .merge(phase, r.durationMs() == 0 ? 0L : r.durationMs(), Long::sum);
        }
        return out;
    }
}
