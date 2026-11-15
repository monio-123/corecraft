package com.mo.corecraft.service.support;

/**
 * 知识平台用到的**字典 code 常量**（唯一一份）。
 *
 * <p>⚠️ 原来这类字面量在 {@code KpTopicServiceImpl} / {@code KpGenTaskServiceImpl} /
 * {@code MasteryScopeResolver} 里各写过一份 —— 三处该收敛（2026-11-15 落地），
 * 漏改一处就是 {@code learnTypeId()} / {@code typeDefaults()} 静默查空。
 * 前端 store 的 {@code TOPIC_TYPE_DICT} 是同一个口径（两端各一份是接口契约，见
 * FEATURES「前后端各写一份常量」待办）。
 */
public final class KpDictCodes {

    /** 知识点类型（笔记 / 学习）：决定掌握度参与判据与出题材料分组 */
    public static final String TOPIC_TYPE = "kp_topic_type";

    private KpDictCodes() {
    }
}
