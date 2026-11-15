package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.req.AiConfigCurrentReq;
import com.mo.corecraft.model.req.AiConfigSaveReq;
import com.mo.corecraft.model.resp.AiConfigResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.AiConfigService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * AI 总开关（接口列表在 {@link AiEndpointController}）。
 *
 * 路径放在 /ai/config 下而不是 /dict/** —— 它是按用户隔离的业务配置，
 * 和字典那种全局参考数据不是一类东西，路径上就要分得开。
 *
 * **userId 一律从登录态取，不接受前端传参**：传了就能改别人的设置。
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

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<AiConfigResp> save(@Validated @RequestBody AiConfigSaveReq req) {
        return ResultResp.data(aiConfigService.save(SecurityUtil.getUserId(), req));
    }

    /**
     * 把某条 API 设为当前（账号级）。
     *
     * <p>**为什么不塞进 PUT**：设置页里"点整行选中"是独立动作，没有保存按钮，
     * 而 PUT 是存总开关的。两个动作两个口子，免得只想换一条却顺带把开关写一遍。
     */
    @RequestMapping(value = "/current", method = RequestMethod.POST)
    public ResultResp<AiConfigResp> useEndpoint(@Validated @RequestBody AiConfigCurrentReq req) {
        return ResultResp.data(aiConfigService.useEndpoint(SecurityUtil.getUserId(), req.getEndpointId()));
    }

}