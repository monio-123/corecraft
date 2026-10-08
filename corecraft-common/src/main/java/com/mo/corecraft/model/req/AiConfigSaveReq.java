package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存 AI 开关。只有一个字段——接口在 {@link com.mo.corecraft.model.req.AiEndpointSaveReq} 里。
 */
@Getter
@Setter
public class AiConfigSaveReq {

    @NotNull(message = "启用状态不能为空!")
    private Boolean enabled;
}