package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 判分结果回调（设计 §11.2）：{@code resultId} 幂等、{@code gradeRevision} 单调递增。
 *
 * <p>相同 resultId 重试只幂等返回（不重复产证据）；新 revision 才触发替代证据与当前 epoch 重放。
 * 部分题未判定可以是 partial：未判定的题不计入也不落 0。
 */
@Getter
@Setter
public class KpGradeResultReq {

    /** 判分回调幂等键（同一次判分产物的稳定 id） */
    @NotBlank(message = "resultId不能为空!")
    private String resultId;

    /** 判分版本；同 resultId 重发必须同 revision */
    @NotNull(message = "gradeRevision不能为空!")
    private Integer gradeRevision;

    @NotNull(message = "answers不能为空!")
    private List<Item> answers;

    @Getter
    @Setter
    public static class Item {

        @NotNull(message = "questionId不能为空!")
        private Long questionId;

        /** 判定结果；null = 未判定（不得落 0） */
        private String verdict;

        /** 得分率 0~1；null = 未判定 */
        private BigDecimal scoreRatio;

        private BigDecimal score;

        private String explanation;

        /** JSON 对象：评分点级判定快照（rubricKey → scoreRatio…） */
        private Map<String, Object> gradePayload;
    }
}
