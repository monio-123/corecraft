package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单条知识点的掌握度档位（2026-11-14，目录树知识点行的新数据源）。
 *
 * <p>树视图不拉列表（§2.10 性能口径）⇒ 知识点行没有档位数据源；
 * 本接口一次返回**当前用户全部有评估的知识点**的档位与掌握度值——
 * 投影行数 = 考过的知识点数（量小），树视图按 topicId 现查。
 *
 * <p>{@code level = null} 的行（已学未评估 / 无证据）**不返回**——
 * 树行对没有掌握度的知识点保持原样（用户口径）。
 */
@Getter
@Setter
public class KpTopicLevelResp {

    private Long topicId;

    /** 档位词（后端一份：待巩固 / 基本掌握 / 熟练 / 稳定掌握 / 待复测） */
    private String level;

    /** 当前掌握度 M（ability × 衰减后 R，0~100）——树行进度条的填充值 */
    private BigDecimal mastery;
}
