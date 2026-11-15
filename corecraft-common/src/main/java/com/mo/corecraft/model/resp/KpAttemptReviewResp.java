package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 回看：逐题题干 + 你的作答 + 判定 + 讲解（设计 §11.3 的 {@code /attempts/{id}/answers}）。
 */
@Getter
@Setter
public class KpAttemptReviewResp {

    private Long attemptId;

    private Long paperId;

    private String paperTitle;

    private String status;

    private BigDecimal score;

    private BigDecimal maxScore;

    private List<Item> items;

    @Getter
    @Setter
    public static class Item {

        private Long questionId;

        private Integer questionNo;

        private String type;

        private BigDecimal maxScore;

        private Map<String, Object> questionPayload;

        /** 你的作答 */
        private Map<String, Object> answerPayload;

        /** 判定；null = 未判定 */
        private String verdict;

        /** 得分率；null = 未判定 */
        private BigDecimal scoreRatio;

        private BigDecimal score;

        private String explanation;

        /** 评分点级判定快照 */
        private Map<String, Object> gradePayload;
    }
}
