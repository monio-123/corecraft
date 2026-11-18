package com.mo.corecraft.service;

import com.mo.corecraft.model.resp.AiConfigResp;

/**
 * AI 配置（记录当前选中的 API 端点；{@code enabled} 总开关已于 2026-11-16 D6 退役）。
 */
public interface AiConfigService {

    AiConfigResp select(Long userId);

    /**
     * **把某条 API 设为当前**（账号级，跨设备一致）。
     *
     * @throws com.mo.corecraft.exception.CoreCraftException 这条 API 不存在 / 不是这个人的
     */
    AiConfigResp useEndpoint(Long userId, Long endpointId);
}