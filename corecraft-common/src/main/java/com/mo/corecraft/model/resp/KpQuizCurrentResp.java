package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 随堂测验的锚（TODO-exam-lifecycle §三/§七）：这条知识「当前的测验」是哪张卷、
 * 它最新一次作答停在哪。点开测验先查它——在答的续答、考完的回看，
 * **不再默认重新生成**。
 *
 * <p>「没有」是正常态不是错误：paper 为 null = 这条还没考过，返回空对象而不是 404。
 */
@Getter
@Setter
public class KpQuizCurrentResp {

    /** 卷摘要（id / 标题 / 题数 / 出卷时间——出卷时间给「正文改过」的过期判据用） */
    private KpPaperSummaryResp paper;

    /** 该卷最新一次作答（含重做）；生成了从没答过就是 null */
    private Attempt attempt;

    @Getter
    @Setter
    public static class Attempt {
        private Long id;
        /** answering / grading / partial / completed / failed */
        private String status;

        public static Attempt of(Long id, String status) {
            Attempt a = new Attempt();
            a.setId(id);
            a.setStatus(status);
            return a;
        }
    }
}
