/**
 * 图渲染的计数（**只存在内存里，不上报**）。
 *
 * ## 为什么要有
 *
 * kbfig 与 mermaid 一共五处**静默失败**：JSON 解析不出来、内容超限被切、解析出来
 * 但没有可画的形状、mermaid 抛错、mermaid 超时。五处在画面上最多只留一句话
 * （或者只是把词切短了），**一个数字都没有** ⇒ "图到底画对了，还是被截断糊过去了"
 * 只能靠肉眼猜。
 *
 * 其中「**解析成功但内容失控**」是本项目自己判定过的最危险情形
 * （见 `kbFig.js` 的 `LIMIT` 注释：它不报错，只是把版面挤爆，看起来像组件写坏了），
 * 却恰好完全不可观测 ⇒ 这份计数就是为它加的。
 *
 * ## 为什么不做成"上报到后端"
 *
 * 一张正文最多一两张图，为一个计数加一条接口 + 一张表不划算。
 * 累计在内存里、挂在 `window` 上，回测时读一次就够：
 *
 * ```js
 * __kbfigStats
 * // { parseFail: 0, repaired: 2, truncated: 11, renderFail: 0,
 * //   mermaidFail: 1,
 * //   types: { layers: 6, stages: 9, pairs: 4,
 * //            'mermaid:flowchart-v2': 17, 'mermaid:stateDiagram': 3 } }
 * ```
 *
 * ## 四个数分别说明什么（判据，别只看总量）
 *
 * - `truncated` 涨得快 ⇒ **提示词里的字数上限没被遵守**（模型给了 40 字的说明、
 *   配置里写的是 20 字）⇒ 该改提示词或放宽 `LIMIT`，不是改渲染。
 * - `parseFail` 涨得快 ⇒ **JSON 结构没描述清楚**（缺示例 / 说允许注释但其实不允许）。
 * - `repaired` 涨得快 ⇒ 模型在写中文引号或尾逗号 ⇒ 提示词该明说"必须合法 JSON"。
 * - `types` 分布偏斜 ⇒ **选型判据没落地**（比如全落 `layers`、`pairs` 恒为 0），
 *   这时先看提示词的分工边界，别急着加形状。
 *   ⚠️ 键带 `mermaid:` 前缀的那几个是 **mermaid 的原始类型**（`mermaid:flowchart-v2`）——
 *   2026-11-12 之前只计 kbfig，导致「mermaid 侧 77% 都是 flowchart」这种退化
 *   **系统自己发现不了**，只能靠人去数库。
 */
export const figStats = {
  parseFail: 0,
  repaired: 0,
  truncated: 0,
  renderFail: 0,
  mermaidFail: 0,
  types: {}
}

// 浏览器里直接看；Node 侧（回测脚本）import 得到同一份对象，不需要 window
if (typeof window !== 'undefined') window.__kbfigStats = figStats
