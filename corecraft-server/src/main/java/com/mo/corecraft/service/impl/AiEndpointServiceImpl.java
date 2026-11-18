package com.mo.corecraft.service.impl;

import tools.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.model.req.AiEndpointSaveReq;
import com.mo.corecraft.model.req.AiEndpointTestReq;
import com.mo.corecraft.model.resp.AiEndpointResp;
import com.mo.corecraft.model.resp.AiEndpointTestResp;
import com.mo.corecraft.repository.AiEndpointRepository;
import com.mo.corecraft.service.AiConfigService;
import com.mo.corecraft.service.AiEndpointService;
import com.mo.corecraft.service.support.AiProtocol;
import com.mo.corecraft.utils.HttpExecutor;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiEndpointServiceImpl implements AiEndpointService {

    private final AiEndpointRepository aiEndpointRepository;
    private final AiConfigService aiConfigService;
    private final HttpExecutor httpExecutor;

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

        // 厂商 + 协议 + 自定义参数 → params 一列
        // **厂商**：null = 没传这个字段 = 沿用原值；"" = 明确清掉（界面上把厂商下拉清空就是它）
        // **协议**：null = 沿用原值；其它值由 protocolOf 归一（空串 / 未知 ⇒ openai）
        // **自定义参数**：null = 沿用原值；空串 = 清掉（textarea 清空了就该真的没有）
        // ⚠️ **params 列只解析一次**：下面三处取用共用这一个结果
        AiEndpoint.Params params = entity.params();
        String vendor = req.getVendor() == null ? params.vendor() : req.getVendor().trim();
        String protocol = req.getProtocol() == null
                ? params.protocol() : AiEndpoint.protocolOf(req.getProtocol());
        Map<String, Object> custom = req.getCustom() == null
                ? params.custom()
                : parseCustom(req.getCustom());
        entity.applyParams(vendor, protocol, custom);

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
        // ⚠️ **不再查「AI 总开关」**（2026-11-16 D6 开关退役）：那个开关同时管着
        // 交互类（问答）、一次点击多次调用（生成/出卷）、零点击自主任务（命名）——
        // 三件不相干的事被一个开关绑在一起，关掉它的瞬间三件一起变行为，
        // 而"能不能用 AI"的真相只有一个：**端点配全了没有**。
        // 急停 = 停用/删除端点（那条能力本来就有）。
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

    @Override
    public boolean currentReady(Long userId) {
        // **免抛异常的同一份判据**：自主任务（命名）用它决定"跑不跑"——
        // 没配好就不跑（笔记留空标题），而不是抛一句错给用户看。
        Long endpointId = aiConfigService.select(userId).getCurrentEndpointId();
        if (endpointId == null) {
            return false;
        }
        return aiEndpointRepository.findByIdAndUserId(endpointId, userId)
                .map(AiEndpoint::isReady)
                .orElse(false);
    }

    @Override
    public AiEndpointTestResp test(Long userId, AiEndpointTestReq req) {
        AiEndpoint endpoint = req.getEndpointId() == null ? null
                : aiEndpointRepository.findByIdAndUserId(req.getEndpointId(), userId).orElse(null);
        // **传了就用传的（弹窗里还没保存的草稿），没传就用端点自己存的**：
        // 列表行只带 endpointId，密钥不必再从前端跑一趟；协议留空退回端点存的，再退回 openai。
        String baseUrl = StringUtils.trimToNull(StringUtils.firstNonBlank(
                req.getBaseUrl(), endpoint == null ? null : endpoint.getBaseUrl()));
        String apiKey = StringUtils.trimToNull(StringUtils.firstNonBlank(
                req.getApiKey(), endpoint == null ? null : endpoint.getApiKey()));
        String model = StringUtils.trimToNull(StringUtils.firstNonBlank(
                req.getModel(), endpoint == null ? null : endpoint.getModel()));
        String protocol = AiEndpoint.protocolOf(StringUtils.firstNonBlank(
                req.getProtocol(), endpoint == null ? null : endpoint.params().protocol()));

        var resp = new AiEndpointTestResp();
        resp.setModel(model);
        if (baseUrl == null) return failed(resp, "没配接口地址");
        if (apiKey == null) return failed(resp, "请先配置 API Key");
        if (model == null) return failed(resp, "没填模型");

        long started = System.currentTimeMillis();
        String message;
        try {
            message = probe(baseUrl, apiKey, model, protocol);
        } catch (IOException e) {
            // 连不上 / 超时：原文直接给，别合成一句「网络错误」让人去查网线
            message = AiProtocol.brief(e.getMessage());
        }
        resp.setLatencyMs(System.currentTimeMillis() - started);
        resp.setOk(message == null);
        resp.setMessage(message == null ? "" : message);
        return resp;
    }

    // ==================== 内部 ====================

    /**
     * 发一条最小对话，返回 null = 通，否则是给界面看的错误原文。
     *
     * <p>⚠️ **不检查成功响应的形状**（那是 AiProtocol.errorText 的规矩）：各家
     * choices/content/自研包装都不一样，逐个认就会把通的判成不通的。
     * 只有状态码 >= 400 或 body 里带错误字段才算不通 —— 模型名写错时上游
     * 一般回 400/404，原文里直接写着哪个 model 不认识。
     */
    private String probe(String baseUrl, String apiKey, String model, String protocol) throws IOException {
        HttpExecutor.HttpResult result = httpExecutor.doPostForResult(
                AiProtocol.chatUrl(baseUrl, protocol),
                AiProtocol.headers(apiKey, protocol),
                AiProtocol.testBody(model, protocol));
        String error = AiProtocol.errorText(result.body());
        if (result.status() >= 400) {
            return error != null ? error
                    : (StringUtils.isBlank(result.body()) ? "HTTP " + result.status()
                    : AiProtocol.brief(result.body()));
        }
        // 2xx 但 body 是业务错误体（国内中转常见 HTTP 200 + {"code":401}）
        return error;
    }

    private static AiEndpointTestResp failed(AiEndpointTestResp resp, String message) {
        resp.setOk(false);
        resp.setMessage(message);
        return resp;
    }

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
        // 密钥明文回传（2026-11-11）：编辑弹窗直接回填，界面上默认掩码。
        // 列表只判"有没有"，所以 hasApiKey 一并给，省得前端拿明文自己算。
        resp.setApiKey(e.getApiKey());
        resp.setHasApiKey(StringUtils.isNotBlank(e.getApiKey()));
        // ⚠️ **params 只解析一次**（原来 getVendor() + getCustomParams() 各解析一遍）
        AiEndpoint.Params params = e.params();
        resp.setVendor(params.vendor());
        resp.setProtocol(params.protocol());
        Map<String, Object> custom = params.custom();
        resp.setCustom(custom.isEmpty() ? "" : JsonUtils.toJsonString(custom));
        // "配全了没有"的判据只有一份（AiEndpoint.isReady），这里不再自己算一遍
        resp.setReady(e.isReady());
        return resp;
    }
}