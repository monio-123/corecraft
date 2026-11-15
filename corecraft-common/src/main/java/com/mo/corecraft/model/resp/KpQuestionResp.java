package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 一道题（答题/回看共用）。{@code questionPayload} 是当次 AI 产物快照原样回传。
 */
@Getter
@Setter
public class KpQuestionResp {

    private Long id;

    private Long paperId;

    private Integer questionNo;

    private String type;

    private BigDecimal maxScore;

    /** 题干、选项、参考答案、评分点、场景与认知层次 */
    private Map<String, Object> questionPayload;

    /** pending / attributed / partial / unattributed / failed */
    private String attributionStatus;
}
