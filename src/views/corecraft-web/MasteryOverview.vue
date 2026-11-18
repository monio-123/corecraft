<template>
  <!-- 书页顶部的**学习掌握概览**（方案 C，2026-11-12）。
       ⚠️ **只看已检验的内容**：概览回答的是"我学过的东西现在掌握得怎么样"，
       不是"这一堆里有多少条是学习类型"——后者是筛选条的事。
       ⚠️ **没有已检验内容时整块不渲染**（v-if 在最外层）：
       一条都没考过 ⇒ 概览条是空的 ⇒ 画一条空轨道只是噪声。
       ⚠️ **不参与掌握度的内容（笔记/备忘）不参与这个统计** —— 判据在后端，
       前端拿到的 eligibleCount 已经过滤过了。 -->
  <div v-if="hasAny" class="mov">
    <span class="mov__label">学习掌握</span>
    <!-- 分布条：每段宽度 = 该档位 / 已检验总数。段宽用百分比（inline style）。 -->
    <span class="mov__bar" role="img" :aria-label="barLabel">
      <i
        v-for="seg in segments"
        :key="seg.level"
        class="mov__seg"
        :style="{ width: seg.percent + '%', background: seg.color }"
      />
    </span>
    <span class="mov__counts">
      <span v-for="seg in segments" :key="seg.level" class="mov__count">
        <span class="mov__dot" :style="{ background: seg.color }" aria-hidden="true" />
        {{ seg.word }} {{ seg.count }}
      </span>
    </span>
    <span class="mov__total">已检验 {{ assessedCount }} / {{ eligibleCount }} 条学习</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { clampPercent } from '../../utils/progress'
import { LEVEL_ORDER, levelWord, levelColor } from './masteryLevels'
import { isMasteryShown } from '../../utils/uiPrefs'

const props = defineProps({
  /** 目录三指标（store 的 getCategoryMastery）：
   *  {eligibleCount, learnedCount, assessedCount, mastery, levelCounts} */
  row: { type: Object, default: null },
  /** 侧边栏那一档的总数（含笔记/备忘）。只用于"这堆东西"的语境，不参与统计。 */
  totalCount: { type: Number, default: 0 }
})

/** 已检验条目数（= 分布的总分母） */
const assessedCount = computed(() => props.row?.assessedCount ?? 0)

/** 参与学习的条目数（分母：已检验 + 还没检验的） */
const eligibleCount = computed(() => props.row?.eligibleCount ?? 0)

/** 有没有任何已检验条目 */
const hasAny = computed(() => isMasteryShown() && assessedCount.value > 0)

/**
 * 分布段。⚠️ **顺序固定**（弱 → 中 → 强）：概览条从左到右应该是一条
 * "从生疏到熟练"的坡度，顺序随字典的 hash 变的话每次刷新颜色位置都不一样。
 * 词 / 颜色从 `masteryLevels.js` 取（和卡片上的轻签是同一张表，改一处两边跟）。
 */
const segments = computed(() => {
  const counts = props.row?.levelCounts
  if (!counts) return []
  const total = assessedCount.value
  if (!total) return []
  return LEVEL_ORDER
    .filter((lv) => counts[lv])
    .map((lv) => ({
      level: lv,
      word: levelWord(lv),
      color: levelColor(lv),
      count: counts[lv],
      // 百分比算口收在 progress.js（判零 + 封顶 100；保留小数——分段宽度要平滑）
      percent: clampPercent((counts[lv] / total) * 100)
    }))
})

const barLabel = computed(() =>
  segments.value.map((s) => `${s.word} ${s.count}`).join(' · ')
)
</script>

<style scoped>
/* 一行：标签 · 分布条 · 各档计数 · 「已检验 N / M」。
   ⚠️ **宽度规则只写在这里**（页面根元素），不在外层壳里再写一遍。 */
.mov {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  flex-wrap: wrap;
  padding: var(--sp-10) var(--sp-12);
  margin-bottom: var(--sp-12);
  border-radius: var(--radius-lg);
  background: var(--cc-surface-2);
}
.mov__label {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  white-space: nowrap;
}
.mov__bar {
  display: inline-flex;
  height: 6px;
  width: 180px;
  border-radius: var(--radius-pill);
  overflow: hidden;
  background: var(--cc-surface-3);
  flex: none;
}
.mov__seg {
  display: block;
  height: 100%;
}
.mov__counts {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-12);
  flex-wrap: wrap;
}
.mov__count {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
  white-space: nowrap;
}
.mov__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
}
.mov__total {
  margin-left: auto;
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  white-space: nowrap;
}
</style>
