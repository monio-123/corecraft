package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 配置回给前端的形态：**总开关 + 当前选中哪条**，就这两个。
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

    /**
     * 当前选中的 API id（账号级，跨设备一致）。null = 没选过。
     *
     * <p>前端拿它当**唯一真相**：进页面就按它对齐，localStorage 只当离线缓存。
     */
    private Long currentEndpointId;
}