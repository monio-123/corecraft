package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存一个 AI 接口。id 为空 = 新增，有值 = 更新。
 *
 * **apiKey 留空 = 沿用原值**：密钥一旦存下就不回传给前端，用户在设置页看不到自己填了什么。
 * 留空表示"不改"，而不是"清空密钥"——否则用户每次改个模型名都得重新抄一遍 key。
 * 真要清空走删除接口。
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