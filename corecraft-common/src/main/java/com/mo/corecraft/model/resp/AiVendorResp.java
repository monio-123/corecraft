package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一个 AI 厂商（DeepSeek / 百炼 / 智谱……）和它下面的模型清单。
 *
 * <p>**厂商 = 字典类型，模型 = 那个类型下的字典项**：
 * 字典表本来就是「类型 + 平铺的项」两层，项下没有子项，
 * 所以"厂商 → 模型"这套层级只能这样落（不另建带 parent_id 的表）。
 *
 * <p>厂商 code 与字典类型 code 的对应：类型 code = {@code ai_vendor_} + 本 code。
 * 厂商的 OpenAI 兼容地址放在**类型的 remark** 里（类型那层才有"这个类型整体是什么"的备注位）。
 */
@Getter
@Setter
public class AiVendorResp {

    /** 厂商 code，例：deepseek。字典类型 code 是 ai_vendor_ + 它 */
    private String code;

    /** 厂商显示名，例：DeepSeek */
    private String name;

    /** 该厂商的 OpenAI 兼容地址（字典类型的 remark）。可能为空（管理员还没填） */
    private String baseUrl;

    /** 该厂商现在的模型清单 */
    private List<Model> models;

    /**
     * 一个模型。
     *
     * <p>**id 是真正发给 API 的那个串**（label 只是给人看的）。
     * 厂商接口返回的字段名不统一（id / name / model），这里统一成 id/label。
     */
    @Getter
    @Setter
    public static class Model {

        /** 模型 id，发给 API 用的就是这个 */
        private String id;

        /** 显示名。厂商没给名字时就是 id */
        private String label;
    }
}
