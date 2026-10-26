package com.mo.corecraft.controller.system;

import com.mo.corecraft.model.req.AiModelsRefreshReq;
import com.mo.corecraft.model.resp.AiVendorResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.service.AiVendorService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 厂商（和它们的模型清单）。
 *
 * 和 {@code AiEndpointController} 分开：那边是"我配了哪些 API"，这边是"有哪些厂商可选"。
 *
 * **userId 一律从登录态取**：refresh 要用"这条端点自己存的密钥"，归属校验在 service 里
 * 按 userId 查，猜别人的 id 也取不到他的密钥。
 *
 * ⚠️ **写字典这件事没有权限码**：厂商和模型是全局配置（字典表没有 user_id），
 * 刷新等于改全局清单 ⇒ 任何登录用户都能改。这和现状一致（字典写接口本来就没鉴权），
 * 不是新增的风险类别。真要收紧，得先给字典加"只有管理员能写"的权限码 —— 那是另一件事。
 */
@RestController
@RequestMapping("/ai/vendor")
@RequiredArgsConstructor
public class AiVendorController {

    private final AiVendorService aiVendorService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<List<AiVendorResp>> list() {
        return ResultResp.data(aiVendorService.list());
    }

    @RequestMapping(value = "/models/refresh", method = RequestMethod.POST)
    public ResultResp<AiVendorResp> refresh(@Validated @RequestBody AiModelsRefreshReq req) {
        return ResultResp.data(aiVendorService.refresh(SecurityUtil.getUserId(), req));
    }
}
