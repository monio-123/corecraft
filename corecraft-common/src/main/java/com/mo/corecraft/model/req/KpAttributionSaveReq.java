package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 保存题目的知识归属（出题后独立的「解释证据」一步，设计 §4.2）。
 *
 * <p>归属是**弱归属**：0..N 个知识点、区分主考/辅考、允许 {@code unattributed}——
 * 绝不硬凑。归属版本由服务端单调递增，payload 按 revision 留痕。
 */
@Getter
@Setter
public class KpAttributionSaveReq {

    /** pending / attributed / partial / unattributed / failed */
    @NotNull(message = "status不能为空!")
    private String status;

    /** JSON 对象：归属方式与原因等说明（可空） */
    private Map<String, Object> note;

    /** 归属条目；unattributed 时为空 */
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {

        /** 评分点 key；整题归属固定 __question__ */
        private String rubricKey;

        @NotNull(message = "topicId不能为空!")
        private Long topicId;

        /** 主考 1 / 辅考 <1；同一评分单位之和 ≤1 */
        @NotNull(message = "topicWeight不能为空!")
        private BigDecimal topicWeight;
    }
}
