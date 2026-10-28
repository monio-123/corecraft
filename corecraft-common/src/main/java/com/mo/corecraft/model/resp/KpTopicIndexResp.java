package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** 标签：JSON 字符串 */
    private String tags;

    /** 有没有正文（正文非空） */
    private Boolean hasContent;

    /** 创建时间。首页的「今日新增」要它（一个时间戳，19 个字符，636 条也就 12KB）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}
