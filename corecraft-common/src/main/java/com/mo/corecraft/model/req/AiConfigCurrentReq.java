package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 「把这条 API 设为当前」的请求体。
 *
 * <p>**单独一个接口而不是塞进 {@code PUT /ai/config}**：那一条是存总开关的，
 * 而"选哪条"是设置页里点整行就发生的动作（不经过保存按钮）⇒ 两个动作两个口子，
 * 混在一起会出现"只想换一条，结果把开关也写了一遍"。
 */
@Getter
@Setter
public class AiConfigCurrentReq {

    /** 要设为当前的那条 API id（必须是**自己的**，归属在 service 里校验） */
    @NotNull(message = "请指定要使用的 API")
    private Long endpointId;
}
