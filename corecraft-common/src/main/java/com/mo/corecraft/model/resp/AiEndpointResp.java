package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 一个 AI API 回给前端的形态。
 *
 * **apiKey 明文回传**（2026-10-07 用户拍板）：设置页要把它和名称、地址、模型一样
 * 当普通字段显示和编辑，不要"已填写，留空表示不改"那一套——那套是为"密钥不能回前端"
 * 的假设设计的，用户每天要核对/复制的就是它，藏起来反而是负担。
 *
 * ⚠️ 代价（已知并接受）：密钥随列表响应一起下发到浏览器，且站点走 HTTP。
 * 要收回这条得靠 HTTPS + 密钥加密存储，不是改这一行能解决的。
 */
@Getter
@Setter
public class AiEndpointResp {

    /** 调用时传这个 id，后端据此取 baseUrl / model / apiKey */
    private Long id;

    private String name;

    private String baseUrl;

    private String model;

    /** 明文回传。设置页直接显示，用户自己看得见改得了 */
    private String apiKey;

    /** 这一条自己配全没有（baseUrl / model / apiKey 三样齐全） */
    private Boolean ready;
}