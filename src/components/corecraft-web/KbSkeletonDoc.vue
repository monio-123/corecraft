<template>
  <div class="kb-skel-doc">
    <KbSkeleton variant="doctitle" />
    <KbSkeleton variant="meta" class="kb-skel-doc__meta" />
    <KbSkeleton variant="line" class="kb-skel-doc__p" />
    <KbSkeleton variant="line-mid" class="kb-skel-doc__p" />
    <KbSkeleton variant="line" class="kb-skel-doc__p" />
    <KbSkeleton variant="line-short" class="kb-skel-doc__p kb-skel-doc__p--gap" />
    <KbSkeleton variant="line" class="kb-skel-doc__p" />
    <KbSkeleton variant="line-mid" class="kb-skel-doc__p" />
  </div>
</template>

<script setup>
import KbSkeleton from './KbSkeleton.vue'

/**
 * 详情页的**同构文档骨架**（2026-11-13）。
 *
 * 形状对着详情页真实结构：标题 → meta 行 → 正文段落。
 * ⚠️ 详情页的正文框（.content）是白底卡片，骨架必须**也在那张白卡里**，
 *    否则加载时是"一个灰块浮在空页面上"、加载完是"一张文档卡" ⇒ 结构跳变。
 *    所以本页只画**框内**的部分，外层白卡由调用方（详情页）自己出。
 */
</script>

<style>
.kb-skel-doc__meta {
  margin: var(--sp-12) 0 var(--sp-16);
}
.kb-skel-doc__p {
  margin-top: var(--sp-10);
  height: 11px;
}
.kb-skel-doc__p--gap {
  margin-top: var(--sp-16);
}
</style>
