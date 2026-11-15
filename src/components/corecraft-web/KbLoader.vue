<template>
  <span
    class="kb-loader"
    :class="[`kb-loader--${size}`]"
    role="status"
    aria-live="polite"
    :aria-label="label"
  >
    <i class="kb-loader__ring"></i>
    <i class="kb-loader__ring kb-loader__ring--inner"></i>
    <i class="kb-loader__dot"></i>
    <span class="kb-loader__pages"><i></i><i></i><i></i></span>
  </span>
</template>

<script setup>
/**
 * 统一加载图形（2026-11-13）。
 *
 * ## 为什么是一个组件而不是各页各画
 * 全站"正在加载"的图形之前有 8 处各画各的：Element Plus 的 `<el-icon class="is-loading">`、
 * 一句纯文本「加载中…」、各页自己的 spinner。**形状和速度各不相同** ⇒ 换主题时
 * 没法一次改全（图形写死在组件里），用户在不同页面看到的是"三种加载"。
 *
 * ## 关键设计：图形由**主题 token 开关**决定，不是由组件 prop 决定
 * 结构是固定的（环 / 内环 / 中心点 / 横线四件都在 DOM 里），
 * 哪个可见、什么颜色、多快，全由 --kb-load-* 这组 token 决定 ⇒
 * **加新主题时只要在 themes.css 里覆盖这组 token，图形自动变成新主题的样子，
 * 组件代码一行都不用动。**
 * ⚠️ 反过来：主题里若不定义这组 token，就还是默认的本色签名 ⇒
 *    "看起来没适配"。这就是为什么主题适配清单里把 --kb-load-* 列为必填项。
 *
 * ## 尺寸
 * sm = 局部指示（20px）｜ 默认 32px ｜ lg = 大块居中（40px）
 * 按钮里的忙碌态由 CSS 覆盖成 1em（跟字号走），不要用 prop 传。
 */
defineProps({
  size: { type: String, default: 'md' },
  label: { type: String, default: '加载中' }
})
</script>
