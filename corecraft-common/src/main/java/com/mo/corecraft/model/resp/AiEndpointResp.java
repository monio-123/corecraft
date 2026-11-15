package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个 AI API 回给前端的形态。
 *
 * <p>**apiKey 明文回传**（2026-11-11 改回，恢复 2026-10-07 的做法）：设置页的编辑框
 * 要能回显，输入框默认掩码、点右侧小眼睛才显明文 —— 用户要能核对/复制自己配的是哪把。
 *
 * <p>⚠️ 这条**推翻了 2026-10-26「密钥不下发」的决定**。当时的理由是"刷新模型本来
 * 就得由后端做，前端没必要拿到密钥"，代价是：编辑框留空 = 不改，用户看不出配的是哪把，
 * 改个模型名还得重新抄一遍 key。用户明确要回显后按**列表直接回传**落地：
 * GET /ai/endpoint 一次带回来，编辑弹窗直接回填，不再另开一个取密钥的接口。
 *
 * <p>{@code hasApiKey} 保留：列表上「未配全」只判"有没有"，不必拿明文去算。
 */
@Getter
@Setter
public class AiEndpointResp {

    /** 调用时传这个 id，后端据此取 baseUrl / model / apiKey */
    private Long id;

    private String name;

    private String baseUrl;

    private String model;

    /** 密钥明文（编辑弹窗回填；界面上默认掩码，点小眼睛才显明文） */
    private String apiKey;

    /** 密钥配了没有。列表的「未配全」判据只读这个，不拿明文算 */
    private Boolean hasApiKey;

    /** 厂商 code（字典里 ai_vendor_xxx 那个类型） */
    private String vendor;

    /** 协议 code（字典 ai_protocol：openai / anthropic；存量端点归一成 openai） */
    private String protocol;

    /** 自定义参数（JSON 文本，原样回显给设置页的编辑框） */
    private String custom;

    /** 这一条自己配全没有（baseUrl / model / apiKey 三样齐全） */
    private Boolean ready;
}