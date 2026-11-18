package com.mo.corecraft.service.impl;

import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiConfig;
import com.mo.corecraft.model.resp.AiConfigResp;
import com.mo.corecraft.repository.AiConfigRepository;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 配置：记录当前选中的端点（{@code enabled} 总开关已于 2026-11-16 D6 退役，判据收敛到 {@code AiEndpointService.requireReady}）。
 */
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AiConfigRepository aiConfigRepository;
    private final AiEndpointRepository aiEndpointRepository;

    @Override
    public AiConfigResp select(Long userId) {
        var found = aiConfigRepository.findByUserId(userId);
        var resp = new AiConfigResp();
        resp.setCurrentEndpointId(found.map(AiConfig::getCurrentEndpointId).orElse(null));
        return resp;
    }

    @Override
    public AiConfigResp useEndpoint(Long userId, Long endpointId) {
        aiEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new CoreCraftException("选中的 API 不存在"));

        AiConfig entity = aiConfigRepository.findByUserId(userId).orElseGet(() -> {
            var fresh = new AiConfig();
            fresh.setUserId(userId);
            return fresh;
        });
        entity.setCurrentEndpointId(endpointId);
        if (entity.getId() == null) {
            aiConfigRepository.insert(entity);
        } else {
            aiConfigRepository.update(entity);
        }
        return select(userId);
    }
}