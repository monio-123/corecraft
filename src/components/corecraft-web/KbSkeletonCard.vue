<template>
  <div class="kb-skel-card">
    <KbSkeleton variant="title" />
    <div class="kb-skel-card__lines">
      <KbSkeleton variant="line" />
      <KbSkeleton variant="line-short" />
    </div>
  </div>
</template>

<script setup>
import KbSkeleton from './KbSkeleton.vue'

/**
 * 知识点卡片的**同构骨架**（2026-11-13）。
 *
 * 形状对着 TopicCard 的真实结构（标题 → 两行摘要），**不多不少**：
 * 骨架比真实卡片多一块或少一块，内容到达时卡片高度就会跳一下 ⇒ 骨架必须同构，
 * 这样加载前后的容器尺寸接近一致（这是硬要求，不是审美偏好）。
 *
 * ⚠️ 改 TopicCard 的结构时，**这个骨架要同步改**，
 *    否则"加载时骨架高度 ≠ 真实卡片高度"，页面会先矮一截再弹回去。
 *
 * ⚠️ **高度是照着真实卡片算出来的，不是估的**（2026-11-13 CDP 实测校准）：
 *   真实卡片 = padding 上下 (16+12) + 标题 min-height 1.5em(17px→25.5)
 *            + 摘要 margin-top(6) + 两行摘要 (12px × 1.65 ≈ 40) + border(2)
 *            ≈ 101px
 *   骨架 = 16 + 25 + 6 + 20 + 20 + 12 + 2 = 101px
 *   ⇒ 加载前后卡片高度差 0，页面不跳版。
 */
</script>

<style>
.kb-skel-card {
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: var(--sp-12) var(--sp-13) var(--sp-11);
  display: flex;
  flex-direction: column;
}
/* 标题：真实卡片 --text-xl(17px) / line-height 1.5 / min-height 1.5em ⇒ 一行 25.5px。
   骨架取 25px（差 0.5px，肉眼不可见）。 */
.kb-skel-card .kb-skel--title {
  height: 25px;
}
/* 摘要两行：真实卡片 --text-xs(12px) / line-height 1.65 ⇒ 每行 19.8px ⇒ 画 20px。
   两行之间**不加间距**（真实摘要是连续两行文字，不是两个独立块）。 */
.kb-skel-card__lines {
  margin-top: var(--sp-6);
}
.kb-skel-card .kb-skel--line,
.kb-skel-card .kb-skel--line-short {
  height: 20px;
}
.kb-skel-card .kb-skel--line-short {
  margin-top: 0;
}
</style>
