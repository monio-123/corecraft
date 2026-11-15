package com.mo.corecraft.service.impl;

import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiConfig;
import com.mo.corecraft.model.req.AiConfigSaveReq;
import com.mo.corecraft.model.resp.AiConfigResp;
import com.mo.corecraft.repository.AiConfigRepository;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 总开关。**接口不在这里**（在 AiEndpoint，一人多行），这里只有一个 enabled。
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
        resp.setEnabled(found.map(e -> Boolean.TRUE.equals(e.getEnabled())).orElse(false));
        resp.setCurrentEndpointId(found.map(AiConfig::getCurrentEndpointId).orElse(null));
        return resp;
    }

    @Override
    public AiConfigResp save(Long userId, AiConfigSaveReq req) {
        AiConfig entity = aiConfigRepository.findByUserId(userId).orElseGet(() -> {
            var fresh = new AiConfig();
            fresh.setUserId(userId);
            return fresh;
        });
        entity.setEnabled(Boolean.TRUE.equals(req.getEnabled()));

        if (entity.getId() == null) {
            aiConfigRepository.insert(entity);
        } else {
            aiConfigRepository.update(entity);
        }
        var resp = new AiConfigResp();
        resp.setEnabled(entity.getEnabled());
        resp.setCurrentEndpointId(entity.getCurrentEndpointId());
        return resp;
    }

    @Override
    public AiConfigResp useEndpoint(Long userId, Long endpointId) {
        // **归属校验在这里做**：只判"存不存在"会让 A 把 B 的端点设成自己的当前，
        // 而 AiChat 那条路拿 endpointId 是**带 userId 查**的 ⇒ 到时候才报"选中的 API 不存在"，
        // 症状是"设置页明明选中了，一问就报错"。
        aiEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new CoreCraftException("选中的 API 不存在"));

        AiConfig entity = aiConfigRepository.findByUserId(userId).orElseGet(() -> {
            var fresh = new AiConfig();
            fresh.setUserId(userId);
            fresh.setEnabled(false);
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

    @Override
    public boolean isEnabled(Long userId) {
        return Boolean.TRUE.equals(select(userId).getEnabled());
    }
}