package com.mo.corecraft.model.req;

import lombok.Getter;
import lombok.Setter;

/**
 * 测试一条 API 配置能不能真的调通。
 *
 * <p>**五个字段全都可以空**：带了就用带的（编辑弹窗里还没保存的那份），
 * 没带就回落到 {@code endpointId} 指向的那条端点自己存的值。列表行点「测试」
 * 只传 endpointId ⇒ 密钥不用再从前端跑一趟；弹窗里点「测试连接」传草稿 ⇒
 * 改完地址/模型不用先保存就能验。
 *
 * <p>归属由 controller 从登录态取 userId，service 按 {@code findByIdAndUserId}
 * 查端点 ⇒ 猜别人的 id 取不到他的密钥。
 */
@Getter
@Setter
public class AiEndpointTestReq {

    /** 已保存的端点 id。可空：新增时还没存进库 */
    private Long endpointId;

    /** 覆盖地址。可空 */
    private String baseUrl;

    /** 覆盖密钥。可空：留空 = 用端点自己存的那把（与保存时「留空 = 不改」同口径） */
    private String apiKey;

    /** 协议 code（openai / anthropic）。可空 */
    private String protocol;

    /** 覆盖模型名。可空 */
    private String model;
}
