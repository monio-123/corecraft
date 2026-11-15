package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 「从厂商接口拉最新模型清单」这个请求。
 *
 * <p>**四样东西都能从别处拿，缺了才要传**：
 * <ul>
 *   <li>接口地址：没传就用厂商字典里的（remark），再没有就用这条端点自己存的</li>
 *   <li>API Key：没传就用这条端点自己存的</li>
 *   <li>协议：没传就用这条端点自己存的，再没有就 openai</li>
 * </ul>
 *
 * <p>⚠️ **新增端点时密钥只能由前端传上来**（那时候还没存进库）。
 * 编辑时密钥已会明文回显到编辑框（2026-11-11），前端会把它原样带回来；
 * 留空仍是"用端点自己存的那把"，两路都走得通。
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

    /** API Key。可空：空则用这条端点自己存的（前端编辑框里也有，留空仍走后端这条） */
    private String apiKey;

    /** 协议 code（openai / anthropic）。可空：空则用端点自己存的，再退回 openai */
    private String protocol;

    /** 哪条端点（拿它存的地址、密钥和协议用）。可空：新增端点时还没有 */
    private Long endpointId;
}
