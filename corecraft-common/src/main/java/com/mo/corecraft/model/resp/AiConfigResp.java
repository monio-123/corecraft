package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * AI 配置回给前端的形态：当前选中的 API 端点 id。
 */
@Getter
@Setter
public class AiConfigResp {

    /** 当前选中的 API id（账号级，跨设备一致）；null = 没选过。 */
    private Long currentEndpointId;
}