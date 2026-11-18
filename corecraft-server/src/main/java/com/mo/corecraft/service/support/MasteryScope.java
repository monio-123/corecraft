package com.mo.corecraft.service.support;

/**
 * 「这条内容**参与不参与掌握度**」的**唯一判据**（2026-11-16 知识模型定稿 D10 改版）。
 *
 * <p><b>为什么要有这一份</b>：掌握度相关的展示/统计散在三处（列表卡片、目录聚合、
 * 详情页），三处各写一份判据的话，迟早有一处漂——界面上表现是"这张卡有标签、
 * 那个目录的统计却把它算进去了"，而且**什么都不报错**。
 *
 * <p><b>解析顺序只有一条</b>（两值语义）：
 * <pre>
 *   单条显式值 kp_topic.mastery_enabled
 *     &gt; 有评估证据（学过了 / 待复测）
 * </pre>
 * NULL = 「没配置，走默认」/ TRUE = 参与 / FALSE = 明确不参与。
 *
 * <p>⚠️ **NULL 和 FALSE 完全是两回事**，不能把 NULL 当 FALSE：
 * 单条 NULL = 走默认（有没有证据）；单条 FALSE = 这条明确不参与
 * （哪怕它已经被考过 —— 用户说「总览这类不重要的也可以单独关掉」就是这一档）。
 *
 * <p><b>为什么默认是「有证据」而不是「某种类型」</b>（2026-11-16）：
 * 原来这一层是"知识点类型默认值"（字典 `kp_topic_type` 的 笔记/学习）——
 * 那是**按出生地管现居**：一条手动写成体系内容的笔记类型还是「笔记」，
 * 于是"它该不该参与掌握度"永远问不准。类型轴退役后，判据回到**内容自己有没有被检验过**
 * ——索引/导读类内容天然没证据，自动不参与；学过的内容自动参与。
 */
public final class MasteryScope {

    private MasteryScope() {
    }

    /**
     * 这条内容参与不参与掌握度。
     *
     * @param topicMasteryEnabled 单条覆盖（{@code kp_topic.mastery_enabled}）：null=走默认
     * @param hasEvidence         有没有有效评估证据（学过了 / 待复测）
     */
    public static boolean participates(Boolean topicMasteryEnabled, boolean hasEvidence) {
        // ① 单条显式值优先（含 FALSE：明确关闭）
        if (topicMasteryEnabled != null) {
            return topicMasteryEnabled;
        }
        // ② 默认：有证据才参与（没被检验过的内容不进掌握度口径）
        return hasEvidence;
    }

    /**
     * 这条内容算不算「**可学条目**」（目录聚合的分母 M）。
     *
     * <p>⚠️ **它和 {@link #participates} 不是同一件事，别合并**（2026-11-16 D10）：
     * M 的含义是"这本书里有多少条可学的"——所以**没被检验过的也算**（那是分母该有的样子）；
     * 而「参与掌握度」说的是"这条有没有掌握度状态"——没检验过就没有。
     * 若把 M 收窄成"已检验的"，「已检验 N / M」恒等于 100%，界面上那个比值就没意义了。
     *
     * <p>只有**显式关闭**（FALSE）才不算：用户说"这条不算学习材料"就该把它从分母里剔掉。
     */
    public static boolean eligible(Boolean topicMasteryEnabled) {
        return !Boolean.FALSE.equals(topicMasteryEnabled);
    }
}
