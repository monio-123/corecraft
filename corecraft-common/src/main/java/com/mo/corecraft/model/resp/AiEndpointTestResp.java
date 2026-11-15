package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 连通性测试的结果。
 *
 * <p>⚠️ **「不通」是结果，不是系统故障**：上游 401 / 404 / 超时 / 返回业务错误体，
 * 一律走 {@code ok=false + message} 正常返回，不抛业务异常。否则全站统一的
 * 错误提示出口会先弹一条红字，界面里再显示一遍同样的原文 —— 一次点击两条提示。
 *
 * <p>{@code message} 只放两样东西：成功时为空；失败时是**上游错误原文**（已截断），
 * 一个字都不改写（brove-shared §8）。
 */
@Getter
@Setter
public class AiEndpointTestResp {

    /** 通不通 */
    private boolean ok;

    /** 这一趟花了多少毫秒（成功时给界面显示，失败时也一起给，便于判断是不是超时） */
    private long latencyMs;

    /** 实际拿去测的模型名（回显，省得界面猜后端到底用了哪份值） */
    private String model;

    /** 失败原因（上游原文 / 缺哪一项）；成功为空 */
    private String message;
}
