package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class KpTopicResp implements Taggable {

    private Long id;

    private String title;

    private String content;

    private Long parentTopicId;

    /** 所属目录ID（null = 未归类）；目录名由前端 store 从目录列表解析 */
    private Long categoryId;
    /** 知识点类型，指向 sys_dict_item.id（纯数字）。见 KpTopic.typeId */
    private Long typeId;

    /**
     * 是否参与掌握度的**单条覆盖**（库里的原始可空值，还没解析）。
     *
     * <p>⚠️ 名字故意与 {@link #masteryEnabled} 区分开：一个是原始值、一个是解析后的
     * 最终判据，同名会被 BeanUtils 转换串位（见 {@code KpTopic.masteryOverride}）。
     */
    private Boolean masteryOverride;

    /**
     * 是否参与掌握度（**已解析**的最终判据，不是库里那个可空的原值）。
     *
     * <p>解析顺序：单条显式值 &gt; 类型默认值 &gt; 安全兜底（false）。判据只在
     * {@code MasteryScope} 一处，前端不再自己判一遍。
     */
    private Boolean masteryEnabled;

    /**
     * 展示档位（后端算好的词，阈值只在 MasteryCalculator 一处）。
     *
     * <p>⚠️ **null = 没有有效评估**（从未被考过）⇒ 前端**完全不渲染**任何掌握度标记。
     * 「未评估」这个档位词只对详情页那套完整信息用，卡片上不画。
     */
    private String masteryLevel;

    private String tags;

    private List<Long> relatedTopicIds;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime updateTime;
}
