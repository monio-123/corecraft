package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 开关回给前端的形态。**只有一个字段**。
 *
 * baseUrl / model / apiKey / ready 都搬到了 {@link AiEndpointResp}——那些是"某一个 API"的属性，
 * 不是一个用户只有一个的东西。
 *
 * ⚠️ 这里同样**没有 apiKey 字段**：密钥挂在某一个 API 上，不挂在总开关上。
 */
@Getter
@Setter
public class AiConfigResp {

    /** 是否启用。界面各处拿它控制 AI 入口的显示与否 */
    private Boolean enabled;
}