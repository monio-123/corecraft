package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 一个配置项：key 定位、value 是内容。
 *
 * **remark 留空 = 沿用原值**（和 AiEndpointSaveReq 同一套语义）：
 * 管理员只想改个值，不该顺手把之前写的说明也抹掉；要换说明就填新值。
 * 用户端不传 remark，自己的覆盖行也就没有说明。
 */
@Getter
@Setter
public class SysConfigItemReq {

    /** 配置键，格式「命名空间.键名」，例 ui.sideWidth */
    @NotBlank(message = "配置键不能为空!")
    @Size(max = 64, message = "配置键长度不能超过 64 个字符!")
    private String key;

    /**
     * 配置值，一律按字符串存：数字含义由前端解释，后端不猜。
     *
     * **上限 16000 而不是 255**：这一列已经改成 TEXT（AI 提示词最长 733 字，
     * varchar(255) 装不下）。TEXT 的字节上限是 65535，一个汉字最多 4 字节，
     * 16000 个字符是它能安全装下的上限。
     */
    @NotNull(message = "配置值不能为空!")
    @Size(max = 16000, message = "配置值长度不能超过 16000 个字符!")
    private String value;

    /** 说明（给人看的）。只有管理端会填 */
    @Size(max = 255, message = "说明长度不能超过 255 个字符!")
    private String remark;
}
