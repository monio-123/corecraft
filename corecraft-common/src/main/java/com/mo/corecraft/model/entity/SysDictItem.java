package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysDictItem extends BaseEntity {

    private Long id;

    @TableField("dict_type_id")
    private Long dictTypeId;

    private String label;

    private String value;

    private Integer sort;

    @TableField("css_class")
    private String cssClass;

    private String remark;

    /**
     * 该类型默认是否参与掌握度：NULL=未配置 / TRUE=参与 / FALSE=不参与。
     *
     * <p>这是**通用字典表**上的一个可选列——只有 {@code kp_topic_type} 那一套字典项
     * 真的消费它（知识点类型的默认开关）；其它字典留 NULL（= 没配置这条规则）。
     *
     * <p>⚠️ **NULL 和 FALSE 是两回事**：NULL=未配置（走安全兜底），
     * FALSE=明确关闭。合并成一种状态就丢掉了"继承"这一层。
     *
     * <p>这里**不用** {@code updateStrategy = ALWAYS}：与 {@link KpTopic#masteryOverride}
     * 不同——单条覆盖那一列的 null 有含义（"继承类型"），必须能写进去；
     * 而这一列的 null 是"这条字典项没配这条规则"，更新时 null 应当**跳过**
     * （MP 默认的 NOT_NULL 策略正好合适）。
     */
    @TableField("mastery_enabled")
    private Boolean masteryEnabled;

    @TableField("is_enabled")
    private boolean enabled;
}
