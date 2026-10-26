package com.mo.corecraft.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.resp.AiEndpointResp;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.service.AiConfigService;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiEndpointServiceImpl implements AiEndpointService {

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

        // 厂商 + 自定义参数 → params 一列
        // **厂商**：null = 没传这个字段 = 沿用原值；"" = 明确清掉（界面上把厂商下拉清空就是它）
        // **自定义参数**：null = 沿用原值；空串 = 清掉（textarea 清空了就该真的没有）
        // ⚠️ **params 列只解析一次**：下面两次取用共用这一个结果
        AiEndpoint.Params params = entity.params();
        String vendor = req.getVendor() == null ? params.vendor() : req.getVendor().trim();
        Map<String, Object> custom = req.getCustom() == null
                ? params.custom()
                : parseCustom(req.getCustom());
        entity.applyParams(vendor, custom);

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
        // ⚠️ **错误文案只说事实，不写界面路径**（brove-shared §8）：后端硬编"侧边栏头像 → AI 设置"
        // 那种路径，前端菜单一改就指错地方；入口在哪用户自己点得到。
        if (!aiConfigService.isEnabled(userId)) {
            throw new CoreCraftException("AI 开关没打开");
        }
        if (endpointId == null) {
            throw new CoreCraftException("还没选好 API");
        }
        AiEndpoint endpoint = aiEndpointRepository.findByIdAndUserId(endpointId, userId)
                .orElseThrow(() -> new CoreCraftException("选中的 API 不存在"));
        // "配全了没有"的判据只有一份（AiEndpoint.isReady）——响应里的 ready 字段也用它
        if (!endpoint.isReady()) {
            throw new CoreCraftException("「" + endpoint.getName() + "」还没配全");
        }
        return endpoint;
    }

    // ==================== 内部 ====================

    /**
     * 自定义参数（JSON 文本）→ map。
     *
     * <p>**非法 JSON 直接报错，不静默丢掉**：静默丢掉的话用户填了半天没生效，
     * 而且界面上看不出任何异常。
     *
     * <p>**只解析一次**：先用 readTree 校验"是不是合法 JSON / 是不是对象"，
     * 再把**已经拿到的那个节点**转成 map —— 原来又 parseObjectMap 解析了第二遍。
     */
    private Map<String, Object> parseCustom(String text) {
        String t = text.trim();
        if (t.isEmpty()) {
            return Map.of();
        }
        JsonNode node;
        try {
            node = JsonUtils.readTree(t);
        } catch (RuntimeException e) {
            throw new CoreCraftException("自定义参数不是合法 JSON");
        }
        if (node == null || !node.isObject()) {
            throw new CoreCraftException("自定义参数必须是 JSON 对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> custom = (Map<String, Object>) JsonUtils.toJava(node);
        return custom;
    }

    private AiEndpointResp toResp(AiEndpoint e) {
        var resp = new AiEndpointResp();
        resp.setId(e.getId());
        resp.setName(e.getName());
        resp.setBaseUrl(e.getBaseUrl());
        resp.setModel(e.getModel());
        // 密钥不再回传（2026-10-26 改）：只说"配了没有"。
        // 改的理由不是"密钥该藏着"这种洁癖，是**刷新模型这件事本来就得由后端做**——
        // 厂商接口要用密钥，前端拿不到密钥就调不了 ⇒ 密钥没有必要下发到浏览器。
        resp.setHasApiKey(StringUtils.isNotBlank(e.getApiKey()));
        // ⚠️ **params 只解析一次**（原来 getVendor() + getCustomParams() 各解析一遍）
        AiEndpoint.Params params = e.params();
        resp.setVendor(params.vendor());
        Map<String, Object> custom = params.custom();
        resp.setCustom(custom.isEmpty() ? "" : JsonUtils.toJsonString(custom));
        // "配全了没有"的判据只有一份（AiEndpoint.isReady），这里不再自己算一遍
        resp.setReady(e.isReady());
        return resp;
    }
}