package com.mo.corecraft.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * **每个环节**该用多少输出额度、要不要思考 —— 由这里决定，不再由界面传。
 *
 * <p>为什么要把这件事从请求参数里挪出来（2026-11-11）：
 * <ul>
 *   <li>界面上的「开启思考」是个**厂商参数**，用户不该看见；而且换个模型它的含义就变一次
 *       （有的模型默认思考、有的压根没有思考，同一个勾选框对它们不是一回事）。</li>
 *   <li>技术上「开启」其实什么都没做——后端只有"关思考"的写法（{@code thinking.type=disabled}），
 *       "开"= 不发，等于让模型按自己的默认来。所以那个勾只在一半情况下有效。</li>
 *   <li>额度（{@code max_tokens}）是**思考和正文共用**的：开着思考时额度给小了，
 *       思考会把它吃光、正文一个字都没有（实测 deepseek-flash 16384 全用在 reasoning 上）。
 *       这层关系不该指望调用方每次自己算。</li>
 * </ul>
 *
 * <p>所以固定成「按环节一组参数」，存在 {@code sys_config}（改配置即时生效、不用发版）：
 * <pre>
 *   ai.policy.plan.thinking  = false   # 规划：关（实测开思考 219~268 秒，树只多 40%，不划算）
 *   ai.policy.plan.maxTokens = 16384   # 思考 + 正文共用，给小了会饿死正文
 *   ai.policy.point.thinking = true    # 正文：开（true = 不发 thinking，按模型默认）
 *   ai.policy.point.maxTokens= 24576
 * </pre>
 *
 * <p>**换模型不用改代码**：新模型只影响它自己认不认"关思考"这个写法（认不认都不影响这里的取舍），
 * 额度也只在超过模型上限时才需要调。没配的环节 ⇒ 返回 {@link Policy#NONE}（什么都不干预，
 * 保持老行为），所以加新环节是**增量**的，不会牵连已有调用。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiStagePolicy {

    /** 规划（出一整棵树）：键 {@code ai.prompt.knowledge.plan} */
    public static final String STAGE_PLAN = "plan";
    /** 写一条正文：键 {@code ai.prompt.knowledge.point} */
    public static final String STAGE_POINT = "point";

    private static final String KEY_FORMAT = "ai.policy.%s.%s";
    private static final String FIELD_THINKING = "thinking";
    private static final String FIELD_MAX_TOKENS = "maxTokens";

    private final SysConfigService sysConfigService;

    /**
     * 一个环节的固定参数。
     *
     * @param thinking   要不要思考；{@code null} = 不干预（按模型默认）
     * @param maxTokens  输出额度；{@code null} = 用后端的兜底值
     */
    public record Policy(Boolean thinking, Integer maxTokens) {
        /** 这个环节没配 ⇒ 什么都不干预 */
        public static final Policy NONE = new Policy(null, null);
    }

    /**
     * 按提示词配置键取参数。
     *
     * @param questionKey 这次用的是哪套提示词（如 {@code ai.prompt.knowledge.plan}）
     * @return 永远不返回 null；不认识这个键 ⇒ {@link Policy#NONE}
     */
    public Policy of(String questionKey) {
        String stage = stageOf(questionKey);
        if (stage == null) {
            return Policy.NONE;
        }
        return new Policy(
                readBoolean(String.format(KEY_FORMAT, stage, FIELD_THINKING)),
                readInt(String.format(KEY_FORMAT, stage, FIELD_MAX_TOKENS)));
    }

    /**
     * 提示词键 → 环节名。
     *
     * <p>**认不出来就返回 null**（= 不干预）。这里刻意不做"模糊匹配"：猜错一个前缀，
     * 就会拿规划的 32k 去跑判卷，一次烧掉几十倍额度，而且看不出来。
     */
    private static String stageOf(String questionKey) {
        if (StringUtils.isBlank(questionKey)) {
            return null;
        }
        return switch (questionKey.trim()) {
            // ⚠️ **分段规划的两条也算 plan 环节**：分段之后真正跑的是
            // `ai.prompt.knowledge.plan.skeleton` / `.expand`，整棵树那条只留给老任务重跑。
            // 漏掉这两个键 ⇒ 规划会按模型默认开着思考跑 4 分钟（实测 219~268 秒），
            // 而配置里写的"关思考"一点也没生效 —— 症状是"改配置没反应"。
            case "ai.prompt.knowledge.plan",
                 "ai.prompt.knowledge.plan.skeleton",
                 "ai.prompt.knowledge.plan.expand" -> STAGE_PLAN;
            case "ai.prompt.knowledge.point" -> STAGE_POINT;
            default -> null;
        };
    }

    private Boolean readBoolean(String key) {
        String raw = sysConfigService.getSystemValue(key);
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private Integer readInt(String key) {
        String raw = sysConfigService.getSystemValue(key);
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // 配错了就当没配（照旧用兜底），**不要因为一个字符让整条链路报错**
            log.warn("配置 {} 不是数字：{}，已忽略", key, raw);
            return null;
        }
    }
}
