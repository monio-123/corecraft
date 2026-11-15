package com.mo.corecraft.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MasteryCalculator} 的确定性校验（设计 §5.5：同一批证据重放必须得到同一个结果）。
 */
class MasteryCalculatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 10, 0);

    private static MasteryCalculator.EvidenceView ev(long id, long attemptId, LocalDateTime at,
                                                     double scoreRatio, double topicWeight,
                                                     boolean retention, boolean schedule) {
        return new MasteryCalculator.EvidenceView(id, attemptId, 1, at,
                BigDecimal.valueOf(scoreRatio), BigDecimal.valueOf(topicWeight),
                BigDecimal.ONE, BigDecimal.ONE,
                true, retention, schedule, "single", "paper");
    }

    /** 辅考证据：可更新 A，但不刷新 R、不推进 H、不加可信度（设计 §5.4）。 */
    private static MasteryCalculator.EvidenceView aux(long id, long attemptId, LocalDateTime at, double scoreRatio) {
        return new MasteryCalculator.EvidenceView(id, attemptId, 1, at,
                BigDecimal.valueOf(scoreRatio), BigDecimal.valueOf(0.3),
                BigDecimal.ONE, BigDecimal.ONE,
                true, false, false, "single", "paper");
    }

    @Test
    void firstEvent_setsAbilityToScore() {
        var result = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 0.8, 1.0, true, true)), T0);
        assertEquals(new BigDecimal("80.00"), result.ability());
    }

    @Test
    void secondEvent_blendsWithKAndW() {
        // 事件1：S=80, W=1 ⇒ A=80, 权重和=1
        // 事件2：S=60, W=1, K=min(1,4)=1 ⇒ A'=(80×1+60×1)/2=70
        var result = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 0.8, 1.0, true, true),
                        ev(2, 11, T0.plusDays(1), 0.6, 1.0, true, true)), T0);
        assertEquals(new BigDecimal("70.00"), result.ability());
        assertEquals(new BigDecimal("2.000"), result.effectiveEventWeight());
    }

    @Test
    void sameAttempt_multipleEvidence_collapsesToOneEvent() {
        // 同 attempt 两个评分点：S = (1.0×1 + 0.0×1)/2 = 0.5 ⇒ A=50，只有一个事件
        var result = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 1.0, 1.0, true, true),
                        ev(2, 10, T0, 0.0, 1.0, true, true)), T0);
        assertEquals(new BigDecimal("50.00"), result.ability());
        assertEquals(1, result.epochReviewCount());
    }

    @Test
    void zeroWeightGroup_producesNoEvent() {
        var zero = new MasteryCalculator.EvidenceView(1, 10, 1, T0,
                BigDecimal.valueOf(0.5), BigDecimal.ZERO,
                BigDecimal.ONE, BigDecimal.ONE, true, true, true, "single", "paper");
        var result = MasteryCalculator.replay(List.of(zero), T0);
        assertNull(result.ability());
        assertEquals(0, result.confidenceLevel());
    }

    @Test
    void disqualifiedEvidence_isIgnoredEntirely() {
        var dead = new MasteryCalculator.EvidenceView(1, 10, 1, T0,
                BigDecimal.valueOf(0.5), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, false, false, false, "single", "paper");
        var result = MasteryCalculator.replay(List.of(dead), T0);
        assertNull(result.ability());
    }

    @Test
    void goodPerformance_doublesH_cappedAt180() {
        List<MasteryCalculator.EvidenceView> chain = new java.util.ArrayList<>();
        LocalDateTime at = T0;
        for (int i = 0; i < 10; i++) {
            chain.add(ev(i + 1, 100 + i, at, 1.0, 1.0, true, true));
            at = at.plusDays(1);
        }
        var result = MasteryCalculator.replay(chain, T0);
        assertEquals(new BigDecimal("180"), result.memoryStabilityDays());
    }

    @Test
    void weakPerformance_halvesH_floorAt1() {
        var result = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 0.3, 1.0, true, true)), T0);
        // H 从 1 开始：减半后 max(0.5, 1) = 1（已在下限）
        assertEquals(BigDecimal.ONE, result.memoryStabilityDays());
        assertEquals(1, result.epochLapseCount());
    }

    @Test
    void eventsBeforeLearnedAt_doNotAdvanceSchedule() {
        // 证据发生在标记已学之前（先考试后标记已学）⇒ 能力留下、调度不动
        var result = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 0.9, 1.0, true, true)),
                T0.plusDays(1));
        assertEquals(new BigDecimal("90.00"), result.ability());
        assertNull(result.nextReviewAt());
        assertEquals(0, result.epochReviewCount());
    }

    @Test
    void auxEvidence_updatesAbilityButNotScheduleOrConfidence() {
        var result = MasteryCalculator.replay(
                List.of(aux(1, 10, T0, 1.0)), T0);
        assertEquals(new BigDecimal("100.00"), result.ability());
        assertNull(result.lastMasteryAt());
        assertNull(result.nextReviewAt());
        assertEquals(0, result.confidenceLevel());
    }

    @Test
    void confidence_needsDistinctDatesAndDiversity() {
        // 两个事件、两个日期、同题型同来源 ⇒ 无多样性 ⇒ 初步
        var sameDay = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 1.0, 1.0, true, true),
                        ev(2, 11, T0.plusDays(1), 1.0, 1.0, true, true)), T0);
        assertEquals(1, sameDay.confidenceLevel());

        // 加上不同题型的第三个事件（不同日期）⇒ 较可信
        var diverse = MasteryCalculator.replay(
                List.of(ev(1, 10, T0, 1.0, 1.0, true, true),
                        ev(2, 11, T0.plusDays(1), 1.0, 1.0, true, true),
                        new MasteryCalculator.EvidenceView(3, 12, 1, T0.plusDays(2),
                                BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ONE, BigDecimal.ONE,
                                true, true, true, "case", "paper")), T0);
        assertEquals(2, diverse.confidenceLevel());
    }

    @Test
    void retentionR_halvesAtHalfLife() {
        BigDecimal r = MasteryCalculator.retentionR(
                new BigDecimal("10"), T0, T0.plusDays(10));
        assertEquals(0, r.compareTo(new BigDecimal("0.5")));  // 2^(-10/10) = 0.5
        assertTrue(MasteryCalculator.retentionR(BigDecimal.ONE, null, T0).compareTo(BigDecimal.ONE) == 0);
    }

    @Test
    void sortForReplay_ordersByAssessedAtThenAttemptThenId() {
        var a = ev(3, 30, T0, 1.0, 1.0, true, true);
        var b = ev(1, 30, T0, 1.0, 1.0, true, true);
        var c = ev(2, 20, T0, 1.0, 1.0, true, true);
        var sorted = MasteryCalculator.sortForReplay(List.of(a, b, c));
        assertEquals(List.of(c, b, a).stream().map(MasteryCalculator.EvidenceView::id).toList(),
                sorted.stream().map(MasteryCalculator.EvidenceView::id).toList());
    }

    @Test
    void replay_isDeterministic() {
        List<MasteryCalculator.EvidenceView> input = List.of(
                ev(2, 11, T0.plusDays(1), 0.6, 1.0, true, true),
                ev(1, 10, T0, 0.8, 1.0, true, true));
        var r1 = MasteryCalculator.replay(MasteryCalculator.sortForReplay(input), T0);
        var reversed = new java.util.ArrayList<>(input);
        java.util.Collections.reverse(reversed);
        var r2 = MasteryCalculator.replay(MasteryCalculator.sortForReplay(reversed), T0);
        assertEquals(r1.ability(), r2.ability());
        assertEquals(r1.nextReviewAt(), r2.nextReviewAt());
    }

    // ==================== 目录聚合口径（设计 §8.1） ====================

    @Test
    void confidenceCoefficient_mapsThreeTiersLinearly() {
        assertEquals(0, MasteryCalculator.confidenceCoefficient(0).compareTo(BigDecimal.ZERO));
        assertEquals(0, MasteryCalculator.confidenceCoefficient(3).compareTo(BigDecimal.ONE));
        // 档 1 = ⅓、档 2 = ⅔（compareTo 判等：scale 不同不算 equals）
        assertEquals(0, MasteryCalculator.confidenceCoefficient(1)
                .compareTo(new BigDecimal("0.333333333333")));
        assertEquals(0, MasteryCalculator.confidenceCoefficient(2)
                .compareTo(new BigDecimal("0.666666666667")));
        // 越界档位按边界算：历史脏数据不该把聚合算崩
        assertEquals(0, MasteryCalculator.confidenceCoefficient(-1).compareTo(BigDecimal.ZERO));
        assertEquals(0, MasteryCalculator.confidenceCoefficient(9).compareTo(BigDecimal.ONE));
    }

    @Test
    void topicWeight_isHalfPlusHalfCoefficient() {
        // 无可信度 0.5 / 满档 1.0 / 档 1 = 0.5 + 0.5×⅓ = ⅔
        assertEquals(0, MasteryCalculator.topicWeight(0).compareTo(new BigDecimal("0.5")));
        assertEquals(0, MasteryCalculator.topicWeight(3).compareTo(BigDecimal.ONE));
        assertEquals(0, MasteryCalculator.topicWeight(1)
                .compareTo(new BigDecimal("0.666666666667")));
    }

    @Test
    void weightedMastery_nullWhenNoEligibleItem() {
        // 没有合格条目 = 中性空态，不是 0（§8.1「未学、已学未评估均不按 0 混入掌握度」）
        assertNull(MasteryCalculator.weightedMastery(null, null));
        assertNull(MasteryCalculator.weightedMastery(BigDecimal.ZERO, BigDecimal.ZERO));
        assertNull(MasteryCalculator.weightedMastery(new BigDecimal("50"), BigDecimal.ZERO));
    }

    @Test
    void weightedMastery_weightedAverageOfM() {
        // M=80 w=1.0 与 M=60 w=0.5：(80×1 + 60×0.5)/1.5 = 73.33… ⇒ 73.33
        BigDecimal m = MasteryCalculator.weightedMastery(new BigDecimal("110"), new BigDecimal("1.5"));
        assertEquals(0, m.compareTo(new BigDecimal("73.33")));
    }
}
