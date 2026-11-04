/**
 * 时间范围：不限 / 今天 / 本周 / 本月。**只有这一份定义**。
 *
 * ## 为什么收成一处
 *
 * 「今天」这个口径有三处都要用：
 * ① **列表页发给服务端的起止** —— 分页在服务端 ⇒ 时间条件必须在服务端；
 * ② **列表页的分面数字**（待归类 / 类型 / 标签下拉里的个数）—— 那些数字在
 *    **结构索引**上算，而索引那条 SQL **不吃时间条件** ⇒ 前端在索引那份数据上
 *    自己按 `createTime` 筛；
 * ③ **首页的「今日新增」**。
 *
 * 三处各算一遍的话，数字会和条数对不上 —— **而界面上什么异常都看不到**
 * （下拉里写着 5、点进去 3）。
 *
 * ## 口径
 *
 * **本地墙钟时间，不带时区**：库里 `create_time` 存的就是本地时间，
 * 前端也按本地时区算起止 ⇒ 两端比的是同一个东西，**不会因为时区换算差一天**。
 *
 * ⚠️ **上界一律到「今天 23:59:59」**：今天是几号就是几号。本周 / 本月同理
 * （不取到本周日 / 本月末 —— 那是还没到的将来，不该算数）。
 */

/** 范围选项。**顺序就是界面上的顺序**。 */
export const TIME_RANGES = [
  // 「不限」= 不限时间，但列表按新的在前排（服务端本来就是这个顺序）。
  // ⚠️ 原来这个选项叫「最近」（2026-11-03 用户拍板改成「不限」）：
  // 「最近」是个**说不清边界**的词（最近多久？最近 100 条？最近访问？），
  // 而它实际的口径就是"不按时间筛" ⇒ 名字要跟口径对上。
  { kind: 'all', label: '不限' },
  { kind: 'today', label: '今天' },
  { kind: 'week', label: '本周' },
  { kind: 'month', label: '本月' }
]

/** 默认范围：不限时间。 */
export const DEFAULT_RANGE = 'all'

/** 范围名对不对。URL 上来的东西**不认就是默认** —— 一个坏参数不该把页面搞空。 */
export function isRangeKind(kind) {
  return TIME_RANGES.some((r) => r.kind === kind)
}

/** 某个范围的名字（认不出来的按默认算）。 */
export function rangeLabel(kind) {
  const hit = TIME_RANGES.find((r) => r.kind === kind)
  return hit ? hit.label : TIME_RANGES[0].label
}

const pad = (n) => String(n).padStart(2, '0')

/** `yyyy-MM-dd HH:mm:ss`（本地）。和后端 `@DateTimeFormat` 的 pattern 一致。 */
function stamp(d) {
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

function startOfDay(d) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

/**
 * 某个范围的起止。**不限（all）返回 null** —— 没有时间条件可传。
 *
 * @param {string} kind TIME_RANGES 里的 kind
 * @param {Date} now "今天"是哪天（默认当前时间）
 * @returns {{from: string, to: string}|null}
 */
export function rangeBounds(kind, now = new Date()) {
  const end = startOfDay(now)
  end.setDate(end.getDate() + 1)
  end.setSeconds(end.getSeconds() - 1) // 今天 23:59:59
  let from = startOfDay(now)
  if (kind === 'today') {
    // 就是今天
  } else if (kind === 'week') {
    const d = new Date(from)
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7)) // 本周一（周一 = 0）
    from = d
  } else if (kind === 'month') {
    const d = new Date(from)
    d.setDate(1)
    from = d
  } else {
    return null
  }
  return { from: stamp(from), to: stamp(end) }
}

/**
 * 这条是不是在范围内（比较的是索引里的 `createTime` 字符串）。
 *
 * ⚠️ **比较 `yyyy-MM-dd HH:mm:ss` 字符串** —— 这个格式的字典序就是时间序
 * ⇒ 字符串比较和日期比较等价。索引里没有时间戳的条目（空串）一律算"不在范围内"。
 *
 * @param {string} createdAt 索引条目上的创建时间
 * @param {{from: string, to: string}|null} bounds 不限时间传 null
 */
export function withinRange(createdAt, bounds) {
  if (!bounds) return true
  const t = String(createdAt || '')
  if (!t) return false
  return t >= bounds.from && t <= bounds.to
}
