<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="sw"
    stroke-linecap="round"
    stroke-linejoin="round"
    style="width: 1em; height: 1em"
    aria-hidden="true"
    v-html="body"
  />
</template>

<script setup>
/**
 * 业务壳统一图标（2026-11-08）：替换 EP 图标的粗描边风格，全站一套 1.7px 圆帽线性族。
 * 尺寸跟随 font-size（1em）—— 原来挂在 el-icon 上的字号规则全部继续生效。
 * 只换视觉：名称即语义，交互与事件链路零改动。
 */
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  sw: { type: [Number, String], default: 1.7 }
})

const ICONS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.4 5.4l1.9 1.9M16.7 16.7l1.9 1.9M18.6 5.4l-1.9 1.9M7.3 16.7l-1.9 1.9"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/>',
  // 侧栏收起/展开（顶栏那个按钮，两态互为镜像）。
  // fold ＝ 侧栏展开时的按钮（"把它折起来"）：完整外框 + 左侧分栏线 —— 分栏线就是侧栏。
  // ⚠️ expand 原来**只有一个 <rect>**（2026-11-11 用户原话：「这个图标在侧边栏没收缩的时候还好看
  //   收缩后变成一个正方形了 很丑」）—— 一个空框在视觉上就是"一个正方形"，跟旁边的图标不是一个东西。
  // ⇒ 改成三段：① 左侧开口括号（侧栏的"位置"，现在是空的）② 右侧主区矩形（当前内容区）
  //   ③ 主区里一个居中右箭头（侧栏从左边滑进来）。
  // 关键：**轮廓左边是开口的**，结构上就不再是闭合的正方形；箭头是第二层信息，
  // 17px 的实际按钮尺寸下仍能看出方向，不会糊成一团。
  // 三个候选（Lucide 约定式 / 双矩形 / 开口括号+主区）渲染对比后选的第三个。
  fold: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M9.5 4v16"/>',
  expand:
    '<path d="M9.5 4H6a2.5 2.5 0 0 0-2.5 2.5v11A2.5 2.5 0 0 0 6 20h3.5"/><rect x="9.5" y="4" width="11.5" height="16" rx="2.5"/><path d="M13.4 10l2.4 2-2.4 2"/>',
  grid: '<rect x="3" y="3" width="7.5" height="7.5" rx="1.5"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1.5"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.5"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1.5"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  'arrow-right': '<path d="M5 12h14"/><path d="M13 6l6 6-6 6"/>',
  'arrow-left': '<path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/>',
  // 大纲：三条线 + 三个项目点（点是实心的 —— 1px 空心圆环在这个尺寸下只是一团灰）
  list: '<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.6" cy="6" r="1.1" fill="currentColor" stroke="none"/><circle cx="4.6" cy="12" r="1.1" fill="currentColor" stroke="none"/><circle cx="4.6" cy="18" r="1.1" fill="currentColor" stroke="none"/>',
  // 更多（⋯）：三个实心点，同上理由
  dots: '<circle cx="5" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.5" fill="currentColor" stroke="none"/>',
  'caret-down': '<path d="M6 9l6 6 6-6"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
  note: '<rect x="5" y="3.5" width="14" height="17" rx="2"/><path d="M9 8.5h6M9 12.5h6M9 16.5h3.5"/>',
  // 目录（文件夹）：斜切页签 + 轮廓，和 note/text 的矩形语言同族（2026-11-13 菜单图标用）
  folder: '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5h4.2l1.8 2.4h9A1.5 1.5 0 0 1 21 8.9v9.6a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  fullscreen: '<path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M16 3h3a2 2 0 0 1 2 2v3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/><path d="M8 21H5a2 2 0 0 1-2-2v-3"/>',
  // 正文/工具栏（"Aa"太花，用经典 T 形文字图标）
  text: '<path d="M4 7V4h16v3"/><path d="M9 20h6"/><path d="M12 4v16"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6M14 11v6"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  filter: '<path d="M4 6h16M7 10h10M10 14h4"/>',
  check: '<path d="M4 12l5 5L20 6"/>',
  /* 2026-11-13 换回老 bak 稿（detail-bak.html）的星芒：用户实测高分屏上曲线小星糊成一团，
     老稿的直边大星清晰——主星占 24 视框 54%（上一版只有 33%），路径与 bak 一字未改。
     仍要 translate 补齐：两颗星合计 bbox 中心在 (13.75,11.5)，同排图标都以 (12,12) 为中心，
     不补会在侧栏往右上飘（上一版实测结论，偏移方向一致）。 */
  sparkle: '<g transform="translate(-1.75 0.5)"><path d="M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9z"/><path d="M19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9z"/></g>',
  external: '<path d="M15 4h5v5"/><path d="M20 4l-8 8"/><path d="M18.5 14v4.5a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2H10"/>',
  copy: '<rect x="9" y="9" width="12" height="12" rx="2.2"/><path d="M5.5 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v.5"/>',
  unlink: '<path d="M10 7.5H8a4.5 4.5 0 0 0 0 9h2"/><path d="M14 7.5h2a4.5 4.5 0 0 1 0 9h-2"/><path d="M8.8 12h6.4"/><path d="M4 4l16 16"/>',
  'arrow-up': '<path d="M12 19V5"/><path d="M5.5 11.5L12 5l6.5 6.5"/>',
  // 下箭头（↑↓ 跳转那一对里的第二个，2026-11-10 补）：
  // 与 arrow-up **严格镜像**（同一个 24 视框、同样的 1.7px 圆帽线）⇒ 成对出现时对称。
  'arrow-down': '<path d="M12 5v14"/><path d="M5.5 12.5L12 19l6.5-6.5"/>',

  /* ---------- 编辑器工具栏族（2026-11-16 重画） ----------
     原来这一排用的是 Element Plus 图标（粗描边、方块感），而 meta 行那一排是自绘的
     1.7px 细线 ⇒ **同一屏两种画风**（用户：「图标还是老的」）。
     这一族统一：24 视框 / 1.7px 圆帽圆角 / 矩形+直线语言，笔法与上面一致。 */
  // 标题：H 字形（竖干 + 上下两弓，设计稿 v5 逐字一致）
  heading: '<path d="M6 4v16M6 5h8a3.5 3.5 0 010 7H6m4 0h6a3.5 3.5 0 010 7h-6"/>',
  // 无序列表：三条线 + 三个实心圆点（比大纲的点小一号，凑近看能分辨）
  bullets: '<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.4" cy="6" r="1.05" fill="currentColor" stroke="none"/><circle cx="4.4" cy="12" r="1.05" fill="currentColor" stroke="none"/><circle cx="4.4" cy="18" r="1.05" fill="currentColor" stroke="none"/>',
  // 有序列表：三条线 + 数字（数字与线同一粗细）
  numbers: '<path d="M10 6h10M10 12h10M10 18h10"/><path d="M4 4.6h1.5v3.1M3.6 15.1c.2-1 1.7-1.3 2.1-.5.4.8-1.9 1.6-2.1 2.4M3.7 18.3c.4-1 2.5-1.1 2.5.1 0 .8-1.1 1.1-2.1.9"/>',
  // 代码块：尖括号对（折角语言，比方框轻）
  'code-block': '<path d="M9 4.5l-4.5 7.5L9 19.5M15 4.5l4.5 7.5-4.5 7.5"/>',
  // 引用块：竖线 + 引文行
  'quote-block': '<path d="M5 5v14M10 7h9M10 12h9M10 17h6"/>',
  // 分割线：中点断开（可点的暗示）
  divider: '<path d="M4 12h16"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/>',
  // 表格：线宽统一，不再是方块
  table: '<rect x="3.5" y="5" width="17" height="14" rx="2"/><path d="M3.5 9.5h17M9.5 9.5V19"/>',
  // 插入链接：两段链环
  link: '<path d="M9.5 14.5a4 4 0 005.7 0l2.8-2.8a4 4 0 00-5.7-5.7l-1.6 1.6"/><path d="M14.5 9.5a4 4 0 00-5.7 0L6 12.3a4 4 0 005.7 5.7l1.6-1.6"/>',
  // 设为目录链接：文件夹 + 入箭头
  'folder-link': '<path d="M3 6.5A1.5 1.5 0 014.5 5h4.2l1.8 2.4h9A1.5 1.5 0 0121 8.9v9.6a1.5 1.5 0 01-1.5 1.5h-15A1.5 1.5 0 013 18.5z"/><path d="M12 11v5M9.5 13.5L12 11l2.5 2.5"/>',
  // 清除格式：橡皮式（扫掉格式）
  eraser: '<path d="M4.5 19.5h15"/><path d="M7 16.5l7.5-7.5"/><path d="M13 6.5l4.5 4.5-6.5 6.5H9v-2.5z"/>',
  // 字体：A 形（排版）
  typeface: '<path d="M5 19l6.5-13h1L19 19"/><path d="M7.6 14h8.8"/>',
  // 编辑工具栏开关（显示/隐藏那一排格式工具）：三条格式行 + 短竖线
  // —— 一眼知道"点了会出一排格式工具"（原来用 EP 的 T 形，认不出是什么）
  'format-lines': '<path d="M4 6h16M4 12h16M4 18h16"/><path d="M9 4v4M15 10v4M9 16v4"/>',
  // 导出：箭头出 + 底线
  download: '<path d="M12 3v11"/><path d="M7.5 9.5L12 14l4.5-4.5"/><path d="M4 17.5h16"/>',
  // 导入：箭头入 + 底线（与 download 同一族镜像）
  upload: '<path d="M12 3v12"/><path d="M7.5 10.5L12 15l4.5-4.5"/><path d="M4 17.5h16"/>'
}

const body = computed(() => ICONS[props.name] || '')
</script>
