package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.req.AiConfigCurrentReq;
import com.mo.corecraft.model.resp.AiConfigResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.AiConfigService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * AI 当前端点配置（接口列表在 {@link AiEndpointController}；原总开关已退役）。
 */
@RestController
@RequestMapping("/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<AiConfigResp> myConfig() {
        return ResultResp.data(aiConfigService.select(SecurityUtil.getUserId()));
    }

    /** 把某条 API 设为当前（账号级）。 */
    @RequestMapping(value = "/current", method = RequestMethod.POST)
    public ResultResp<AiConfigResp> useEndpoint(@Validated @RequestBody AiConfigCurrentReq req) {
        return ResultResp.data(aiConfigService.useEndpoint(SecurityUtil.getUserId(), req.getEndpointId()));
    }
}