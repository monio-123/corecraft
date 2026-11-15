<template>
  <div class="kb-skel-row">
    <div class="kb-skel-row__body">
      <KbSkeleton variant="title" width="46%" height="24px" />
      <div class="kb-skel-row__meta">
        <KbSkeleton variant="chip" width="58px" height="21px" />
        <KbSkeleton variant="chip" width="64px" height="21px" />
        <KbSkeleton variant="chip" width="38px" height="21px" />
        <KbSkeleton variant="chip" width="72px" height="21px" />
      </div>
    </div>
    <KbSkeleton variant="chip" width="110px" height="22px" />
    <KbSkeleton variant="chip" width="78px" height="28px" />
  </div>
</template>

<script setup>
import KbSkeleton from './KbSkeleton.vue'

/**
 * 行卡片的**同构骨架**（2026-11-13）。
 *
 * 形状对着"考试记录"那一行：标题 + 一排小标签 → 分数位 → 按钮位。
 *
 * ⚠️ **每一段的高度都对着真实行的实测值标定**（不是估的）：
 * 真实行盒模型 = 标题 24 + meta 上边距 4 + meta 21.2 = body 49.2，
 * 加上下 padding 14×2 + 上下 border 0.67×2 ⇒ **行高 78.53px**。
 * 骨架行实测 78.33px（差 0.2px）⇒ 内容到达时列表**不跳版**。
 * 改这里的高度前先去量真实行，别凭感觉改（#895 的教训）。
 */
</script>

<style>
.kb-skel-row {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: var(--sp-11) var(--sp-14);
}
.kb-skel-row__body {
  flex: 1;
  min-width: 0;
}
.kb-skel-row__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin-top: var(--sp-4);
}
/* 行间距：与真实行（.rec + .rec 的 8px）同值 ⇒ 骨架组与真实列表总高一致 */
.kb-skel-row + .kb-skel-row {
  margin-top: var(--sp-8);
}
</style>
