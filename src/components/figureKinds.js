/**
 * 图的**类型名**（全站单一来源）。
 *
 * ## 为什么要单独一份
 *
 * 「这是哪一种图」有三处要用：图上角那个小标签、`kbFigureStats` 的选型分布、
 * 以及以后写「系统支持哪几类图」的说明。分散写必然漂 —— 加了 `layers` 忘了某处，
 * 界面上就是「有图、没名字」。
 *
 * ## 名字怎么起
 *
 * 只有一条判据：**读者看见标签就知道这张图在回答什么问题**。
 * 所以不按实现分（mermaid / 结构化），也不按形状分（框、菱形、点线），
 * 按「这张图能回答什么」分：
 *
 * | 标签 | 回答什么 | 谁画的 |
 * |---|---|---|
 * | 分层 | 一个整体包含哪几块、块里还有什么 | kbfig `layers` |
 * | 阶段 | 按什么顺序走 | kbfig `stages`（无侧翼） |
 * | 输入产出 | 每一步吃什么、吐什么 | kbfig `stages`（带 `from`/`out`） |
 * | 对应 | 两组东西怎么一一对上 | kbfig `pairs` |
 * | 关系 | 谁跟谁连着（调用 / 依赖） | mermaid `flowchart` |
 * | 流程 | 按什么条件分支走 | mermaid `flowchart`（带菱形） |
 * | 状态 | 有哪几个状态、怎么流转 | mermaid `stateDiagram` |
 * | 时序 | 多个角色按先后交互 | mermaid `sequence` |
 *
 * ## mermaid 的类型键从哪来
 *
 * `mermaid.detectType()` 的返回值，**实测过**（2026-11-12，mermaid 12.2.0）：
 * `graph` 和 `flowchart` 都返回 `flowchart-v2`；`stateDiagram-v2` 返回
 * `stateDiagram`；`sequenceDiagram` 返回 `sequence`；`erDiagram` 返回 `er`。
 * ⇒ 键照着这个表写，别照着「语法关键字」写。
 */

/**
 * `detectType` 返回值 → 标签。
 *
 * <p>没列进来的（mermaid 以后新增的图型）**不显示标签**，而不是硬起个名字：
 * 起错了比不显示更糟 —— 读者会以为那是系统给出的准确分类。
 */
const MERMAID_KIND = {
  'flowchart-v2': '关系',
  stateDiagram: '状态',
  sequence: '时序',
  classDiagram: '类图',
  er: '实体关系',
  gantt: '甘特',
  pie: '占比',
  mindmap: '导图',
  timeline: '时间线',
  journey: '旅程',
  quadrantChart: '象限',
  gitGraph: '分支',
  sankey: '流向',
  xychart: '统计',
  block: '分块',
  packet: '报文',
  info: '信息'
}

/**
 * flowchart 里有没有**菱形**（判断节点）。
 *
 * <p>为什么要分：`flowchart` 在提示词里承担两件事 —— 「谁跟谁连着」和
 * 「带判断分支的流程」。两者都是 `flowchart-v2`，光看类型分不出来，
 * 但读者需要知道自己在看哪一种。
 *
 * <p>`(?!\{)` 是为了排掉六边形 `{{…}}`（那也是节点形状，但不是判断）。
 * 这是**启发式**：命中就标「流程」，没命中标「关系」。判错的代价只是标签不准，
 * 不影响图本身。
 */
const HAS_DIAMOND = /(^|[^{])\{(?!\{)[^{}]*\}/m

/**
 * mermaid 源码 → 类型标签。
 *
 * @param {object} mermaid 已加载的 mermaid 实例（要它导出的 `detectType`）
 * @param {string} text 围栏里的源码
 * @returns {string} 标签；识别不出来返回空串（调用方据此不显示标签）
 */
export function mermaidType(mermaid, text) {
  try {
    return mermaid.detectType(text) || ''
  } catch (e) {
    // 源码残缺（正在输入）时它会抛 ⇒ 没有类型可说，不猜
    return ''
  }
}

/**
 * 类型 → 中文标签（纯函数，不碰 mermaid）。
 *
 * <p>拆出来是给**计数**用的：`types` 统计要的是原始类型键（`flowchart-v2`），
 * 标签要的是「关系 / 流程」。同一个 `detectType` 只该跑一次。
 */
export function kindOf(type, text) {
  if (!type) return ''
  if (type === 'flowchart-v2') return HAS_DIAMOND.test(text) ? '流程' : '关系'
  return MERMAID_KIND[type] || ''
}
