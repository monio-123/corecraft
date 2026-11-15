package com.mo.corecraft.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 掌握度与复习调度的**纯计算组件**（设计 §5.5 / §6 / §7）。
 *
 * <p><b>在线增量与全量重放必须走这同一个实现、同一顺序</b>——散在 service 里各写一份
 * 必然漂移（漂了界面上什么异常都看不到）。service 只负责取数、组证据视图、写回投影。
 *
 * <p>重放顺序固定 {@code (assessed_at, attempt_id, id)}；评估事件 =
 * {@code (attempt_id, topic_id, content_epoch)} 聚合（同一 topic 的重放里按 attempt 聚即可）。
 *
 * <h3>口径备忘（校准阶段要动就动这里，阈值全在这一个类里）</h3>
 * <ul>
 *   <li>事件得分 {@code S = Σ(score_ratio × topic_weight) / Σ(topic_weight)}（设计 §5.2）。</li>
 *   <li>「权重总和为 0 不产生评估事件」按**分母为 0** 实现（归属失效 = 权重被归零）。
 *       辅考证据（低权重）参与事件、可按低权重更新 A（设计 §5.4），但**不增加独立可信度、
 *       不刷新 R、不推进 H**——这三件事由证据的三个资格列承载，不靠猜权重阈值。</li>
 *   <li>事件权重 {@code W = min(单次上限, Σ(topic_weight × reliability × novelty))}；
 *       {@code A' = (A×K + S×W)/(K+W)}，{@code K = min(当前 epoch 历史事件权重和, 4)}。</li>
 *   <li>H：好表现(≥85) 翻倍封顶 180；60–84 不动；&lt;60 减半保底 1。
 *       {@code nextReviewAt = 评估时间 + H 天}（设计 §7.1）。</li>
 *   <li>R(t) = 2^(-距 lastMasteryAt 天数 / H)。从未做过回忆测量（lastMasteryAt 为空）时 R=1。</li>
 * </ul>
 */
public final class MasteryCalculator {

    /** 算法版本：进 kp_topic_learning.algorithm_version，算法变了整体重算用 */
    public static final String ALGORITHM_VERSION = "v1";

    /** 记忆稳定度 H 的下限/上限（天） */
    public static final BigDecimal H_MIN = BigDecimal.ONE;
    public static final BigDecimal H_MAX = new BigDecimal("180");

    /** 单个评估事件的权重上限（设计 §6.3「设置单次上限」） */
    private static final BigDecimal EVENT_WEIGHT_CAP = BigDecimal.ONE;

    /** 历史事件权重和参与 K 时的封顶（设计 §6.3「K 上限为 4」） */
    private static final BigDecimal K_CAP = new BigDecimal("4");

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal THREE = new BigDecimal("3");

    private MasteryCalculator() {
    }

    /**
     * 一条证据的算法视图（重放输入）。调用方负责过滤：
     * 当前 epoch、未被替代（superseded_by 为空）、未失效。
     */
    public record EvidenceView(
            long id,
            long attemptId,
            int contentEpoch,
            LocalDateTime assessedAt,
            BigDecimal scoreRatio,
            BigDecimal topicWeight,
            BigDecimal reliabilityWeight,
            BigDecimal noveltyWeight,
            boolean abilityEligible,
            boolean retentionEligible,
            boolean scheduleEligible,
            /** 题型（已归一化或原始都行，可信度比较前再归一） */
            String questionType,
            String source) {
    }

    /** 重放结果 = 学习投影里的算法字段（生命周期累计计数不在这里，见 §9.2 重放只重建 epoch 计数）。 */
    public record ReplayResult(
            BigDecimal ability,
            Integer confidenceLevel,
            LocalDateTime lastMasteryAt,
            LocalDateTime lastScheduleAt,
            BigDecimal memoryStabilityDays,
            LocalDateTime nextReviewAt,
            Integer epochReviewCount,
            Integer epochLapseCount,
            BigDecimal effectiveEventWeight) {
    }

    /** 全量重放的固定顺序：(assessed_at, attempt_id, id) 升序。 */
    public static List<EvidenceView> sortForReplay(List<EvidenceView> evidence) {
        List<EvidenceView> sorted = new ArrayList<>(evidence);
        sorted.sort(Comparator
                .comparing(EvidenceView::assessedAt)
                .thenComparingLong(EvidenceView::attemptId)
                .thenComparingLong(EvidenceView::id));
        return sorted;
    }

    /**
     * 确定性重放：按固定顺序把证据折成 A / 可信度 / 时间戳 / H / 计数。
     *
     * @param sortedEvidence 已按 {@link #sortForReplay} 排序的当前 epoch 有效证据
     * @param learnedAt      标记已学时间；事件早于它不推进 H（「只有当时已学且符合调度资格才推进 H」）。
     *                       未学传 null。
     */
    public static ReplayResult replay(List<EvidenceView> sortedEvidence, LocalDateTime learnedAt) {
        BigDecimal ability = null;
        BigDecimal weightSum = BigDecimal.ZERO;      // 当前 epoch 历史事件权重和（K 的原料）
        BigDecimal stability = H_MIN;                // H 从 1 开始（v1：epoch 切换也回 1）
        LocalDateTime lastMasteryAt = null;
        LocalDateTime lastScheduleAt = null;
        LocalDateTime nextReviewAt = null;
        int epochReviewCount = 0;
        int epochLapseCount = 0;
        List<EventSummary> confidencePool = new ArrayList<>();

        // 事件 = (attemptId, contentEpoch) 聚合；输入已按时间排序 ⇒ LinkedHashMap 保序
        Map<String, List<EvidenceView>> events = new LinkedHashMap<>();
        for (EvidenceView e : sortedEvidence) {
            events.computeIfAbsent(e.attemptId() + "/" + e.contentEpoch(), k -> new ArrayList<>()).add(e);
        }

        for (List<EvidenceView> members : events.values()) {
            // 只有有能力资格的成员进事件（「重复达防刷条件后对全部指标无效」= 三资格列全 false，进不来）
            List<EvidenceView> eligible = members.stream().filter(EvidenceView::abilityEligible).toList();
            BigDecimal topicWeightSum = eligible.stream()
                    .map(EvidenceView::topicWeight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (eligible.isEmpty() || topicWeightSum.signum() <= 0) {
                continue; // 权重总和为 0（全是失效归属）⇒ 不产生评估事件
            }

            // S = Σ(score_ratio × topic_weight) / Σ(topic_weight)
            BigDecimal weightedScore = eligible.stream()
                    .map(e -> e.scoreRatio().multiply(e.topicWeight(), MC))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sRatio = weightedScore.divide(topicWeightSum, MC);       // 0~1
            BigDecimal s = sRatio.multiply(HUNDRED);                            // 0~100

            // W = min(单次上限, Σ(topic_weight × reliability × novelty))
            BigDecimal wRaw = eligible.stream()
                    .map(e -> e.topicWeight()
                            .multiply(e.reliabilityWeight(), MC)
                            .multiply(e.noveltyWeight(), MC))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal w = wRaw.min(EVENT_WEIGHT_CAP);

            // A 更新：第一次 A=S；后续 A' = (A×K + S×W)/(K+W)，K = min(历史权重和, 4)
            if (ability == null) {
                ability = s;
            } else {
                BigDecimal k = weightSum.min(K_CAP);
                ability = ability.multiply(k, MC).add(s.multiply(w, MC))
                        .divide(k.add(w), MC);
            }
            weightSum = weightSum.add(w);

            boolean hasPrimaryQuality = eligible.stream()
                    .anyMatch(e -> e.retentionEligible() || e.scheduleEligible());
            LocalDateTime eventTime = eligible.get(0).assessedAt();

            // R 的 t0：回忆测量资格的主考证据才刷新（辅考不刷新，设计 §5.4）
            if (eligible.stream().anyMatch(EvidenceView::retentionEligible)) {
                if (lastMasteryAt == null || eventTime.isAfter(lastMasteryAt)) {
                    lastMasteryAt = eventTime;
                }
            }

            // H / nextReviewAt：调度资格 + 事件发生时已学（设计 §3.2 / §7.2）
            boolean learnedThen = learnedAt != null && !eventTime.isBefore(learnedAt);
            if (learnedThen && eligible.stream().anyMatch(EvidenceView::scheduleEligible)) {
                int scorePercent = s.setScale(0, RoundingMode.HALF_UP).intValue();
                stability = applyPerformance(stability, scorePercent);
                lastScheduleAt = eventTime;
                nextReviewAt = eventTime.plusDays(stability.longValue());
                epochReviewCount++;
                if (scorePercent < 60) {
                    epochLapseCount++;
                }
            }

            // 可信度事件池：只收「主考级」事件（辅考不增加独立可信度，设计 §5.4）
            if (hasPrimaryQuality) {
                confidencePool.add(summarize(eventTime, members));
            }
        }

        int confidenceLevel = confidenceLevel(confidencePool);
        BigDecimal abilityOut = ability == null ? null : ability.setScale(2, RoundingMode.HALF_UP);
        return new ReplayResult(abilityOut, confidenceLevel, lastMasteryAt, lastScheduleAt,
                stability, nextReviewAt, epochReviewCount, epochLapseCount,
                weightSum.setScale(3, RoundingMode.HALF_UP));
    }

    /** H 随表现调整（设计 §7.1）：≥85 翻倍封顶；60–84 不动；<60 减半保底 1。 */
    private static BigDecimal applyPerformance(BigDecimal current, int scorePercent) {
        if (scorePercent >= 85) {
            return current.multiply(TWO).min(H_MAX).max(H_MIN);
        }
        if (scorePercent >= 60) {
            return current;
        }
        return current.divide(TWO, MC).max(H_MIN);
    }

    // ===== 可信度（§6.4）：事件池是一次 replay 调用内的局部状态，不跨调用 =====

    private record EventSummary(LocalDateTime assessedAt, Set<String> types, Set<String> sources) {
    }

    private static EventSummary summarize(LocalDateTime assessedAt, List<EvidenceView> members) {
        Set<String> types = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (EvidenceView e : members) {
            types.add(normalizeType(e.questionType()));
            if (e.source() != null && !e.source().isBlank()) {
                sources.add(e.source());
            }
        }
        return new EventSummary(assessedAt, types, sources);
    }

    /**
     * 可信度三档（设计 §6.4）：稳定 3 / 较可信 2 / 初步 1；无主考级事件 0。
     * 判据：独立日期数、事件数、多题型或多来源。题型比较前归一化。
     */
    private static int confidenceLevel(List<EventSummary> pool) {
        if (pool.isEmpty()) {
            return 0;
        }
        Set<String> dates = new HashSet<>();
        Set<String> types = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (EventSummary s : pool) {
            dates.add(s.assessedAt().toLocalDate().toString());
            types.addAll(s.types());
            sources.addAll(s.sources());
        }
        boolean diverse = types.size() >= 2 || sources.size() >= 2;
        if (pool.size() >= 4 && dates.size() >= 2 && diverse) {
            return 3;
        }
        if (pool.size() >= 2 && dates.size() >= 2 && diverse) {
            return 2;
        }
        return 1;
    }

    /**
     * 题型归一化（设计 §6.4「题型先归一化」）：AI 生成的 type 五花八门，比较前折到有限集合。
     * public：可靠性的封闭/开放判定（KpMasteryEvidenceServiceImpl）也走这张表，
     * 不能在那边另抄一份名单——加题型时两处不同步会把填空当开放题打折，且不报错。
     */
    public static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "other";
        }
        String t = raw.trim().toLowerCase();
        return switch (t) {
            case "single", "single_choice", "singlechoice", "单选", "单选题" -> "single";
            case "multiple", "multi", "multiple_choice", "多选", "多选题" -> "multiple";
            case "judge", "true_false", "truefalse", "判断", "判断题" -> "judge";
            case "fill", "blank", "填空", "填空题" -> "fill";
            case "short", "short_answer", "简答", "简答题", "concept", "概念", "概念题" -> "short";
            case "case", "案例", "案例题", "interview", "面试", "面试题" -> "case";
            case "design", "设计", "设计题" -> "design";
            default -> "other";
        };
    }

    /**
     * 保持度 R(t) = 2^(-距 lastMasteryAt 天数 / H)（设计 §6.1）。
     * 从未做过回忆测量（lastMasteryAt 为空）⇒ R = 1（刚学过/刚被碰过的新鲜状态）。
     */
    public static BigDecimal retentionR(BigDecimal stabilityDays, LocalDateTime lastMasteryAt, LocalDateTime now) {
        if (lastMasteryAt == null) {
            return BigDecimal.ONE;
        }
        BigDecimal h = (stabilityDays == null || stabilityDays.signum() <= 0) ? H_MIN : stabilityDays;
        double days = ChronoUnit.MILLIS.between(lastMasteryAt, now) / 86_400_000.0;
        if (days <= 0) {
            return BigDecimal.ONE;
        }
        double r = Math.pow(2.0, -days / h.doubleValue());
        return new BigDecimal(r, MC).min(BigDecimal.ONE).max(BigDecimal.ZERO);
    }

    /** 掌握度 M = A × R（0~100）。对外展示拆两个维度，M 只做内部信号（设计 §6.2）。 */
    public static BigDecimal masteryM(BigDecimal ability, BigDecimal r) {
        if (ability == null || r == null) {
            return null;
        }
        return ability.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 可信度系数（0~1）：三档 {@code 0/1/2/3} 线性映射到 {@code 0/⅓/⅔/1}。
     *
     * <p>目录掌握度加权用（设计 §8.1 {@code topicWeight = 0.5 + 0.5 × 可信度系数}）。
     * 系数只是把「三档」拉成连续权重，**不改变档位本身**——档位判据在 {@link #confidenceLevel}。
     * 越界档位按边界算（历史脏数据不该把聚合算崩）。
     */
    public static BigDecimal confidenceCoefficient(int level) {
        int clamped = Math.max(0, Math.min(3, level));
        return BigDecimal.valueOf(clamped).divide(THREE, MC);
    }

    /**
     * 目录聚合里一个知识点的权重（设计 §8.1）：{@code 0.5 + 0.5 × 可信度系数}。
     *
     * <p>下限 0.5 = 「未评估/刚起步的条目也有半票」，避免目录掌握度被少数高可信条目拉走。
     */
    public static BigDecimal topicWeight(int confidenceLevel) {
        return new BigDecimal("0.5").add(new BigDecimal("0.5").multiply(confidenceCoefficient(confidenceLevel), MC));
    }

    /**
     * 目录掌握度（设计 §8.1）：{@code Σ(M × w) / Σw}，{@code mSum/wSum} 由调用方按
     * {@link #topicWeight} 累好。{@code wSum ≤ 0}（没有合格条目）⇒ {@code null} ——
     * 中性空态不是 0，未学/未评估不按 0 混入（§8.1）。
     */
    public static BigDecimal weightedMastery(BigDecimal mSum, BigDecimal wSum) {
        if (mSum == null || wSum == null || wSum.signum() <= 0) {
            return null;
        }
        return mSum.divide(wSum, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 展示档位（设计 §6.5）：互斥、按序判定、命中即停：
     * {@code 待复测 → 未评估 → 稳定掌握 → 初步掌握 → 待巩固(<60) → 基本掌握(60–84) → 熟练(≥85)}。
     *
     * <p>阈值只在这一处（设计 §6.4「档位由后端下发，前端不复制阈值」）——前端只渲染拿到的词。
     * 稳定掌握 / 初步掌握先看「达到分数」（M ≥ 85）再看可信度；未达到就按 A 分档。
     *
     * @param reviewState {@code pending_reassessment} = 正文大版本切换、旧证据失效 ⇒ 待复测
     * @param ability     能力 A；{@code null} = 当前 epoch 未评估 ⇒ 未评估（不显示 0）
     * @param confidenceLevel 可信度 0=未评估 / 1=初步 / 2=较可信 / 3=稳定
     * @param m           掌握度 M = A × R（内部信号，这里只做「稳定掌握」的分数判据）
     */
    public static String masteryLevel(String reviewState, BigDecimal m) {
        if ("pending_reassessment".equals(reviewState)) {
            return "待复测";
        }
        if (m == null) {
            return "未评估";
        }
        // 四档纯分数（2026-11-14 用户拍板的最终模型）：**评分按 0–100 出四个档位**，
        // 词序 = 程度递进，条 = 档位的可视化（**最高档才 100%**），档位与条永不倒挂。
        // 可信度轴不再进档位词（首考满分的保守提示由详情页表达）。
        if (m.compareTo(new BigDecimal("95")) >= 0) {
            return "稳定掌握";
        }
        if (m.compareTo(new BigDecimal("85")) >= 0) {
            return "熟练";
        }
        if (m.compareTo(new BigDecimal("60")) >= 0) {
            return "基本掌握";
        }
        return "待巩固";
    }
}
