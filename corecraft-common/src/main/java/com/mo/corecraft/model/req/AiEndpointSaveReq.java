package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存一个 AI 接口。id 为空 = 新增，有值 = 更新。
 *
 * **apiKey 留空 = 沿用原值**：2026-11-11 起密钥会明文回显到编辑框，
 * 所以正常情况下保存会原样带回来；用户把框清空仍是"不改"（真要清空走删除接口），
 * 免得改个模型名就得重新抄一遍 key。
 *
 * **更新时 name / baseUrl / model 留空也是沿用原值**：同一个语义，免得出现
 * "只改模型名结果把地址也清空了"这种自己都没想到的情况。
 */
@Getter
@Setter
public class AiEndpointSaveReq {

    /** 为空则新增 */
    private Long id;

    @NotBlank(message = "名字不能为空!")
    private String name;

    private String baseUrl;

    private String model;

    private String apiKey;

    /**
     * 厂商 code（字典里 {@code ai_vendor_<code>} 那个类型）。
     *
     * <p>**null = 没传这个字段 = 沿用原值；空串 = 明确清掉**（界面上把厂商下拉清空就是它）。
     */
    private String vendor;

    /**
     * 协议 code（字典 {@code ai_protocol}：openai / anthropic）。
     *
     * <p>**null = 没传这个字段 = 沿用原值**；其它值交给 {@code AiEndpoint.protocolOf}
     * 归一，认不出来（含空串）一律 openai —— 存量端点没存过这个键，也是 openai。
     *
     * <p>⚠️ **不能照抄 vendor 的"空串 = 清掉"**：协议没有"没配"这个状态，
     * 下拉清空的结果是回到默认的 OpenAI 兼容，不是把协议抹掉。
     */
    private String protocol;

    /**
     * 自定义参数（JSON 文本）。
     *
     * <p>存下来之后**直接展开进 chat 请求体**，与 model/stream/messages 平级。
     * 空 = 不带任何额外字段。
     *
     * <p>**null = 沿用原值；空串 = 清掉**。
     *
     * <p>⚠️ 非法 JSON 直接报错，不静默丢掉 —— 静默丢掉的话用户填了半天没生效，
     * 而且界面上看不出任何异常。
     */
    private String custom;
}