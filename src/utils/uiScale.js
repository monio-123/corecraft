/**
 * 界面缩放偏好。
 *
 * 底座是 CSS 变量 `--ui-scale`：全站所有尺寸都写成
 * `calc(Npx * var(--ui-scale))`（见 base.css 的 token 说明），所以这个模块
 * 只负责一件事——**把该填的值写进 `<html>` 的行内 style**。行内样式压过样式表，
 * 所以用户选的值能生效，而不用去改任何 CSS 文件。
 *
 * ## 为什么不用 CSS `zoom`（2026-10-06 踩过一遍，已回退）
 *
 * `zoom` 是在渲染那一刻乘，会把 JS 写进**行内样式**的像素值也一起乘。
 * 于是「JS 量到的坐标」和「浏览器画出来的位置」对不上：
 * 拖拽幽灵飘到光标旁边、`elementFromPoint` 命中的不是你看到的那行，
 * Element Plus 浮层（floating-ui 算完写回 translate）同样飘。
 * `calc()` 在声明那一刻乘，行内值原样不动，两个坐标空间始终一致。
 *
 * ## 两个壳各存各的
 *
 * 同一时刻只有一个壳挂在页面上，所以写 `<html>` 行内不会打架：谁在谁说了算。
 * 切走时把行内样式摘掉，`--ui-scale` 退回 `:root` 的 1。
 */

const KEYS = { app: 'app-ui-scale', admin: 'admin-ui-scale' }

/** 固定几档。滑块会存出 1.1734 这种没法复现、也没法说清的值，档位不会。 */
export const UI_SCALES = [
  { value: 0.85, hint: '85%' },
  { value: 1, hint: '100%' },
  { value: 1.15, hint: '115%' },
  { value: 1.3, hint: '130%' }
]

/** 默认值不在这重复写：`:root` 的 `--ui-scale` 是唯一权威 */
function cssDefault() {
  const n = Number(
    getComputedStyle(document.documentElement).getPropertyValue('--ui-scale').trim()
  )
  return Number.isFinite(n) && n > 0 ? n : 1
}

export function getUiScale(shell = 'app') {
  const saved = Number(localStorage.getItem(KEYS[shell]))
  return Number.isFinite(saved) && saved > 0 ? saved : cssDefault()
}

export function applyUiScale(value) {
  document.documentElement.style.setProperty('--ui-scale', String(value))
}

export function saveUiScale(shell, value) {
  localStorage.setItem(KEYS[shell], String(value))
  applyUiScale(value)
}

/** 壳卸载时把行内摘掉，让 --ui-scale 退回 :root 的默认值 */
export function clearUiScale() {
  document.documentElement.style.removeProperty('--ui-scale')
}