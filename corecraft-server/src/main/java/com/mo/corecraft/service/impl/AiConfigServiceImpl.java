package com.mo.corecraft.service.impl;

import com.mo.corecraft.model.entity.AiConfig;
import com.mo.corecraft.model.req.AiConfigSaveReq;
import com.mo.corecraft.model.resp.AiConfigResp;
import com.mo.corecraft.repository.AiConfigRepository;
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

    @Override
    public AiConfigResp select(Long userId) {
        var resp = new AiConfigResp();
        resp.setEnabled(aiConfigRepository.findByUserId(userId)
                .map(e -> Boolean.TRUE.equals(e.getEnabled()))
                .orElse(false));
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
        return resp;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return Boolean.TRUE.equals(select(userId).getEnabled());
    }
}