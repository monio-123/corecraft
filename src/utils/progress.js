/**
 * 进度百分比的**唯一算口**。
 *
 * ## 为什么要收在一处
 *
 * "done / total → 百分比"这个公式在项目里散在好几处：任务卡片的进度条、
 * 建结构对话框的进度、答题页的"已答比例"、判分页的分数比例。
 * 各写一遍就会出现两种不一致：
 *
 * - 一处**除零不判**（total 为 0 时算出 `NaN%`，进度条宽度变成 `NaN`，浏览器直接忽略）
 * - 一处**不封顶**（done 超了 total 时算出 137%，进度条溢出容器）
 *
 * ⇒ 这里给三个函数：要**数字**的用 `percentNumber`，要**带 % 的字符串**的用 `percentText`，
 * 手里已经是百分数、只想夹回 0~100 的用 `clampPercent`（掌握度那类后端值）。
 */

/**
 * 百分比（0~100 的整数）。
 *
 * @param {number} done 已完成
 * @param {number} total 总数
 * @returns {number} 0~100；total 为 0 或非法时给 0
 */
export function percentNumber(done, total) {
  const d = Number(done) || 0
  const t = Number(total) || 0
  if (t <= 0) return 0
  return clampPercent(Math.round((d / t) * 100))
}

/**
 * 把一个**已经是百分数**的值夹回 0~100（NaN → 0）。
 * 给后端下发的掌握度/比例值做显示边界，别在组件里各写一遍 clamp。
 *
 * @param {number} v
 * @returns {number} 0~100
 */
export function clampPercent(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 0
  return Math.min(100, Math.max(0, n))
}

/**
 * 百分比字符串（`'42%'`），直接给进度条的 `width`。
 *
 * @param {number} done 已完成
 * @param {number} total 总数
 * @returns {string}
 */
export function percentText(done, total) {
  return `${percentNumber(done, total)}%`
}
