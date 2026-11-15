package com.mo.corecraft.service.support;

import java.util.Map;

/**
 * 「这条内容**参与不参与掌握度**」的**唯一判据**（2026-11-12）。
 *
 * <p><b>为什么要有这一份</b>：掌握度相关的展示/统计散在三处（列表卡片、目录聚合、
 * 详情页），三处各写一份判据的话，迟早有一处漂——界面上表现是"这张卡有标签、
 * 那个目录的统计却把它算进去了"，而且**什么都不报错**。
 *
 * <p><b>解析顺序只有一条</b>（三值语义）：
 * <pre>
 *   单条显式值 kp_topic.mastery_enabled
 *     &gt; 类型默认值 sys_dict_item.mastery_enabled
 *     &gt; 安全兜底（不参与）
 * </pre>
 * NULL = 「没配置，继承上一层」/ TRUE = 参与 / FALSE = 不参与。
 *
 * <p>⚠️ **NULL 和 FALSE 完全是两回事**，不能把 NULL 当 FALSE：
 * 单条 NULL = 继承类型默认（笔记类型默认关、学习类型默认开）；
 * 单条 FALSE = 这条明确不参与（哪怕它属于"学习"类型 —— 用户说的
 * 「总览这类不重要的也可以单独关掉」就是这一档）。
 *
 * <p><b>兜底为什么是"不参与"</b>：查不到类型字典项（软删/停用）时，
 * 宁可让这条**不出现**掌握度标记，也不要给它挂一个假的档位——
 * 界面上的假信息比界面上的空缺更糟。
 */
public final class MasteryScope {

    private MasteryScope() {
    }

    /**
     * 这条内容参与不参与掌握度。
     *
     * @param topicMasteryEnabled 单条覆盖（{@code kp_topic.mastery_enabled}）：null=继承
     * @param typeDefaults        类型默认值：字典项 id → 默认是否参与（null=未配置）
     * @param typeId              知识点类型（{@code kp_topic.type_id}），null=未归类
     */
    public static boolean participates(Boolean topicMasteryEnabled, Map<Long, Boolean> typeDefaults, Long typeId) {
        // ① 单条显式值优先（含 FALSE：明确关闭）
        if (topicMasteryEnabled != null) {
            return topicMasteryEnabled;
        }
        // ② 类型默认值
        if (typeId != null && typeDefaults != null) {
            Boolean def = typeDefaults.get(typeId);
            if (def != null) {
                return def;
            }
        }
        // ③ 安全兜底：查不到就**不参与**
        return false;
    }

    /**
     * 类型默认值表（字典项 id → 是否参与）。
     *
     * <p>只包含**启用中**的字典项 —— 停用的类型在界面上也不出现，
     * 让它继续参与统计就是"界面上看不见但还在算"。
     */
    public static Map<Long, Boolean> typeDefaults(Map<Long, Boolean> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Long, Boolean> out = new java.util.HashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v);
            }
        });
        return out;
    }
}
