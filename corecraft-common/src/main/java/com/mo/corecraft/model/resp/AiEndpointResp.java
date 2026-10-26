package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个 AI API 回给前端的形态。
 *
 * **apiKey 不再回传**（2026-10-26 改）：设置页只说"配了没有"（hasApiKey），
 * 不给明文。改的理由不是"密钥该藏着"这种洁癖，是**刷新模型这件事本来就得由后端做**
 * —— 厂商接口要用密钥，前端拿不到密钥就调不了 ⇒ 密钥没有必要下发到浏览器。
 *
 * ⚠️ 这条**推翻了 2026-10-07 那次"明文回传"的决定**（当时用户要能核对/复制）。
 * 现在改成：想换密钥就重新填一次；想核对是哪一把，看不出（那就把密钥名记在
 * "名称"里，比如"DeepSeek · 公司那把"）。
 */
@Getter
@Setter
public class AiEndpointResp {

    /** 调用时传这个 id，后端据此取 baseUrl / model / apiKey */
    private Long id;

    private String name;

    private String baseUrl;

    private String model;

    /** 密钥配了没有。**只给"有/没有"，不给值** */
    private Boolean hasApiKey;

    /** 厂商 code（字典里 ai_vendor_xxx 那个类型） */
    private String vendor;

    /** 自定义参数（JSON 文本，原样回显给设置页的编辑框） */
    private String custom;

    /** 这一条自己配全没有（baseUrl / model / apiKey 三样齐全） */
    private Boolean ready;
}