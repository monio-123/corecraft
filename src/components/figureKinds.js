/**
 * kbfig v2 图的类型中文标签（全站单一来源）。
 */
export const FIG_KINDS = {
  layers: '分层',
  stages: '阶段',
  flow: '输入产出',
  pairs: '对应',
  tree: '层级',
  anatomy: '结构',
  matrix: '对比',
  memory: '内存',
  sequence: '时序',
  timeline: '演进',
  graph: '拓扑',
  decisionGraph: '流程'
}

/**
 * 根据识别出的 shape 与原始数据返回中文类型标签。
 */
export function figKindOf(shape, data) {
  if (!shape) return ''
  if (shape === 'stages') {
    const hasSide = Array.isArray(data?.steps) && data.steps.some((s) => s && (s.from || s.out))
    return hasSide ? FIG_KINDS.flow : FIG_KINDS.stages
  }
  if (shape === 'graph') {
    const hasDecision = Array.isArray(data?.nodes) && data.nodes.some((n) => n?.kind === 'decision')
    return hasDecision ? FIG_KINDS.decisionGraph : FIG_KINDS.graph
  }
  return FIG_KINDS[shape] || ''
}
