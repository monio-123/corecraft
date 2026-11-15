<template>
  <!-- 目录行的掌握度迷你进度条（侧边栏 / 目录树共用，设计 §8.3「轻量掌握度进度」）。
       ⚠️ **没有参与掌握度的内容 ⇒ 整条不渲染**（2026-11-12）：
       全是笔记/备忘的目录，画一条空轨道只是噪声（用户：「如果全是笔记类型那么目录本身也不展示这些了」）。
       判据 = {@code eligibleCount}：后端已经按"参与掌握度"过滤过了
       （笔记/备忘不计入），所以这里**不需要**自己判类型。
       ⚠️ **无数据（还没拉 / 没有合格条目）= 空轨道**（中性空态），不画假的 0%（§8.3）；
       数字只进 title，窄侧栏不堆多个数字。 -->
  <span v-if="show" class="mbar" :title="title">
    <span v-if="fill != null" class="mbar__fill" :style="{ width: fill + '%' }" />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { clampPercent } from '../../utils/progress'

const props = defineProps({
  /** 目录三指标（store 的 getCategoryMastery）：{eligibleCount, learnedCount, assessedCount, mastery} */
  row: { type: Object, default: null }
})

/**
 * 这条目录行**画不画**掌握条。
 *
 * ⚠️ **判据只有"有没有参与掌握度的内容"这一条**：
 * `eligibleCount` 是后端按参与判据过滤后的数量（笔记/备忘不算）。
 * `eligibleCount === 0` ⇒ 这个目录里一条学习内容都没有 ⇒ 整条不画。
 * ⚠️ **row 为 null（还没拉聚合）时也不画**：画一条空轨道等于告诉用户
 * "这个目录学得不好"，而真相是"还不知道"。
 */
const show = computed(() => (props.row?.eligibleCount ?? 0) > 0)

const fill = computed(() => {
  const m = props.row?.mastery
  if (m == null) return null
  // 百分比算口收在 progress.js（判零 + 封顶 100）
  return clampPercent(Math.round(Number(m)))
})

const title = computed(() => {
  const r = props.row
  if (!r) return ''
  return [
    `已学 ${r.learnedCount}/${r.eligibleCount}`,
    `评估 ${r.assessedCount}`,
    r.mastery == null ? '掌握度 未评估' : `掌握度 ${fill.value}%`
  ].join(' · ')
})
</script>

<style scoped>
.mbar {
  display: inline-block;
  width: 32px;
  height: 3px;
  border-radius: var(--radius-pill);
  background: var(--cc-surface-3);
  overflow: hidden;
}
.mbar__fill {
  display: block;
  height: 100%;
  border-radius: var(--radius-pill);
  background: var(--cc-primary);
}
</style>
