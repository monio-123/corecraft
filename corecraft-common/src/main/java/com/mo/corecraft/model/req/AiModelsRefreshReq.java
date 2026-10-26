package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 「从厂商接口拉最新模型清单」这个请求。
 *
 * <p>**三样东西都能从别处拿，缺了才要传**：
 * <ul>
 *   <li>接口地址：没传就用厂商字典里的（remark），再没有就用这条端点自己存的</li>
 *   <li>API Key：没传就用这条端点自己存的（**密钥不再下发到浏览器，所以前端手上没有**）</li>
 * </ul>
 *
 * <p>⚠️ **新增端点时密钥只能由前端传上来**（那时候还没存进库）。
 * 密钥在网络上会多跑一趟（保存时本来也要跑一趟），站点走 HTTP —— 这是现状的代价，
 * 不是新增的暴露面。
 */
@Getter
@Setter
public class AiModelsRefreshReq {

    /** 厂商 code，例：deepseek */
    @NotBlank(message = "厂商不能为空!")
    private String vendor;

    /** 接口地址。可空：空则用厂商字典里的地址，再用这条端点自己存的 */
    private String baseUrl;

    /** API Key。可空：空则用这条端点自己存的（前端手上没有密钥，只能走后端这条） */
    private String apiKey;

    /** 哪条端点（拿它存的地址和密钥用）。可空：新增端点时还没有 */
    private Long endpointId;
}
