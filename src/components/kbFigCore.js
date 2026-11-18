/**
 * kbfig v2 公共协议基础设施：限额表、安全文本裁剪、SVG 辅助、JSON 宽松解析、形状识别。
 */
import { figStats } from './kbFigureStats'

export const LANG = 'kbfig'
const SVG_NS = 'http://www.w3.org/2000/svg'

export const LIMIT = {
  steps: 8,
  subSteps: 8,
  groups: 5,
  items: 6,
  tags: 6,
  rows: 8,
  title: 36,
  desc: 52,
  name: 18,
  nodes: 16,
  children: 5,
  depth: 3,
  parts: 6,
  cols: 4,
  mRows: 6,
  cell: 36,
  gNodes: 12,
  gEdges: 20,
  participants: 6,
  messages: 12,
  events: 6,
  slots: 8
}

export function cut(value, max) {
  const s = String(value == null ? '' : value).trim()
  if (s.length <= max) return s
  figStats.truncated++
  return s.slice(0, max) + '…'
}

export function svgEl(tag, attrs = {}, text = null) {
  const n = document.createElementNS(SVG_NS, tag)
  for (const [k, v] of Object.entries(attrs)) {
    if (v != null) n.setAttribute(k, String(v))
  }
  if (text != null) n.textContent = String(text)
  return n
}

export function textPx(s, pxPerCjk = 13, pxPerAscii = 7.6) {
  let w = 0
  for (const ch of String(s || '')) {
    w += ch.charCodeAt(0) > 127 ? pxPerCjk : pxPerAscii
  }
  return w
}

export function parseFig(text) {
  const raw = String(text || '').trim()
  if (!raw) return null
  const fenced = /^```[a-z]*\s*\n([\s\S]*?)\n?```$/i.exec(raw)
  const body = fenced ? fenced[1] : raw
  try {
    return JSON.parse(body)
  } catch {
    /* fallback repair */
  }
  try {
    const repaired = body
      .replace(/[\u201c\u201d]/g, '"')
      .replace(/,\s*([\]}])/g, '$1')
    const data = JSON.parse(repaired)
    figStats.repaired++
    return data
  } catch {
    figStats.parseFail++
    return null
  }
}

export function appendArrowMarkers(svg, idPrefix, colorMap, size) {
  const defs = svgEl('defs')
  for (const [k, color] of Object.entries(colorMap)) {
    const m = svgEl('marker', {
      id: idPrefix + k,
      viewBox: '0 0 10 10',
      refX: '8.5',
      refY: '5',
      markerWidth: size,
      markerHeight: size,
      orient: 'auto-start-reverse'
    })
    m.append(svgEl('path', { d: 'M 0 1.5 L 9 5 L 0 8.5 z', fill: color }))
    defs.append(m)
  }
  svg.append(defs)
}

export function shapeOf(data) {
  if (!data || typeof data !== 'object') return null
  const t = String(data.type || '').trim().toLowerCase()
  const known = ['pairs', 'layers', 'stages', 'tree', 'anatomy', 'matrix', 'memory', 'sequence', 'timeline', 'graph']
  if (known.includes(t)) return t
  if (Array.isArray(data.nodes) && Array.isArray(data.edges)) return 'graph'
  if (Array.isArray(data.participants) && Array.isArray(data.messages)) return 'sequence'
  if (Array.isArray(data.events)) return 'timeline'
  if (data.container || Array.isArray(data.slots)) return 'memory'
  if (data.root && typeof data.root === 'object') return 'tree'
  if (Array.isArray(data.parts)) return 'anatomy'
  if (Array.isArray(data.cols) && Array.isArray(data.rows)) return 'matrix'
  if (Array.isArray(data.rows)) return 'pairs'
  if (Array.isArray(data.groups)) return 'layers'
  if (Array.isArray(data.steps)) return 'stages'
  return null
}
