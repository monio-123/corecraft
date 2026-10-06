package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 一条引导问题。
 *
 * **只有 key 和 label，remark 不给前端**：remark 是"这条问题该怎么问 AI"的规则，
 * 属于服务端拼 prompt 用的内部信息。label 已经说明了这条问题是什么，
 * 前端再拿 remark 去渲染一遍 tooltip 只是把同一句话说两遍。
 */
@Getter
@Setter
public class AiQuestionResp {

    /**
     * 字典项的 value 原样透传，格式 `key:contexts`（如 `pick_category:categories`）。
     * 前端只当不透明字符串回传，服务端自己 split。
     */
    private String key;

    /** 问题文本，显示在引导按钮上 */
    private String label;
}