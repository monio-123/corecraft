package com.mo.corecraft.service.support;

import com.mo.corecraft.utils.OutlineText;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录规划的**纯算法段**（从 `KpGenTaskServiceImpl` 拆出，2026-11-15）：
 * 展开材料拼装、片段合并进全书树、结构校验、摘要。
 *
 * <p>纯函数、无状态——「什么时候问模型、结果写哪张表」归执行器（任务循环），
 * 「材料长什么样、树拼得对不对」的判据只有这一份。
 */
public final class GenTaskPlanner {

    private GenTaskPlanner() {
    }

    /**
     * 展开某一章的材料。
     *
     * <p>⚠️ **必须把兄弟章名带上**：不带的话模型会自己扩张到相邻领域
     * （让它展开「垃圾收集」，它顺手把「内存分配」也写了），合并时就是成片重复。
     */
    public static String expandMaterial(String theme, List<OutlineText.Node> modules, int index, String pref) {
        OutlineText.Node cur = modules.get(index);
        StringBuilder sb = new StringBuilder();
        sb.append("主题：").append(theme);
        if (StringUtils.hasText(pref)) {
            sb.append("\n\n生成偏好（按它取舍）：\n").append(pref);
        }
        sb.append("\n\n全书章节（已定稿，**这次只展开其中一章**）：\n");
        for (OutlineText.Node m : modules) {
            sb.append("- ").append(m.name());
            if (StringUtils.hasText(m.intent())) {
                sb.append(" ｜ ").append(m.intent());
            }
            sb.append('\n');
        }
        sb.append("\n这次要展开的章节：").append(cur.name());
        if (StringUtils.hasText(cur.intent())) {
            sb.append("（").append(cur.intent()).append("）");
        }
        return sb.toString();
    }

    public static String namesOf(List<OutlineText.Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (OutlineText.Node n : nodes) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(n.name());
        }
        return sb.toString();
    }

    /**
     * 一个展开片段的一句话摘要。
     *
     * <p>「节」只数**第 2 层**（模块下面的章），不是数全部节点 ——
     * 后者会把知识点也算进"节"，写出来是「77 节 · 59 点」这种自相矛盾的数字。
     */
    public static String summaryOf(List<OutlineText.Node> fragment) {
        int sections = 0;
        for (OutlineText.Node n : fragment) {
            if (n.depth() == 2) {
                sections += 1;
            }
        }
        return sections + " 节 · " + OutlineText.leafCount(fragment) + " 点";
    }

    /**
     * 把一章的展开片段拼进全书树（新跑 / 恢复复用两个分支共用，原来两处各写一遍）。
     *
     * <p>⚠️ **先锚定再拼**（2026-11-13 根修）：片段第一行（模块行）由
     * {@link OutlineText#anchorFirstLevel} 强制归到片段第 1 层。模型偶尔给模块行
     * 自己带上 {@code ├── }（前导 4 格 = 解析成第 2 层），不锚定的话这一章整体
     * 沉一级、挂到上一章最后一个节点底下（任务 #49 的模块 3 实锤：
     * 「3. 数据链路层与局域网」挂到了「2.6 传输损伤」下面），而 verifyTree
     * 对这种连续深度全盲。
     *
     * @return 锚定后的片段（调用方拿它做摘要 / 空判）
     */
    public static List<OutlineText.Node> appendFragment(List<OutlineText.Node> tree, String text) {
        List<OutlineText.Node> fragment = OutlineText.anchorFirstLevel(OutlineText.parse(text));
        // 片段整体挂到根下面：锚定后的片段第 1 层就是"篇"这一层（全书第 2 层）
        for (OutlineText.Node n : fragment) {
            tree.add(new OutlineText.Node(n.depth() + 1, n.name(), n.intent()));
        }
        return fragment;
    }

    /**
     * 目录结构校验（**只判结构，不判内容好坏**）。
     *
     * <p>五条都能一眼看出对错，而且都是"合并分段结果"才会出现的问题：
     * 重名（两章各写了一个同名叶子）、跳层（章下面直接挂子章节）、
     * 叶子太少（模型没展开）、以及章数为 1（模型把所有内容塞进一章）。
     * 第五条**章号不在章层**（2026-11-13 任务 #49 实锤后补）：锚定只治
     * 「模块行带树线」那一种格式抖动；章号是模型自己写的语义，带「N. 」的
     * 节点就该在第 2 层——沉到别处就是铁证（当时「3. 数据链路层与局域网」
     * 挂进了第 2 章底下，前四条全没响）。
     */
    public static List<String> verifyTree(List<OutlineText.Node> tree) {
        List<String> issues = new ArrayList<>();
        java.util.Map<String, Integer> leafSeen = new java.util.HashMap<>();
        int prevDepth = 0;
        int jump = 0;
        int modules = 0;
        int sunkChapter = 0;
        for (int i = 0; i < tree.size(); i++) {
            OutlineText.Node n = tree.get(i);
            boolean leaf = i + 1 >= tree.size() || tree.get(i + 1).depth() <= n.depth();
            if (n.depth() == 2) {
                modules += 1;
            }
            if (prevDepth > 0 && n.depth() > prevDepth + 1) {
                jump += 1;
            }
            prevDepth = n.depth();
            if (leaf) {
                leafSeen.merge(n.name(), 1, Integer::sum);
            }
            if (OutlineText.hasChapterIndex(n.name()) && n.depth() != 2) {
                sunkChapter += 1;
            }
        }
        int dup = 0;
        for (Integer v : leafSeen.values()) {
            if (v > 1) {
                dup += v - 1;
            }
        }
        if (dup > 0) {
            issues.add("重名叶子 " + dup + " 个");
        }
        if (jump > 0) {
            issues.add("跳层 " + jump + " 处");
        }
        if (sunkChapter > 0) {
            issues.add("章号不在章层 " + sunkChapter + " 处（如「N.」挂在别的章下面）");
        }
        if (leafSeen.size() < 20) {
            issues.add("知识点只有 " + leafSeen.size() + " 个，偏少");
        }
        if (modules <= 1) {
            issues.add("只有 " + modules + " 个章节，模型没有分篇");
        }
        return issues;
    }
}
