package com.mo.corecraft.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.model.entity.AiEndpoint;
import com.mo.corecraft.service.AiChatService.AiDelta;
import com.mo.corecraft.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 端点**协议在调用层的全部差异**，只收在这一处（字典 {@code ai_protocol} 的
 * openai / anthropic 两个值）。
 *
 * <p>为什么要有这个类：URL、请求头、SSE 解帧三件事都要按协议分叉，而调用方有两处
 * （{@code AiChatServiceImpl} 对话、{@code AiVendorServiceImpl} 拉模型清单）。
 * 各写一遍的话，"Anthropic 要带 anthropic-version" 这种规则必然只改一边。
 * ⇒ 协议值仍定义在 {@link AiEndpoint}（实体自带 params 解码），这里只管"怎么调"。
 *
 * <p>⚠️ **两套 SSE 的正文/思考/结束判据在这里对齐到同一个 {@link AiDelta}**：
 * 上层（controller 的 SSE 写出、前端的截断判定）完全不知道这次是哪种协议。
 * openai 的 {@code finish_reason=stop} 与 anthropic 的 {@code stop_reason=end_turn}
 * 都要归一成 {@code stop} —— 前端是 {@code finish !== 'stop'} 就报截断，
 * 不归一的话 Anthropic 每次正常答完都会被判成"没生成完"。
 */
public final class AiProtocol {

    /** Anthropic Messages API 的版本头，官方要求显式给 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String SUFFIX_CHAT = "/chat/completions";
    private static final String SUFFIX_OPENAI_MODELS = "/models";
    private static final String SUFFIX_MESSAGES = "/v1/messages";
    private static final String SUFFIX_ANTHROPIC_MODELS = "/v1/models";
    private static final String SUFFIX_V1 = "/v1";

    /** 空帧。**两套协议共用一份**，不给上层制造"这帧该不该发"的分支 */
    private static final AiDelta EMPTY_DELTA = AiDelta.of("", "", "");

    /** 上游错误原文最多给多少字（界面放不下要截断，但一个字都不改） */
    private static final int UPSTREAM_BRIEF = 200;

    private AiProtocol() { }

    // ==================== 地址 ====================

    /**
     * 对话地址。**允许两种填法**：只到版本段（{@code https://api.anthropic.com/v1}）
     * 或直接填到端点（{@code https://api.anthropic.com/v1/messages}）——
     * 各家文档给的都是完整 URL，硬拼会 404，而 404 在这里看不出是"地址填错"还是别的。
     */
    public static String chatUrl(String baseUrl, String protocol) {
        String base = AiEndpoint.baseOf(baseUrl);
        if (!AiEndpoint.isAnthropic(protocol)) {
            return base + SUFFIX_CHAT;
        }
        if (base.endsWith(SUFFIX_MESSAGES)) return base;
        if (base.endsWith(SUFFIX_V1)) return base + "/messages";
        return base + SUFFIX_MESSAGES;
    }

    /**
     * 拉模型清单的地址。Anthropic 官方是 {@code GET /v1/models}。
     *
     * <p>⚠️ **也允许直接填到 /models**：各家文档给完整 URL 很常见，硬拼会变成
     * {@code /v1/models/models}。chat 地址那边由 {@code AiEndpoint.baseOf} 去皮，
     * models 这边自己判一次（两套后缀不同，别塞进实体里互相猜）。
     */
    public static String modelsUrl(String baseUrl, String protocol) {
        String base = AiEndpoint.baseOf(baseUrl);
        if (!AiEndpoint.isAnthropic(protocol)) {
            return base.endsWith(SUFFIX_OPENAI_MODELS) ? base : base + SUFFIX_OPENAI_MODELS;
        }
        if (base.endsWith(SUFFIX_ANTHROPIC_MODELS)) return base;
        if (base.endsWith(SUFFIX_V1)) return base + SUFFIX_OPENAI_MODELS;
        return base + SUFFIX_ANTHROPIC_MODELS;
    }

    // ==================== 请求头 ====================

    /**
     * ⚠️ Anthropic **不认** {@code Authorization: Bearer}，用 {@code x-api-key}
     * 且必须带 {@code anthropic-version}；少一个头上游直接 400。
     */
    public static Map<String, String> headers(String apiKey, String protocol) {
        if (!AiEndpoint.isAnthropic(protocol)) {
            return Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json");
        }
        return Map.of(
                "x-api-key", apiKey,
                "anthropic-version", ANTHROPIC_VERSION,
                "Content-Type", "application/json");
    }

    // ==================== 请求体 ====================

    /**
     * 协议是否要求把 system 提到顶层。
     *
     * <p>Anthropic 的 {@code messages} 只收 user / assistant，system 是顶层独立字段；
     * OpenAI 兼容协议里 system 就是 messages 的第一条。
     */
    public static boolean systemAtTop(String protocol) {
        return AiEndpoint.isAnthropic(protocol);
    }

    /**
     * 把**已按 OpenAI 形态搭好**的请求体整形成这次真正要发的那份。
     *
     * <p>OpenAI 兼容协议下是 **no-op**：body 里的 messages 本来就是最终形状，
     * 一个键都不碰（存量端点默认走这条，改一个字就是全站回归）。
     *
     * <p>Anthropic 要做两件事：
     * <ol>
     *   <li>删 {@code thinking:{type:"disabled"}} —— 那是 OpenAI 兼容各家自己认的
     *       "关思考"写法，Anthropic 不认，带上直接 400；Anthropic 默认就不开思考。</li>
     *   <li>把 messages 里的 system 提出来放到顶层 {@code system} 字段，
     *       messages 只留 user / assistant。</li>
     * </ol>
     *
     * <p>⚠️ {@code max_tokens} 两套都叫这个名字，但 Anthropic 是**必填**，
     * 而 {@code AiChatServiceImpl.requestBody} 一直显式补了兜底值 ⇒ 这里不用再补。
     *
     * @param body 会被**原地修改**（调用方持有的是 LinkedHashMap）
     */
    public static void shapeBody(Map<String, Object> body, List<Map<String, String>> messages, String protocol) {
        if (!AiEndpoint.isAnthropic(protocol)) {
            return;
        }
        body.remove("thinking");
        String system = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> m.get("content"))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n\n"));
        if (!system.isEmpty()) {
            body.put("system", system);
        }
        body.put("messages", messages.stream()
                .filter(m -> !"system".equals(m.get("role")))
                .toList());
    }

    /**
     * 连通性测试的**最小请求体**。
     *
     * <p>⚠️ 只带所有模型都认的必需字段，**故意不带** {@code max_tokens} /
     * {@code temperature} / {@code thinking} 这些可选参数：推理模型（o1 系、各家
     * reasoner）常把它们判成 400「Unsupported parameter」，带上就会把「通的」
     * 测成「不通的」。代价只是上游会多吐几个字，换来的是对所有厂商 / 模型 /
     * 中转都成立 —— 这正是「适配所有不同的模型」要靠形状而不是靠白名单。
     *
     * <p>协议分叉只在这一处：Anthropic 的 {@code max_tokens} 是**必填**，最小给 1；
     * 而 {@code stream} 它根本不认，所以只有 OpenAI 兼容那一支才显式给 false
     * （不给的话少数中转会默认回 SSE，body 就不是 JSON 了）。
     */
    public static String testBody(String model, String protocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", "hi")));
        if (AiEndpoint.isAnthropic(protocol)) {
            body.put("max_tokens", 1);
        } else {
            body.put("stream", false);
        }
        return JsonUtils.toJsonString(body);
    }

    /**
     * 模型清单里"显示名"的字段名：Anthropic 是 {@code display_name}，
     * OpenAI 兼容是 {@code name}。**只认 id 的那套规则不变**（见 AiVendorServiceImpl）。
     */
    public static String modelName(JsonNode node, String protocol) {
        String key = AiEndpoint.isAnthropic(protocol) ? "display_name" : "name";
        return StringUtils.defaultString(JsonUtils.text(node, key));
    }

    /**
     * 上游"为什么停"归一到前端认识的值。
     *
     * <p>前端只判 {@code finish !== 'stop'} 就报截断，所以 {@code end_turn} 必须翻成
     * {@code stop}、{@code max_tokens} 翻成 {@code length}；其余原样带过去
     * （{@code stop_sequence} / {@code tool_use} 之类界面会显示"上游以 X 结束"）。
     */
    public static String stopReason(String raw) {
        if (StringUtils.isBlank(raw)) return "";
        if ("end_turn".equals(raw)) return "stop";
        if ("max_tokens".equals(raw)) return "length";
        return raw;
    }

    // ==================== 连通性测试：认错误，不认成功 ====================

    /**
     * 从上游响应体里认出「错误」。**认不出就当成功**（返回 null）。
     *
     * <p>⚠️ 故意不枚举成功响应的形状：{@code choices} / {@code content} /
     * 各家中转自己包的 {@code data} … 每家都不一样，逐个认必然把通的判成不通的。
     * 反过来，只要上游返回的是结构化响应且没带错误字段，就说明地址、密钥、
     * 模型名三关都过了 —— 测试要验的也就是这三关。
     *
     * <p>认三种错误体：{@code {"error":{...}}} / {@code {"error":"..."}}
     * （OpenAI 兼容 + Anthropic）、{@code {"code":401,"msg":"..."}}（国内中转常见）、
     * 以及压根不是 JSON 的纯文本 / HTML（网关 404 页）。
     * HTTP 状态码由调用方判（见 {@code AiEndpointServiceImpl.test}），这里只管 body。
     *
     * @return 错误原文（已截断，一个字不改）；null = 没认出错误
     */
    public static String errorText(String body) {
        if (StringUtils.isBlank(body)) return "没有返回内容";
        String text = body.trim();
        // 有些中转不管 stream 参数、直接回 SSE：能吐 data: 就说明模型真的在答
        if (text.startsWith("data:") || text.startsWith("event:")) return null;
        JsonNode root;
        try {
            root = JsonUtils.readTree(text);
        } catch (RuntimeException e) {
            return brief(text);
        }
        if (root == null) return brief(text);
        if (!root.isObject()) return null;
        JsonNode error = root.get("error");
        if (error != null && !error.isNull()) {
            String msg = error.isObject() ? JsonUtils.text(error, "message") : error.asText();
            return brief(StringUtils.defaultIfBlank(msg, text));
        }
        JsonNode code = root.get("code");
        if (code != null && code.isNumber() && code.asInt() != 0 && code.asInt() != 200) {
            return brief(StringUtils.firstNonBlank(
                    JsonUtils.text(root, "msg"), JsonUtils.text(root, "message"), text));
        }
        return null;
    }

    // ==================== SSE 解帧 ====================

    /**
     * SSE 的一行 → 一段增量。**只收 {@code data:} 行**：
     * Anthropic 的 {@code event: xxx} 行不携带内容，data 里的 {@code type} 已足够分派。
     */
    public static AiDelta parseDelta(String line, String protocol) {
        if (!AiEndpoint.isAnthropic(protocol)) return openaiDelta(line);
        return anthropicDelta(line);
    }

    /**
     * OpenAI 兼容帧：{@code {"choices":[{"delta":{"content":..,"reasoning_content":..},
     * "finish_reason":..}],"usage":..}}。
     *
     * <p><b>原样搬自 AiChatServiceImpl</b>，一个字段没改 —— 存量端点默认走这条路径，
     * 改一个字就是全站回归。
     */
    private static AiDelta openaiDelta(String line) {
        if (line == null || !line.startsWith("data:")) {
            return EMPTY_DELTA;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return EMPTY_DELTA;
        }
        JsonNode root = JsonUtils.readTree(payload);
        JsonNode choice = root.path("choices").path(0);
        JsonNode delta = choice.path("delta");
        JsonNode usage = root.path("usage");
        Integer tokenPrompt = usage.hasNonNull("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null;
        Integer tokenCompletion = usage.hasNonNull("completion_tokens") ? usage.get("completion_tokens").asInt() : null;
        return new AiDelta(
                "",
                StringUtils.defaultString(JsonUtils.text(delta, "reasoning_content")),
                StringUtils.defaultString(JsonUtils.text(delta, "content")),
                StringUtils.defaultString(JsonUtils.text(choice, "finish_reason")),
                tokenPrompt,
                tokenCompletion);
    }

    /**
     * Anthropic Messages 流式帧。事件形状（{@code data:} 里的 {@code type}）：
     * <ul>
     *   <li>{@code content_block_delta} → {@code delta.text_delta.text} 是正文、
     *       {@code delta.thinking_delta.thinking} 是思考过程（开了 extended thinking 才有）</li>
     *   <li>{@code message_start} → {@code message.usage.input_tokens}（输入 token）</li>
     *   <li>{@code message_delta} → {@code delta.stop_reason} + {@code usage.output_tokens}</li>
     *   <li>{@code error} → 上游原文直接抛（不包装），否则界面会对着戛然而止的回答发呆</li>
     *   <li>{@code ping} / {@code content_block_start} / {@code content_block_stop} /
     *       {@code message_stop} → 空帧</li>
     * </ul>
     */
    private static AiDelta anthropicDelta(String line) {
        if (line == null || !line.startsWith("data:")) {
            return EMPTY_DELTA;
        }
        String payload = line.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return EMPTY_DELTA;
        }
        JsonNode root = JsonUtils.readTree(payload);
        String type = StringUtils.defaultString(JsonUtils.text(root, "type"));
        switch (type) {
            case "error":
                throw new CoreCraftException(brief(StringUtils.defaultString(
                        JsonUtils.text(root.path("error"), "message"), payload)));
            case "content_block_delta": {
                JsonNode delta = root.path("delta");
                String kind = StringUtils.defaultString(JsonUtils.text(delta, "type"));
                String content = "text_delta".equals(kind)
                        ? StringUtils.defaultString(JsonUtils.text(delta, "text")) : "";
                String reasoning = "thinking_delta".equals(kind)
                        ? StringUtils.defaultString(JsonUtils.text(delta, "thinking")) : "";
                return new AiDelta("", reasoning, content, "", null, null);
            }
            case "message_start": {
                JsonNode usage = root.path("message").path("usage");
                Integer tokenPrompt = usage.hasNonNull("input_tokens")
                        ? usage.get("input_tokens").asInt() : null;
                return new AiDelta("", "", "", "", tokenPrompt, null);
            }
            case "message_delta": {
                String finish = stopReason(JsonUtils.text(root.path("delta"), "stop_reason"));
                JsonNode usage = root.path("usage");
                Integer tokenCompletion = usage.hasNonNull("output_tokens")
                        ? usage.get("output_tokens").asInt() : null;
                return new AiDelta("", "", "", finish, null, tokenCompletion);
            }
            default:
                return EMPTY_DELTA;
        }
    }

    /**
     * 上游原文：只截断，一个字都不改。
     * **全站只这一份**：SSE 错误帧、模型清单拉取失败、连通性测试失败都走它，
     * 别在各个 service 里再抄一份带自己前缀的版本。
     */
    public static String brief(String text) {
        String t = StringUtils.trimToEmpty(text);
        if (t.isEmpty()) return "没有返回内容";
        return t.length() <= UPSTREAM_BRIEF ? t : t.substring(0, UPSTREAM_BRIEF) + "…";
    }
}
