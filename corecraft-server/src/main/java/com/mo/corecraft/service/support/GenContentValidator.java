package com.mo.corecraft.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成正文的**后处理与体检**（从 `KpGenTaskServiceImpl` 拆出，2026-11-15）：
 * 结构下限检查（checkStructure）、标题剥除与降级（stripHeading）、一句话摘要
 * （contentSummary）。三者共用同一批正文正则/常量，所以住在一起。
 *
 * <p>纯函数、无状态——执行器（任务循环）只管"什么时候问模型"，正文合不合格、
 * 落库前长什么样，判据只有这一份。
 */
public final class GenContentValidator {

    /** 正文开头可能是模型重复写的一级 / 二级标题，落库前剥掉 */
    private static final Pattern LEAD_HEADING = Pattern.compile("^#{1,2}\\s+[^\\n]*\\n");
    /** 正文中间的二级标题（模型偶尔把某一段写成 `## 常见考点`，本该是 `### 5.`） */
    private static final Pattern H2_IN_BODY = Pattern.compile("(?m)^##\\s+");
    /**
     * 模型写的知识点关联标记 `[[标题]]`。
     *
     * <p>任务模式下服务端**不解析**这个标记（解析要拿标题换 id，那是前端的知识，
     * 把它搬进 Java 等于多一份要同步的规则），但提示词里还留着关联要求 ⇒ 模型照写。
     * 原样落库的表现是正文里**直接显示 `[[引用计数算法]]`**（带方括号），
     * 而渲染层的自动链接又只认纯标题串 ⇒ 既难看又链不上。
     * ⇒ 落库前剥成纯文字「引用计数算法」，链接交给渲染层按标题现算。
     */
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\[\\]\\n]+)]]");
    /** 一条正文的合理下限（与前端 `utils/kbValidate.MIN_LEN` 同一个数） */
    private static final int MIN_CONTENT_LEN = 200;
    /** 有「### 序号. 段名」结构就算过（段名允许微调，不锁字面） */
    private static final Pattern SECTION = Pattern.compile("(?m)^###\\s*\\d+[.、]\\s*\\S");
    /** kbfig 块（Java 侧只判它的 JSON，mermaid 不是 JSON 不判） */
    private static final Pattern KBFIG_BLOCK = Pattern.compile("(?s)```kbfig\\s*\\n(.*?)```");
    /** 围栏语言：```java / ```mermaid / ```kbfig 这一行的语言标识 */
    private static final Pattern FENCE_LANG = Pattern.compile("(?m)^\\s*```([A-Za-z][A-Za-z0-9_+-]*)");
    /**
     * **前端画不出来**的图语言。
     *
     * <p>判据是「这个语言除了画图没有别的正当用途」：`svg` / `plantuml` / `dot` 这类
     * 只可能是模型想自己画图（渲染层只认 mermaid 与 kbfig，其余走默认代码块 ⇒
     * 用户看到的是一坨源码）。
     *
     * <p>⚠️ **`html` / `xml` 故意不在名单里**：讲 HTML 的知识点正文里出现 ```html
     * 是正当用法，禁掉会误伤（代价是白重写一次）。
     */
    private static final Set<String> UNRENDERABLE_LANG = Set.of(
            "mermaid", "svg", "plantuml", "puml", "dot", "graphviz", "drawio");
    /** 一个知识点最多几张图（与提示词「结构化图和 mermaid 共用这一个额度」同口径） */
    private static final int MAX_FIGURES = 1;
    /** 模型没内容硬写时最典型的形态 */
    private static final Pattern PLACEHOLDER = Pattern.compile("待补充|此处省略|内容待定|尚未给出|TODO");

    private GenContentValidator() {
    }

    /**
     * 结构下限检查（**只管结构对错，不管写得好不好**）。
     *
     * <p>与前端 `utils/kbValidate.js` 同一套判据、同一批文案 —— 那边随老链路一起删掉之后，
     * 这份是唯一一份。只留"判错了也就是多问一次"的几条，每条都能一眼看出对错。
     *
     * <p>⚠️ **图的三条是 2026-11-12 补的**：提示词里早就写了「最多一张图」「只准用
     * mermaid / kbfig」，但**没有人真的查** —— 规矩只写在提示词里等于「说过」不等于「做到」。
     * 补进来之后它们自动走执行器那条「带问题清单重写一次」的回路，不用另加机制。
     *
     * <p>⚠️ **「`graph` 里没有菱形就不叫分支」这条故意没做**：`graph LR` 画调用依赖
     * 本来就没有菱形（那是正当用法），只看文本分不出「分支」还是「依赖」⇒ 做进去就是
     * 误伤，代价是白重写一次。
     *
     * @return 问题清单（空 = 通过）
     */
    public static List<String> checkStructure(String text) {
        List<String> issues = new ArrayList<>();
        String c = text == null ? "" : text;
        if (c.trim().isEmpty()) {
            issues.add("模型没有输出正文");
            return issues;
        }
        if (!SECTION.matcher(c).find()) {
            // ⚠️ **这段文案会原文喂回给模型重写**（执行器的问题清单回路）：不许出现占位词 ——
            // 原来写「没有『### 序号. 段名』的段落结构」，模型把「段名」当成了要照抄的字面，
            // 3 篇正文的小标题全写成了「### 1. 段名：xxx」（2026-11-14 修）。
            // 判据：喂给模型的每句问题都必须是**能直接照着做的指令 + 真实样例**，不是含占位符的规则描述。
            issues.add("正文缺少带序号的小标题：每个内容块单独一行，以 ### 开头、带序号和该段自己的名字（例如 ### 1. 定义、### 2. 原理）");
        }
        if ((c.split("```", -1).length - 1) % 2 == 1) {
            issues.add("代码围栏没有闭合（正文被截断）");
        }
        Matcher kb = KBFIG_BLOCK.matcher(c);
        while (kb.find()) {
            try {
                JsonNode node = JsonUtils.readTree(kb.group(1));
                // **形状按字段认**（与前端 `shapeOf` 同一口径）：10 种 kbfig 图型
                boolean validShape = node.has("groups") || node.has("steps") || node.has("rows")
                        || node.has("root") || node.has("parts")
                        || (node.has("nodes") && node.has("edges"))
                        || (node.has("participants") && node.has("messages"))
                        || node.has("events")
                        || node.has("slots") || node.has("container");
                if (!validShape) {
                    issues.add("kbfig 块里没有可画的形状（groups / steps / rows / root / parts / nodes+edges / participants+messages / events / container 选一）");
                    break;
                }
            } catch (Exception e) {
                issues.add("kbfig 块里的 JSON 解析不了");
                break;
            }
        }
        // ---- 图的两条硬规矩（提示词里写了，这里负责真的查） ----
        Matcher fence = FENCE_LANG.matcher(c);
        while (fence.find()) {
            String lang = fence.group(1).toLowerCase(Locale.ROOT);
            if (UNRENDERABLE_LANG.contains(lang)) {
                issues.add("用了画不出来的图语言「" + lang + "」（只认 kbfig）");
                break;
            }
        }
        int figures = countKbfigBlocks(c);
        if (figures > MAX_FIGURES) {
            issues.add("配了 " + figures + " 张图（一个知识点最多 " + MAX_FIGURES + " 张）");
        }
        if (c.trim().length() < MIN_CONTENT_LEN) {
            issues.add("正文只有 " + c.trim().length() + " 字（不足 " + MIN_CONTENT_LEN + "）");
        }
        if (PLACEHOLDER.matcher(c).find()) {
            issues.add("正文里有「待补充 / 此处省略」这类占位话");
        }
        return issues;
    }

    /**
     * 剥掉模型重复写的标题，并把正文中间的 `##` **降级**成 `###`。
     *
     * <p>⚠️ **不要截断**。原来这里是「遇到下一个 `## ` 就截断，只留第一条」，
     * 想对齐前端 `parsePoints` 的多条切分 —— 但实测（2026-11-10，关思考那批）
     * 模型偶尔把「常见考点」那一段写成 `## 常见考点`（本该 `### 5.`），
     * 一截就**整段丢掉**：15 条里有 11 条缺考点段，单条少 117~235 字，
     * 而且落库后看不出少了东西（正文结尾是完整的）。
     *
     * <p>改成降级：`## X` → `### X`，内容一个不丢，只是渲染时小一号。
     * 代价是万一模型真写了「另一条知识点」，会被并进这一条 —— 但材料明确只让写一条，
     * 这种情况比"整段考点被吃掉"罕见得多，而且**多**比**丢**好。
     */
    public static String stripHeading(String text) {
        String t = text.trim();
        Matcher m = LEAD_HEADING.matcher(t);
        boolean hasLead = m.find();
        // **只在第一行确实是个"标题"时才剥**（≤ 40 字）。模型偶尔把标题和正文写成一行
        // （`## ZGC 是一种…`，实测剥掉 125 字）；不判长度就按行剥，等于把开头一段吃掉。
        if (hasLead && m.group().length() <= 40) {
            String rest = t.substring(m.end()).trim();
            if (!rest.isEmpty()) {
                t = rest;
            }
        }
        return WIKILINK.matcher(H2_IN_BODY.matcher(t).replaceAll("### ")).replaceAll("$1");
    }

    /**
     * 一条正文的**一句话摘要**（卡片第二行直接显示）。
     *
     * <p>为什么由后端算：段数、图数、表数都要读正文，前端拿不到 ——
     * 让前端为了显示一句话把几 MB 正文都拉回去，是把带宽和等待都花在装饰上。
     */
    public static String contentSummary(String text, boolean retried) {
        String c = text == null ? "" : text;
        Matcher m = SECTION.matcher(c);
        int sections = 0;
        while (m.find()) {
            sections += 1;
        }
        int figures = countKbfigBlocks(c);
        int tables = 0;
        for (String line : c.split("\n")) {
            if (line.trim().startsWith("|")) {
                tables += 1;
            }
        }
        // 表格按"块"算：连续的 | 行只算一张（表头 + 分隔 + 数据行）
        tables = tables > 0 ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        sb.append(sections).append(" 段 ").append(c.trim().length()).append(" 字");
        if (figures > 0) {
            sb.append(" · 图 ").append(figures);
        }
        if (tables > 0) {
            sb.append(" · 表 1");
        }
        if (retried) {
            sb.append(" · 重试过");
        }
        return sb.toString();
    }

    private static int countKbfigBlocks(String text) {
        int count = 0;
        for (int i = text.indexOf("```kbfig"); i >= 0; i = text.indexOf("```kbfig", i + 8)) {
            count += 1;
        }
        return count;
    }
}
