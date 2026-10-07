/**
 * 界面偏好：面板调的那些**具体值**，按**维度**一组一组存。
 *
 * ⚠️ **这里原来还有一整套 `--ui-scale` 乘数（顶栏一个百分比 chip、85/100/115/130 四档），
 * 2026-10-07 整个删掉了。** 删的理由不是"不够好看"，是两条更硬的：
 *
 * 1. **说不清它是什么的比例。** 那个数乘的是 66 个毫不相干的数值——2px 边框、4px 间距、
 *    1440px 内容宽一起乘 1.3。没有任何一句话能解释 130% 代表什么。
 * 2. **粒度对不上。** 想调某一类做不到：动 4px 会连带把 1440px 和 2px 一起动。
 *    用户原话："**只能统一调无法精确调，某个地方我很满意但是另外的地方不满意**"。
 *
 * 换成什么：**没有全局旋钮了，按维度一个一个来。** 组件里写 `var(--token)`，
 * 面板改的是 token 的**具体值**。第一组是**侧边栏**（宽度 + 密度），
 * 其余组（字号 / 间距 / 圆角 / 内容宽…）按同样方式逐个加。
 *
 * **字号不在其中**：它由**浏览器 Ctrl +/−** 管，默认就是最好看的。
 * ⚠️ 也**别用 CSS `zoom` 顶替这个空缺**——zoom 在渲染那一刻乘，会把 JS 行内样式的
 * 像素值也乘一遍，于是「JS 量到的坐标」和「浏览器画出来的位置」对不上
 * （拖拽幽灵飘到光标旁边、`elementFromPoint` 命中的不是你看到的那行、
 * EP 浮层同样飘）。自建拖拽和 EP 浮层定位都靠「JS 坐标 = 视口坐标」这条不变式。
 *
 * 值的兜底一律回 base.css 的 `:root`（那里是唯一权威），本文件不重复写默认。
 */

const PREF_KEY = 'app-ui-prefs'

/**
 * 侧边栏三档密度。值刻意取自 `--sp-*` 阶梯（见 base.css），不新造数字。
 * 行高 ≈ 15px 文字的 1.5 倍行距 + 上下内距 ×2：紧凑 ≈ 31px ／ 适中 ≈ 37px ／ 松散 ≈ 47px。
 */
export const SIDE_DENSITY = {
  compact: { label: '紧凑', px: 'var(--sp-8)', py: 'var(--sp-4)', gap: 'var(--sp-4)', listGap: '0px' },
  normal: { label: '适中', px: 'var(--sp-10)', py: 'var(--sp-7)', gap: 'var(--sp-7)', listGap: 'var(--sp-1)' },
  roomy: { label: '松散', px: 'var(--sp-12)', py: 'var(--sp-10)', gap: 'var(--sp-10)', listGap: 'var(--sp-4)' }
}

/** 侧边栏宽度的可调范围。下限 200 是"财务基本知识"这类目录名还基本放得下。 */
export const SIDE_WIDTH = { min: 200, max: 360, step: 4, def: 264 }

/** 每组偏好：改了哪些 CSS 变量。**加新组时在这里加一条，面板跟着加一行。 */
const GROUPS = {
  side: ['--w-side', '--side-px', '--side-py', '--side-gap', '--side-list-gap']
}

function readPrefs() {
  try {
    const raw = localStorage.getItem(PREF_KEY)
    const p = raw ? JSON.parse(raw) : {}
    return {
      sideWidth: Number.isFinite(p.sideWidth) ? p.sideWidth : null,
      sideDensity: SIDE_DENSITY[p.sideDensity] ? p.sideDensity : null
    }
  } catch {
    // 存的值坏了就当没存，让 :root 的默认值顶上
    return { sideWidth: null, sideDensity: null }
  }
}

/**
 * 把偏好写到 `<html>` 的行内 style。行内压过样式表，所以用户选的值立刻生效，
 * 而 base.css 的 `:root` 一个字都不用改——**没设过偏好的用户看到的仍是设计值**。
 */
function applyPrefs(prefs) {
  const el = document.documentElement
  if (prefs.sideWidth) el.style.setProperty('--w-side', `${prefs.sideWidth}px`)
  const d = prefs.sideDensity && SIDE_DENSITY[prefs.sideDensity]
  if (d) {
    el.style.setProperty('--side-px', d.px)
    el.style.setProperty('--side-py', d.py)
    el.style.setProperty('--side-gap', d.gap)
    el.style.setProperty('--side-list-gap', d.listGap)
  }
}

/** 壳启动时调一次。读不到偏好就什么都不写，:root 说了算 */
export function applyUiPrefs() {
  applyPrefs(readPrefs())
}

export function getUiPrefs() {
  return readPrefs()
}

export function saveUiPrefs(patch) {
  const next = { ...readPrefs(), ...patch }
  // 存干净的那份：null 的意思是"用默认值"，不该存进去
  const clean = {}
  if (next.sideWidth) clean.sideWidth = next.sideWidth
  if (next.sideDensity) clean.sideDensity = next.sideDensity
  localStorage.setItem(PREF_KEY, JSON.stringify(clean))
  applyPrefs(next)
}

/** 壳卸载时用：只摘行内，不动存档。侧边栏那组变量在别的壳里没有意义 */
export function clearUiPrefs() {
  const el = document.documentElement
  for (const k of Object.values(GROUPS).flat()) el.style.removeProperty(k)
}

/** 「恢复默认」：把行内全摘掉，:root 接管，同时清掉存档 */
export function resetUiPrefs() {
  localStorage.removeItem(PREF_KEY)
  clearUiPrefs()
}

/** 宽度滑块的越界修正：面板外面也可能在调，别让 5000px 那种值落进样式表 */
export function clampSideWidth(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return SIDE_WIDTH.def
  return Math.min(SIDE_WIDTH.max, Math.max(SIDE_WIDTH.min, Math.round(n / SIDE_WIDTH.step) * SIDE_WIDTH.step))
}
