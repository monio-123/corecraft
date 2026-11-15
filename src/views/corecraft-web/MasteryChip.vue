<template>
  <!-- 卡片右上角的**掌握度轻签**（方案 C，2026-11-12）。
       ⚠️ **没有有效评估时整块不渲染**（v-if 在最外层）：
       用户定的语义是「没有掌握度就是未学」⇒ "还没考过"表现为**空白**，
       画一个「未评估」标签反而像出了错（看着像"这条坏了"）。
       ⚠️ **档位词是后端算好下发的**（masteryLevel），前端不复制阈值、不自己判断。
       ⚠️ **「基本掌握」在这里缩成「掌握」**：卡片右上角只有几十像素宽，
       四字标签会把标题挤下去；长词留给详情页那套完整信息。 -->
  <span v-if="word" class="mchip" :style="{ color }" :title="title">
    <span class="mchip__dot" aria-hidden="true" :style="{ background: color }" />
    {{ word }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { levelWord, levelColor } from './masteryLevels'

const props = defineProps({
  /** 档位词（后端下发）：待巩固 / 基本掌握 / 熟练 / 稳定掌握 / 待复测 */
  level: { type: String, default: '' }
})

/**
 * 卡片上的**短词**。⚠️ 这里是**展示层**的缩写，不是第二套档位规则 ——
 * 判定仍然只有后端那一份（MasteryCalculator），短词映射在 `masteryLevels.js`
 * （和概览那份是同一张表，改一处两边跟）。
 */
const word = computed(() => (props.level ? levelWord(props.level) : ''))

/**
 * 颜色 = `masteryLevels` 的**唯一档位色源**（2026-11-14 统一：原来 chip 走 tone 类的
 * CC 语义色（黄/绿），树行圆点走 LEVEL_STYLE.color（蓝系），同一档位两处颜色不同
 * ——用户实锤「卡片栏都是黄色 树里颜色不对应」。现在文字/圆点同走 levelColor。
 */
const color = computed(() => levelColor(props.level))

const title = computed(() => (props.level ? `掌握度：${props.level}` : ''))

</script>

<style scoped>
/* 轻签：小圆点 + 一个词。**不画底色块** —— 卡片右上角是"角标位"，
   底色块会和卡片边框抢注意力；圆点 + 字就够了。
   ⚠️ **颜色走内联 style（levelColor）**——档位色唯一色源在 masteryLevels.js
   （2026-11-14 统一：原来 chip 走 tone 类的 CC 语义色，树行圆点走 LEVEL_STYLE，
   同一档位两处颜色不同——用户实锤）。tone 类已随 levelTone 一起删（零消费者）。 */
.mchip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  padding: 1px var(--sp-6) 1px var(--sp-5);
  border-radius: var(--radius-pill);
  border: 1px solid transparent;
  font-size: var(--text-xs);
  line-height: 1.6;
  white-space: nowrap;
  cursor: default;
}
.mchip__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex: none;
}
</style>
