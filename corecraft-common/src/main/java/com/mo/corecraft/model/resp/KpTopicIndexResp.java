package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识点的**结构索引**（轻量投影）。
 *
 * <p><b>它不含正文</b>，只有"这条在哪、叫什么、属于什么、写了没有"。
 *
 * <p><b>为什么要另立一份</b>（2026-10-27 性能诊断）：
 * 目录树要在知识点那一层显示**标题**、每个目录要显示**条数**、列表页的分面计数
 * 要按**类型/标签/有无正文**分类统计 —— 这些全都需要"全集"，
 * 但**一条正文都不需要**。
 * 而正文那 90 万字符（TEXT 列）是 JDBC 逐行读回 JVM 的主要耗时：
 * 实测读全文 4416ms、不读正文 78ms。
 * ⇒ 全集用这份轻投影（不碰 content 列），正文只在**要显示正文的那一页**里取。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code hasContent} —— **有没有正文**（布尔）。不是正文本身。
 *       "这条还没写正文"要判的是这件事，而正文是几十万字符。</li>
 *   <li>{@code tags} —— 标签名数组的 JSON 字符串（和 {@link KpTopicResp} 同一形状）。</li>
 * </ul>
 */
@Getter
@Setter
public class KpTopicIndexResp implements Taggable {

    private Long id;

    private String title;

    /** 所属目录ID（null = 未归类） */
    private Long categoryId;

    /** 知识点类型，指向 sys_dict_item.id */
    private Long typeId;

    /**
     * 是否参与掌握度的**单条覆盖**（库里的原始可空值，还没解析）。
     *
     * <p>⚠️ 名字故意与 {@link #masteryEnabled} 区分开：一个是原始值、一个是解析后的
     * 最终判据，同名会被 BeanUtils 转换串位（见 {@code KpTopic.masteryOverride}）。
     */
    private Boolean masteryOverride;

    /**
     * 是否参与掌握度（**已解析**的最终判据；解析顺序：单条显式值 &gt; 类型默认值 &gt; 安全兜底）。
     *
     * <p>索引投影里也有它，是因为目录树/概览要按"参与项"过滤 ——
     * 同一个判据必须在这一层也能读到，不能让前端自己再判一遍。
     */
    private Boolean masteryEnabled;

    /** 标签：JSON 字符串 */
    private String tags;

    /** 有没有正文（正文非空） */
    private Boolean hasContent;

    /** 创建时间。首页的「今日新增」要它（一个时间戳，19 个字符，636 条也就 12KB）。 */
    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    /**
     * 最后更新时间。**详情页「更新于」跟手刷新靠它**（2026-11-09 用户报「更新正文后
     * 时间不变，只有刷新页面才会变」）：保存类动作走 applyPatch，新时间戳只能从
     * 保存后强刷回来的索引里拿 —— 索引投影里没有 update_time，那个补丁就永远补不上。
     * 同样是一个 19 字符的时间戳，量级与 createTime 相同。
     */
    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
