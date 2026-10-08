package com.mo.corecraft.service.impl;

import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.resp.AiEndpointResp;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.service.AiConfigService;
import com.mo.corecraft.service.AiEndpointService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiEndpointServiceImpl implements AiEndpointService {

    private static final String SETTINGS_HINT = "去「侧边栏头像 → AI 设置」";

    private final AiEndpointRepository aiEndpointRepository;
    private final AiConfigService aiConfigService;

    @Override
    public List<AiEndpointResp> list(Long userId) {
        return aiEndpointRepository.listByUserId(userId).stream().map(this::toResp).toList();
    }

    @Override
    public AiEndpointResp save(Long userId, AiEndpointSaveReq req) {
        AiEndpoint entity;
        if (req.getId() == null) {
            entity = new AiEndpoint();
            entity.setUserId(userId);
            entity.setName(req.getName().trim());
        } else {
            entity = aiEndpointRepository.findByIdAndUserId(req.getId(), userId)
                    .orElseThrow(() -> new CoreCraftException("这个 API 不存在或已删除"));
            // 三个字段统一语义：留空 = 沿用原值。用户只想改模型名时不该把地址也清掉
            entity.setName(StringUtils.defaultIfBlank(req.getName(), entity.getName()).trim());
        }

        if (StringUtils.isNotBlank(req.getBaseUrl())) {
            entity.setBaseUrl(req.getBaseUrl().trim());
        }
        if (StringUtils.isNotBlank(req.getModel())) {
            entity.setModel(req.getModel().trim());
        }
        if (StringUtils.isNotBlank(req.getApiKey())) {
            entity.setApiKey(req.getApiKey().trim());
        }

        if (req.getId() == null) {
            aiEndpointRepository.insert(entity);
        } else {
            aiEndpointRepository.update(entity);
        }
        return toResp(entity);
    }

    @Override
    public void delete(Long userId, Long id) {
        if (aiEndpointRepository.deleteByIdAndUserId(id, userId) == 0) {
            throw new CoreCraftException("这个 API 不存在或已删除");
        }
    }

    @Override
    public AiEndpoint requireReady(Long userId, Long endpointId) {
        if (!aiConfigService.isEnabled(userId)) {
            throw new CoreCraftException("AI 开关没打开，" + SETTINGS_HINT + "里启用");
        }
        if (endpointId == null) {
            throw new CoreCraftException("还没选 API，" + SETTINGS_HINT + "里添加一个并选中");
        }
        AiEndpoint endpoint = aiEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new CoreCraftException("选中的 API 不存在，去 " + SETTINGS_HINT + "里重新选"));
        if (StringUtils.isAnyBlank(endpoint.getBaseUrl(), endpoint.getModel(), endpoint.getApiKey())) {
            throw new CoreCraftException("「" + endpoint.getName() + "」还没配全，" + SETTINGS_HINT + "里补齐 API 地址、模型和 API Key");
        }
        return endpoint;
    }

    // ==================== 内部 ====================

    private AiEndpointResp toResp(AiEndpoint e) {
        var resp = new AiEndpointResp();
        resp.setId(e.getId());
        resp.setName(e.getName());
        resp.setBaseUrl(e.getBaseUrl());
        resp.setModel(e.getModel());
        // 明文回传（2026-10-07 用户拍板）：设置页要把它当普通字段显示和编辑。
        // 原来的 hasApiKey 已删——键都给了，"有没有"这个布尔就是废话。
        resp.setApiKey(e.getApiKey());
        resp.setReady(StringUtils.isNotBlank(e.getApiKey())
                && StringUtils.isNotBlank(e.getBaseUrl())
                && StringUtils.isNotBlank(e.getModel()));
        return resp;
    }
}