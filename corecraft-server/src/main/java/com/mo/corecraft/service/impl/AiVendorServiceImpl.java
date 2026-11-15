package com.mo.corecraft.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.entity.SysDictItem;
import com.mo.corecraft.model.entity.SysDictType;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.model.req.AiModelsRefreshReq;
import com.mo.corecraft.model.resp.AiVendorResp;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.repository.SysDictItemRepository;
import com.mo.corecraft.repository.SysDictTypeRepository;
import com.mo.corecraft.service.AiVendorService;
import com.mo.corecraft.service.support.AiProtocol;
import com.mo.corecraft.utils.HttpExecutor;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 厂商和模型清单 = **字典**。
 *
 * <p>厂商 = 一个字典类型（code 前缀 {@code ai_vendor_}，类型 remark = 该厂商的 OpenAI 兼容地址），
 * 模型 = 那个类型下的字典项（label = 显示名，value = 模型 id，停用 = is_enabled=0）。
 * 字典表本来就是「类型 + 平铺的项」两层、项下没有子项 ⇒ 这套层级只能这样落。
 *
 * <p>**刷新按端点的协议分叉**（{@code AiProtocol.modelsUrl / headers}）：
 * OpenAI 兼容是 GET {base}/models + Bearer，Anthropic 是 GET /v1/models + x-api-key。
 * **但不按厂商分叉**：同一协议下换厂商只改地址，不引 provider 字段
 * （{@code AiChatServiceImpl} 那条规矩）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiVendorServiceImpl implements AiVendorService {

    /** 厂商字典类型的 code 前缀。厂商 code ↔ 类型 code 的对应靠它 */
    private static final String VENDOR_PREFIX = "ai_vendor_";

    private final SysDictTypeRepository dictTypeRepository;
    private final SysDictItemRepository dictItemRepository;
    private final AiEndpointRepository aiEndpointRepository;
    private final HttpExecutor httpExecutor;

    @Override
    public List<AiVendorResp> list() {
        var typeQuery = new SysDictTypeQuery();
        typeQuery.setEnabled(true);
        // code 是 eq 匹配，列不出"前缀是 ai_vendor_ 的" ⇒ 全部取回来自己筛
        List<SysDictType> types = dictTypeRepository.list(typeQuery, t -> t).stream()
                .filter(t -> StringUtils.startsWith(t.getCode(), VENDOR_PREFIX))
                .toList();
        if (types.isEmpty()) {
            return List.of();
        }
        // 一次把模型项全捞出来按类型分组，别每个厂商查一次
        var itemQuery = new SysDictItemQuery();
        itemQuery.setEnabled(true);
        Map<Long, List<SysDictItem>> itemsByType = dictItemRepository.list(itemQuery, i -> i).stream()
                .collect(Collectors.groupingBy(SysDictItem::getDictTypeId));
        return types.stream()
                .map(t -> toResp(t, itemsByType.getOrDefault(t.getId(), List.of()).stream()
                        .map(AiVendorServiceImpl::toModel).toList()))
                .toList();
    }

    @Override
    public AiVendorResp refresh(Long userId, AiModelsRefreshReq req) {
        SysDictType type = findVendor(req.getVendor());
        AiEndpoint endpoint = req.getEndpointId() == null ? null
                : aiEndpointRepository.findByIdAndUserId(req.getEndpointId(), userId).orElse(null);

        // 地址：前端传的 → 厂商字典里的 → 这条端点自己存的
        // （firstNonBlank 是 commons-lang3 自带的；trimToNull 保住"去掉首尾空格"的语义）
        String baseUrl = StringUtils.trimToNull(StringUtils.firstNonBlank(req.getBaseUrl(), type.getRemark(),
                endpoint == null ? null : endpoint.getBaseUrl()));
        if (StringUtils.isBlank(baseUrl)) {
            throw new CoreCraftException("没配接口地址");
        }
        // 密钥：前端传的 → 这条端点自己存的
        // （2026-11-11 起编辑框会明文回显，正常会原样带回来；留空仍走后端这条兜底）
        String apiKey = StringUtils.trimToNull(
                StringUtils.firstNonBlank(req.getApiKey(), endpoint == null ? null : endpoint.getApiKey()));
        if (StringUtils.isBlank(apiKey)) {
            throw new CoreCraftException("请先配置 API Key");
        }

        // 协议：前端传的 → 端点自己存的 → openai（存量端点默认）
        // ⚠️ 拉模型和聊天必须用**同一个协议**：URL（/models vs /v1/models）和
        // 请求头（Bearer vs x-api-key）都跟着它变，猜错就是 401/404 且看不出根因。
        String protocol = AiEndpoint.protocolOf(StringUtils.firstNonBlank(
                req.getProtocol(), endpoint == null ? null : endpoint.params().protocol()));

        List<AiVendorResp.Model> models = parseModels(
                fetchModels(AiProtocol.modelsUrl(baseUrl, protocol), apiKey, protocol), protocol);
        syncModels(type.getId(), models);
        log.info("拉取厂商 {} 的模型清单：{} 个", req.getVendor(), models.size());
        return toResp(type, models);
    }

    // ==================== 内部 ====================

    private SysDictType findVendor(String vendor) {
        var query = new SysDictTypeQuery();
        query.setCode(VENDOR_PREFIX + vendor.trim());
        return dictTypeRepository.find(query, t -> t)
                .orElseThrow(() -> new CoreCraftException("没有这个厂商：" + vendor));
    }

    private String fetchModels(String url, String apiKey, String protocol) {
        try {
            // ⚠️ HttpExecutor **不检查 HTTP 状态码** ⇒ 401 的错误体会被原样当 body 拿回来，
            // 所以下面解析失败时直接把这段原文抛出去，否则用户分不清"key 不对"和"厂商没这接口"
            return httpExecutor.doGet(url, AiProtocol.headers(apiKey, protocol));
        } catch (IOException e) {
            throw new CoreCraftException(AiProtocol.brief(e.getMessage()));
        }
    }

    /**
     * 解析模型清单：{@code {"data":[{"id":"...","name":"..."}]}}。
     *
     * <p>**只认 id** —— 那是真正发给 API 的那个串；显示名没有就用 id。
     * 显示名的字段名按协议取（Anthropic 叫 display_name），见 {@code AiProtocol.modelName}。
     */
    private List<AiVendorResp.Model> parseModels(String body, String protocol) {
        JsonNode root;
        try {
            root = JsonUtils.readTree(body);
        } catch (RuntimeException e) {
            throw new CoreCraftException(AiProtocol.brief(body));
        }
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray()) {
            throw new CoreCraftException(AiProtocol.brief(body));
        }
        // 按 id 去重：厂商偶尔会把同一个模型列两遍
        Map<String, AiVendorResp.Model> byId = new LinkedHashMap<>();
        for (JsonNode node : data) {
            String id = JsonUtils.text(node, "id");
            if (StringUtils.isBlank(id)) {
                continue;
            }
            byId.putIfAbsent(id, toModel(id, AiProtocol.modelName(node, protocol)));
        }
        if (byId.isEmpty()) {
            throw new CoreCraftException(AiProtocol.brief(body));
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 把拉回来的清单写回字典。
     *
     * <p>**厂商这轮没返回的 = 停用，不删**：
     * 删了就没法手工找回来（厂商下次不列它，它就永远回不来），停用还能在字典页里改回来。
     */
    private void syncModels(Long dictTypeId, List<AiVendorResp.Model> models) {
        var query = new SysDictItemQuery();
        query.setDictTypeId(dictTypeId);
        List<SysDictItem> existing = dictItemRepository.list(query, i -> i);
        Map<String, SysDictItem> byValue = existing.stream()
                .collect(Collectors.toMap(SysDictItem::getValue, i -> i, (a, b) -> a));

        Set<String> seen = new LinkedHashSet<>();
        int sort = 0;
        for (AiVendorResp.Model model : models) {
            sort++;
            SysDictItem item = byValue.get(model.getId());
            boolean isNew = item == null;
            if (isNew) {
                item = new SysDictItem();
                item.setDictTypeId(dictTypeId);
                item.setValue(model.getId());
                item.setRemark("厂商接口拉取");
            }
            item.setLabel(model.getLabel());
            item.setSort(sort);
            item.setEnabled(true);
            if (isNew) {
                dictItemRepository.insert(item);
            } else {
                dictItemRepository.update(item);
            }
            seen.add(model.getId());
        }
        for (SysDictItem item : existing) {
            if (!seen.contains(item.getValue()) && item.isEnabled()) {
                item.setEnabled(false);
                dictItemRepository.update(item);
            }
        }
    }

    private AiVendorResp toResp(SysDictType type, List<AiVendorResp.Model> models) {
        var resp = new AiVendorResp();
        resp.setCode(type.getCode().substring(VENDOR_PREFIX.length()));
        resp.setName(type.getName());
        resp.setBaseUrl(type.getRemark());
        resp.setModels(models);
        return resp;
    }

    /**
     * 模型 id + 显示名 → Model。**显示名缺省就用 id** —— 这条规则只存这一份
     * （原来「厂商接口返回的名字」和「字典项里的名字」各写了一遍同样的缺省逻辑）。
     */
    private static AiVendorResp.Model toModel(String id, String label) {
        var model = new AiVendorResp.Model();
        model.setId(id);
        model.setLabel(StringUtils.isNotBlank(label) ? label : id);
        return model;
    }

    private static AiVendorResp.Model toModel(SysDictItem item) {
        return toModel(item.getValue(), item.getLabel());
    }
}
