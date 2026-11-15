package com.mo.corecraft.service.support;

import com.mo.corecraft.model.entity.KpTopicLearning;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习投影 → **展示档位**的解析（2026-11-12）。
 *
 * <p>档位阈值只有 {@link MasteryCalculator#masteryLevel} 一处；这里只做两件事：
 * ① 把「投影行」摊平成算档位要的那几个参数；
 * ② <b>没有有效评估时返回 null</b>（而不是"未评估"这个词）。
 *
 * <p>⚠️ **为什么要区分 null 和"未评估"**：
 * 「未评估」这个档位词对**详情页那套完整信息**有意义（它回答"为什么是空的"），
 * 但在**卡片右上角那个轻签**上，"从未考过"应该表现为**什么都不画**——
 * 用户看到的空白本身就是"还没检验过"的信息，画一个「未评估」标签反而像出了错。
 */
public final class MasteryLevelResolver {

    private MasteryLevelResolver() {
    }

    /**
     * 这条知识点的展示档位。
     *
     * @return {@code null} = 当前 epoch 没有有效评估（从未考过 / 证据已失效）
     */
    public static String levelOf(KpTopicLearning learning, LocalDateTime now) {
        if (learning == null) {
            return null;
        }
        BigDecimal ability = learning.getAbility();
        // 正文大版本切换后旧证据失效 ⇒ ability 为 null，但状态是"待复测"——
        // 这时仍要显示（它在告诉用户"这条要重新考一次"），所以不能在这里直接 return null。
        boolean reassess = "pending_reassessment".equals(learning.getReviewState());
        if (ability == null && !reassess) {
            return null;
        }
        BigDecimal r = MasteryCalculator.retentionR(
                learning.getMemoryStabilityDays(), learning.getLastMasteryAt(), now);
        BigDecimal m = MasteryCalculator.masteryM(ability, r);
        return MasteryCalculator.masteryLevel(learning.getReviewState(), m);
    }
}
