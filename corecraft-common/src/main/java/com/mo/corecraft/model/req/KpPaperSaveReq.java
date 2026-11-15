package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 保存整张试卷（出卷完成后先持久化再进答题，设计 §11.2）。
 *
 * <p>出卷由前端出卷链路（exam.js）跑 AI 完成，这里只负责**落库成档案**：
 * 题目快照、归属快照、范围快照一律照传（当次 AI 产物快照，不回查现状）。
 */
@Getter
@Setter
public class KpPaperSaveReq {

    /** quiz 随堂测验 / exam 目录考试 / review 复习卷 */
    @NotBlank(message = "kind不能为空!")
    private String kind;

    /** 目录考试有值；复习卷跨目录可空 */
    private Long categoryId;

    private String title;

    /** JSON 对象：当时范围（知识点 id、标题、内容 hash/epoch、目录路径） */
    private Map<String, Object> scopeSnapshot;

    /** JSON 对象：题型、道数、分值、难度、附加要求、模型等 */
    private Map<String, Object> settingsSnapshot;

    @NotEmpty(message = "题目不能为空!")
    private List<KpQuestionSaveReq> questions;

    @Getter
    @Setter
    public static class KpQuestionSaveReq {

        private Integer questionNo;

        /** 题型 code（落库前服务端会归一化比较用，但原样存） */
        private String type;

        private BigDecimal maxScore;

        /** JSON 对象：题干、选项、参考答案、评分点、场景与认知层次 */
        private Map<String, Object> questionPayload;

        /** JSON 对象：题目级/评分点级知识归属（首轮可不带，后续归属接口补） */
        private Map<String, Object> attributionPayload;

        /** pending / attributed / partial / unattributed / failed */
        private String attributionStatus;
    }
}
