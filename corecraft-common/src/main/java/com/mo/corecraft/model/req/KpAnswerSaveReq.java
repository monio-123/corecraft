package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 保存作答（自动保存 / 交卷共用，PUT 全量覆盖本次 attempt 的作答）。
 * 判定不在这里——判定走 {@code grade-result}，缺失判定保持未判定不落 0（设计 §12.4）。
 */
@Getter
@Setter
public class KpAnswerSaveReq {

    @NotNull(message = "answers不能为空!")
    private List<Item> answers;

    @Getter
    @Setter
    public static class Item {

        @NotNull(message = "questionId不能为空!")
        private Long questionId;

        /** JSON 对象：考生作答原文（选择项、文字答案） */
        private Map<String, Object> answerPayload;
    }
}
