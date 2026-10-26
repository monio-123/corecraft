package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.utils.JsonUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 接口：一人多行，提问时选一行用。
 *
 * **为什么没有 provider 字段**：厂商由 {@code baseUrl} 决定。OpenAI 兼容协议下
 * 通义 / DeepSeek / Moonshot / 智谱 / OpenAI 本家是同一个请求格式，换厂商只改
 * baseUrl 和 model，代码零改动。真碰到不兼容的厂商才需要写适配器——到那时再加字段。
 *
 * **name 是用户自己起的别名**，不是厂商名：同一家可以配多个模型（deepseek-chat / deepseek-reasoner），
 * 光看 base_url 分不清两条，列表里也就没法选。
 *
 * **不继承 BaseEntity**：不需要 is_delete（删了再建即可），时间交给数据库默认值。
 */
@Getter
@Setter
@TableName("ai_endpoint")
public class AiEndpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 自己起的名字，例："日常问答" / "写代码" */
    private String name;

    /** OpenAI 兼容接口地址，例：https://api.deepseek.com/v1 */
    private String baseUrl;

    private String model;

    /** 只存后端，Resp 里绝不带出去 */
    private String apiKey;

    /**
     * 端点附加参数（一列 JSON）：{@code {"vendor":"deepseek","custom":{...}}}。
     *
     * <p>**为什么是"附加参数"而不是拆成两列**：厂商和模型从字典来（不用存），
     * base_url / model 表里本来就有（用户可以覆盖字典里的默认值 —— 中转地址是真实需求），
     * api_key 必须每人一份留在这儿（字典是全局的，密钥放全局 = 所有人的密钥堆一起）。
     * ⇒ 真正新增的只有"自定义参数"这一份每人不同的东西。
     *
     * <p>**custom 里放的是"直接展开进请求体"的字段**（思考开关、温度……），
     * 发请求时与 model/stream/messages 平级 ⇒ 那三个键不许被它盖掉。
     */
    private String params;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== params 列的编解码 ====================
    // **格式定义只存这一份**：谁写、谁读都用下面这几个方法，
    // 别的地方再写一遍 JSON 结构，那个结构迟早两边对不上。

    /** params 列里"厂商"那个键 */
    private static final String KEY_VENDOR = "vendor";

    /** params 列里"自定义参数"那个键 */
    private static final String KEY_CUSTOM = "custom";

    /**
     * params 列解析出来的内容。
     *
     * @param vendor 厂商 code（没设过就是 null）
     * @param custom 自定义参数（**直接展开进 chat 请求体**的那些字段：温度、思考开关……；没有就是空 map）
     */
    public record Params(String vendor, Map<String, Object> custom) { }

    /**
     * **params 列只解析一次**。
     *
     * <p>原来 {@code getVendor()} 和 {@code getCustomParams()} 各解析一遍 ——
     * 同一行数据在一次响应里被 JSON 解析两到三次。⇒ 收敛成这一个出口。
     */
    public Params params() {
        Map<String, Object> m = paramsMap();
        Object v = m.get(KEY_VENDOR);
        Object c = m.get(KEY_CUSTOM);
        @SuppressWarnings("unchecked")
        Map<String, Object> custom = c instanceof Map ? (Map<String, Object>) c : Map.of();
        return new Params(v == null ? null : String.valueOf(v), custom);
    }

    /**
     * 把厂商 + 自定义参数写进 params 一列。
     *
     * <p>两样都没有 ⇒ 这一列留空（不给它写 "{}" 这种没意义的值）。
     */
    public void applyParams(String vendor, Map<String, Object> custom) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(vendor)) {
            p.put(KEY_VENDOR, vendor.trim());
        }
        if (custom != null && !custom.isEmpty()) {
            p.put(KEY_CUSTOM, custom);
        }
        this.params = p.isEmpty() ? null : JsonUtils.toJsonString(p);
    }

    /** params 列解析成 map。空列 = 空 map。**只有 {@link #params()} 用它** —— 别处直接调 params() */
    private Map<String, Object> paramsMap() {
        if (StringUtils.isBlank(params)) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = JsonUtils.parseObjectMap(params);
            return m;
        } catch (RuntimeException e) {
            // 只有我们自己能写这一列，写坏了就是数据坏了 —— 说清楚，别甩一堆 Jackson 栈
            throw new CoreCraftException("附加参数格式错误");
        }
    }

    // ==================== 地址规范化 ====================

    /**
     * 这条端点配全了没有（地址 + 模型 + 密钥）。
     *
     * <p>**判据只存这一份**：{@code requireReady} 判一次、响应里的 {@code ready} 字段算一次 ——
     * 两处各写一遍的话，改了一处另一处就悄悄不一样了。
     */
    public boolean isReady() {
        return StringUtils.isNotBlank(baseUrl)
                && StringUtils.isNotBlank(model)
                && StringUtils.isNotBlank(apiKey);
    }

    /**
     * baseUrl 允许两种填法：只到版本段（https://x/v1）或直接填到端点（https://x/v1/chat/completions）。
     * 后者太常见了——各家文档给的都是完整 URL，硬拼会 404，而 404 在这里看不出是"地址填错"还是别的。
     * 这是外部输入边界上的规整，不是给内部调用兜底。
     *
     * <p>**规范化只做一次**：对话地址和"拉模型清单"地址都从它派生，
     * 两边各写一份拼法，早晚有一边忘了去尾斜杠。
     */
    public static String baseOf(String baseUrl) {
        String base = StringUtils.trimToEmpty(baseUrl);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String chatSuffix = "/chat/completions";
        return base.endsWith(chatSuffix) ? base.substring(0, base.length() - chatSuffix.length()) : base;
    }

    /** 调对话用的地址 */
    public static String chatUrlOf(String baseUrl) {
        return baseOf(baseUrl) + "/chat/completions";
    }

    /**
     * 拉模型清单的地址。**OpenAI 兼容标准就是 GET {base}/models**，
     * 不按厂商分叉——厂商由地址决定，不引 provider 字段。
     */
    public static String modelsUrlOf(String baseUrl) {
        return baseOf(baseUrl) + "/models";
    }
}