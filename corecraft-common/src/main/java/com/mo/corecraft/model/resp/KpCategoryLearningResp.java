package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 目录三指标聚合（设计 §8.1）：学习进度 / 评估覆盖 / 当前掌握度。
 *
 * <p>接口一次返回整棵树的聚合（{@code GET /kp/learning/tree}），不让前端逐目录请求。
 * <b>目录不维护第二份掌握度</b>——{@code mastery} 是加权聚合的权威值，
 * 前端只做显示，不在客户端另算一套公式（设计 §8.1 / §12）。
 *
 * <p>三个指标共用同一基础集合（设计 §8.1）：
 * <ul>
 *   <li>{@code eligible} = 未删除 AND 当前有正文 **AND 参与掌握度**；</li>
 *   <li>{@code learned} = eligible 中 {@code learned_at} 非空（activeLearned）；</li>
 *   <li>{@code assessed} = learned 中有当前 epoch 有效证据者。</li>
 * </ul>
 * 未学、已学未评估<b>不按 0 混进掌握度</b>：{@code mastery} 为空时前端渲染中性空态，
 * 不画虚假的 0%。未归类知识点允许学习和复习，但<b>不参与任何目录聚合</b>（设计 §3.1）。
 *
 * <p>⚠️ **基础集合多了"参与掌握度"这一条**（2026-11-12）：笔记/备忘类内容
 * 不该出现在学习进度和掌握度里。判据只有一处（{@code MasteryScope}），
 * 前端不自己判。
 *
 * <p>⚠️ **"目录里一共有多少条"不受这个影响** —— 那个数字来自另一处（结构索引的
 * 全集计数），仍统计全部内容。这里只管"学习相关的那些"。
 */
@Getter
@Setter
public class KpCategoryLearningResp {

    private Long categoryId;

    /**
     * 参与学习统计的知识点数（有正文、未删除、**且参与掌握度**）= 学习进度的分母。
     *
     * <p>⚠️ **不是"目录里一共几条"**：那是结构索引的总数（含笔记/备忘）。
     */
    private Integer eligibleCount;

    /** 已学知识点数（activeLearned）= 学习进度的分子、评估覆盖的分母 */
    private Integer learnedCount;

    /** 已学且有当前 epoch 有效证据数 = 评估覆盖的分子 */
    private Integer assessedCount;

    /**
     * 当前掌握度（0~100）：activeLearned 中 M 非空者的加权聚合
     * {@code Σ(M × topicWeight) / ΣtopicWeight}（设计 §8.1）。
     * {@code null} = 没有合格条目 ⇒ 中性空态，不是 0。
     */
    private BigDecimal mastery;

    /**
     * **档位分布**（方案 C 顶部概览用）：只统计**参与掌握度**且**已检验**的条目。
     *
     * <p>键 = 档位词（稳定掌握 / 初步掌握 / 待巩固 / 基本掌握 / 熟练 / 待复测），
     * 值 = 该档位下的条目数。**没出现过的档位不出现这个键**（不是 0）。
     *
     * <p>⚠️ **只统计已检验的**：未检验的条目不该占一个档位——
     * 它们在概览里由「已检验 N / M 条学习」这个分母表达，不进分布。
     */
    private Map<String, Integer> levelCounts;
}
