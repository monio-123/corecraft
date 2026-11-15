package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 标记 / 取消「已学」（设计 §3.1）。
 *
 * <p>刻意不走 {@code KpTopicUpdateReq}：那是正文编辑通道（applyPatch 白名单只认 6 个字段），
 * 学习标记是「用户与知识点的关系」，不是知识点的内容（实施计划 §1.1）。
 */
@Getter
@Setter
public class KpLearningMarkReq {

    /** true = 标记已学；false = 取消已学。空正文不能标记已学（服务端校验） */
    @NotNull(message = "learned不能为空!")
    private Boolean learned;

    /** 可选备注（进 pendingReason 或审计），不要求 */
    private String reason;
}
