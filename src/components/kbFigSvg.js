/**
 * kbfig v2 矢量 SVG 类图型渲染器：tree / sequence / graph（Dagre）。
 */
import dagre from '@dagrejs/dagre'
import { el } from './kbCodeFigure'
import { LIMIT, appendArrowMarkers, cut, svgEl, textPx } from './kbFigCore'

export function renderTree(data) {
  const wrap = el('div', 'kbtree-wrap')
  let count = 0

  function buildModel(raw, depth) {
    if (!raw || count >= LIMIT.nodes) return null
    count++
    const t = cut(raw.t, LIMIT.title)
    const d = raw.d ? cut(raw.d, LIMIT.desc) : ''
    const tag = raw.tag ? cut(raw.tag, LIMIT.name) : ''
    const kidsRaw = Array.isArray(raw.children) && depth < LIMIT.depth
      ? raw.children.slice(0, LIMIT.children)
      : []
    const children = []
    for (const kr of kidsRaw) {
      const m = buildModel(kr, depth + 1)
      if (m) children.push(m)
    }
    const titleW = textPx(t, 13.5, 8) + (tag && depth === 0 ? textPx(tag, 11, 6.5) + 26 : 0)
    const descW = d ? textPx(d, 11, 6.2) : 0
    const tagW = tag && depth > 0 ? textPx(tag, 11, 6.2) + 18 : 0
    const w = Math.max(96, Math.min(220, Math.ceil(Math.max(titleW, descW, tagW) + 26)))
    const h = depth === 0 ? (d ? 54 : 38) : (32 + (d ? 16 : 0) + (tag ? 16 : 0))
    return { t, d, tag, depth, w, h, children, subW: w, x: 0, y: 0 }
  }

  const root = buildModel(data.root, 0)
  if (!root) return null

  const GAP_X = 16
  const GAP_Y = 32
  const PAD = 16

  function measure(n) {
    if (!n.children.length) {
      n.subW = n.w
      return n.subW
    }
    let sum = 0
    n.children.forEach((c, i) => {
      sum += measure(c) + (i > 0 ? GAP_X : 0)
    })
    n.subW = Math.max(n.w, sum)
    return n.subW
  }
  measure(root)

  let maxY = 0
  function place(n, leftX, topY) {
    n.x = leftX + n.subW / 2
    n.y = topY
    maxY = Math.max(maxY, topY + n.h)
    if (!n.children.length) return
    const totalKids = n.children.reduce((acc, c, i) => acc + c.subW + (i > 0 ? GAP_X : 0), 0)
    let cx = n.x - totalKids / 2
    for (const c of n.children) {
      place(c, cx, topY + n.h + GAP_Y)
      cx += c.subW + GAP_X
    }
  }
  place(root, PAD, PAD)

  const totalW = Math.max(360, root.subW + PAD * 2)
  const totalH = maxY + PAD
  const svg = svgEl('svg', {
    class: 'kbtree-svg',
    viewBox: `0 0 ${totalW} ${totalH}`,
    width: totalW,
    height: totalH
  })

  function drawEdges(n) {
    for (const c of n.children) {
      const x1 = n.x
      const y1 = n.y + n.h
      const x2 = c.x
      const y2 = c.y
      const my = (y1 + y2) / 2
      svg.append(svgEl('path', {
        d: `M ${x1} ${y1} C ${x1} ${my}, ${x2} ${my}, ${x2} ${y2}`,
        fill: 'none',
        stroke: 'var(--cc-border-strong)',
        'stroke-width': '1.4'
      }))
      drawEdges(c)
    }
  }
  drawEdges(root)

  function drawNodes(n) {
    const g = svgEl('g')
    const x = n.x - n.w / 2
    const y = n.y
    const isRoot = n.depth === 0
    g.append(svgEl('rect', {
      x,
      y,
      width: n.w,
      height: n.h,
      rx: isRoot ? 10 : 8,
      fill: isRoot ? 'var(--cc-primary-soft)' : 'var(--cc-surface)',
      stroke: isRoot ? 'var(--cc-primary)' : 'var(--cc-border-strong)',
      'stroke-width': isRoot ? '1.5' : '1.1'
    }))

    if (isRoot && n.tag) {
      const tw = textPx(n.t, 13.5, 8)
      const pw = Math.ceil(textPx(n.tag, 10.5, 6.2) + 14)
      const groupW = tw + 8 + pw
      const tx = n.x - groupW / 2
      const ty = y + (n.d ? 21 : n.h / 2 + 5)
      g.append(svgEl('text', {
        x: tx, y: ty,
        fill: 'var(--cc-text-1)', 'font-size': '13px', 'font-weight': '600'
      }, n.t))
      const px = tx + tw + 8
      g.append(svgEl('rect', {
        x: px, y: ty - 13, width: pw, height: 18, rx: 9,
        fill: 'var(--cc-surface)', stroke: 'var(--cc-primary-border)', 'stroke-width': '1'
      }))
      g.append(svgEl('text', {
        x: px + pw / 2, y: ty - 0.5, 'text-anchor': 'middle',
        fill: 'var(--cc-primary)', 'font-size': '10.5px', 'font-weight': '600'
      }, n.tag))
    } else {
      const ty = y + (n.d || n.tag ? 19 : n.h / 2 + 4.5)
      g.append(svgEl('text', {
        x: n.x, y: ty, 'text-anchor': 'middle',
        fill: 'var(--cc-text-1)', 'font-size': '12.5px', 'font-weight': '600'
      }, n.t))
    }

    if (n.d) {
      g.append(svgEl('text', {
        x: n.x, y: y + (isRoot ? 40 : 36), 'text-anchor': 'middle',
        fill: 'var(--cc-text-3)', 'font-size': '10.8px'
      }, n.d))
    }
    if (!isRoot && n.tag) {
      g.append(svgEl('text', {
        x: n.x, y: y + (n.d ? 53 : 36), 'text-anchor': 'middle',
        fill: 'var(--cc-primary)', 'font-size': '10.5px', 'font-weight': '500'
      }, '↳ ' + n.tag))
    }
    svg.append(g)
    n.children.forEach(drawNodes)
  }
  drawNodes(root)

  const scroll = el('div', 'kbtree-scroll')
  scroll.append(svg)
  wrap.append(scroll)
  return wrap
}

export function renderSequence(data) {
  const parts = Array.isArray(data.participants) ? data.participants.slice(0, LIMIT.participants) : []
  const msgs = Array.isArray(data.messages) ? data.messages.slice(0, LIMIT.messages) : []
  if (!parts.length || !msgs.length) return null

  const COL_W = Math.max(130, Math.min(185, Math.floor(640 / parts.length)))
  const PAD_X = 24
  const TOP_H = 44
  const ROW_H = 42
  const W = PAD_X * 2 + (parts.length - 1) * COL_W + 120
  const H = TOP_H + msgs.length * ROW_H + 22

  const xMap = new Map()
  parts.forEach((p, i) => xMap.set(p.id, PAD_X + 60 + i * COL_W))

  const svg = svgEl('svg', {
    class: 'kbseq-svg',
    viewBox: `0 0 ${W} ${H}`,
    width: W,
    height: H
  })
  const mkColor = {
    sync: 'var(--cc-primary)',
    async: 'var(--cc-text-3)',
    wait: 'var(--cc-warning)',
    ok: 'var(--cc-success, #34a853)'
  }
  appendArrowMarkers(svg, 'seq-mk-', mkColor, '6.5')

  for (const p of parts) {
    const cx = xMap.get(p.id)
    svg.append(svgEl('line', {
      x1: cx, y1: TOP_H, x2: cx, y2: H - 10,
      stroke: 'var(--cc-border-strong)', 'stroke-width': '1.2', 'stroke-dasharray': '4 4'
    }))
    const boxW = Math.max(88, Math.min(130, textPx(p.name, 13, 7.5) + 24))
    svg.append(svgEl('rect', {
      x: cx - boxW / 2, y: 8, width: boxW, height: 30, rx: 7,
      fill: p.hl ? 'var(--cc-primary-soft)' : 'var(--cc-surface)',
      stroke: p.hl ? 'var(--cc-primary)' : 'var(--cc-border-strong)',
      'stroke-width': p.hl ? '1.4' : '1.1'
    }))
    svg.append(svgEl('text', {
      x: cx, y: 27, 'text-anchor': 'middle',
      fill: p.hl ? 'var(--cc-primary)' : 'var(--cc-text-1)',
      'font-size': '12px', 'font-weight': '600'
    }, cut(p.name, LIMIT.name)))
  }

  msgs.forEach((m, i) => {
    const x1 = xMap.get(m.from)
    const x2 = xMap.get(m.to)
    if (x1 == null || x2 == null) return
    const y = TOP_H + 26 + i * ROW_H
    const kind = mkColor[m.kind] ? m.kind : 'sync'
    const color = mkColor[kind]
    const dashed = kind === 'async' || kind === 'wait'
    if (x1 === x2) {
      svg.append(svgEl('path', {
        d: `M ${x1} ${y - 6} h 28 v 14 h -28`,
        fill: 'none', stroke: color, 'stroke-width': '1.5',
        'stroke-dasharray': dashed ? '4 3' : null,
        'marker-end': `url(#seq-mk-${kind})`
      }))
    } else {
      const dir = x2 > x1 ? 1 : -1
      svg.append(svgEl('line', {
        x1, y1: y, x2: x2 - dir * 3, y2: y,
        stroke: color, 'stroke-width': '1.5',
        'stroke-dasharray': dashed ? '5 3' : null,
        'marker-end': `url(#seq-mk-${kind})`
      }))
    }
    const label = cut(m.text, LIMIT.title)
    const mx = x1 === x2 ? x1 + 16 : (x1 + x2) / 2
    svg.append(svgEl('text', {
      x: mx, y: y - 7, 'text-anchor': x1 === x2 ? 'start' : 'middle',
      fill: kind === 'sync' ? 'var(--cc-text-2)' : color,
      'font-size': '11.5px', 'font-weight': '500'
    }, label))
  })

  const wrap = el('div', 'kbseq-wrap')
  const scroll = el('div', 'kbseq-scroll')
  scroll.append(svg)
  wrap.append(scroll)
  return wrap
}

export function renderGraph(data) {
  const nodesRaw = Array.isArray(data.nodes) ? data.nodes.slice(0, LIMIT.gNodes) : []
  const edgesRaw = Array.isArray(data.edges) ? data.edges.slice(0, LIMIT.gEdges) : []
  if (!nodesRaw.length) return null

  const dir = String(data.dir || 'LR').toUpperCase() === 'TD' ? 'TB' : 'LR'
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: dir, nodesep: 38, ranksep: 52, marginx: 18, marginy: 18 })
  g.setDefaultEdgeLabel(() => ({}))

  const nodeMap = new Map()
  for (const n of nodesRaw) {
    if (!n || !n.id) continue
    const label = cut(n.label, LIMIT.title)
    const sub = n.sub ? cut(n.sub, LIMIT.desc) : ''
    const kind = n.kind || 'step'
    const prefix = kind === 'decision' ? '◇ ' : ''
    const w = Math.max(86, Math.min(210, Math.ceil(Math.max(textPx(prefix + label, 13, 7.6), textPx(sub, 11, 6.4)) + 30)))
    const h = sub ? 52 : 36
    const meta = { id: String(n.id), label, sub, kind, hl: n.hl || '', w, h }
    nodeMap.set(meta.id, meta)
    g.setNode(meta.id, { width: w, height: h })
  }

  const edgeList = []
  for (const e of edgesRaw) {
    if (!e || !nodeMap.has(String(e.from)) || !nodeMap.has(String(e.to))) continue
    const label = e.label ? cut(e.label, LIMIT.name) : ''
    const lw = label ? Math.ceil(textPx(label, 11.5, 6.8) + 16) : 0
    edgeList.push({
      from: String(e.from),
      to: String(e.to),
      label,
      style: e.style === 'dashed' ? 'dashed' : 'solid',
      hl: e.hl || ''
    })
    g.setEdge(String(e.from), String(e.to), { label, width: lw, height: label ? 20 : 0, labelpos: 'c' })
  }

  dagre.layout(g)
  const graphInfo = g.graph()
  const W = Math.max(320, Math.ceil(graphInfo.width || 360))
  const H = Math.max(120, Math.ceil(graphInfo.height || 160))

  const svg = svgEl('svg', {
    class: 'kbgraph-svg',
    viewBox: `0 0 ${W} ${H}`,
    width: W,
    height: H
  })

  const colorOf = {
    '': 'var(--cc-border-strong)',
    primary: 'var(--cc-primary)',
    ok: 'var(--cc-success, #34a853)',
    warn: 'var(--cc-warning)'
  }
  appendArrowMarkers(svg, 'g-mk-', Object.fromEntries(
    Object.entries(colorOf).map(([k, c]) => [k || 'def', c])
  ), '6')

  edgeList.forEach((e) => {
    const ge = g.edge(e.from, e.to)
    if (!ge || !ge.points?.length) return
    const pts = ge.points
    let d = `M ${pts[0].x} ${pts[0].y}`
    if (pts.length === 2) {
      d += ` L ${pts[1].x} ${pts[1].y}`
    } else {
      for (let i = 1; i < pts.length - 1; i++) {
        const xc = (pts[i].x + pts[i + 1].x) / 2
        const yc = (pts[i].y + pts[i + 1].y) / 2
        d += ` Q ${pts[i].x} ${pts[i].y}, ${xc} ${yc}`
      }
      const last = pts[pts.length - 1]
      d += ` L ${last.x} ${last.y}`
    }
    const stroke = colorOf[e.hl] || colorOf['']
    svg.append(svgEl('path', {
      d, fill: 'none', stroke,
      'stroke-width': e.hl ? '1.8' : '1.3',
      'stroke-dasharray': e.style === 'dashed' ? '5 4' : null,
      'marker-end': `url(#g-mk-${e.hl || 'def'})`
    }))

    if (e.label) {
      const mid = pts[Math.floor(pts.length / 2)]
      const lx = ge.x != null ? ge.x : mid.x
      const ly = ge.y != null ? ge.y : mid.y
      const pw = Math.ceil(textPx(e.label, 11, 6.4) + 14)
      svg.append(svgEl('rect', {
        x: lx - pw / 2, y: ly - 10, width: pw, height: 20, rx: 10,
        fill: 'var(--cc-surface)',
        stroke: e.hl ? stroke : 'var(--cc-border-strong)',
        'stroke-width': '1'
      }))
      svg.append(svgEl('text', {
        x: lx, y: ly + 3.8, 'text-anchor': 'middle',
        fill: e.hl ? stroke : 'var(--cc-text-2)',
        'font-size': '10.8px', 'font-weight': '600'
      }, e.label))
    }
  })

  for (const [id, meta] of nodeMap) {
    const gn = g.node(id)
    if (!gn) continue
    const x = gn.x - meta.w / 2
    const y = gn.y - meta.h / 2
    const isDec = meta.kind === 'decision'
    const isEnd = meta.kind === 'end'
    const hlColor = meta.hl === 'ok'
      ? 'var(--cc-success, #34a853)'
      : meta.hl === 'warn'
        ? 'var(--cc-warning)'
        : meta.hl === 'primary'
          ? 'var(--cc-primary)'
          : null

    const fill = hlColor
      ? (meta.hl === 'ok'
        ? 'var(--cc-success-soft, rgba(52,168,83,0.14))'
        : meta.hl === 'warn'
          ? 'var(--cc-warning-soft, rgba(242,194,107,0.14))'
          : 'var(--cc-primary-soft)')
      : isDec
        ? 'var(--cc-surface-2)'
        : 'var(--cc-surface)'
    const stroke = hlColor || (isDec ? 'var(--cc-primary-border)' : 'var(--cc-border-strong)')
    const rx = isDec || isEnd ? meta.h / 2 : 8

    svg.append(svgEl('rect', {
      x, y, width: meta.w, height: meta.h, rx,
      fill, stroke, 'stroke-width': hlColor ? '1.6' : '1.2'
    }))

    if (meta.kind === 'store') {
      svg.append(svgEl('line', {
        x1: x + 6, y1: y + 6, x2: x + meta.w - 6, y2: y + 6,
        stroke: stroke, 'stroke-width': '1', opacity: '0.6'
      }))
    }

    const titleText = (isDec ? '◇ ' : '') + meta.label
    svg.append(svgEl('text', {
      x: gn.x, y: gn.y + (meta.sub ? -3 : 4.2), 'text-anchor': 'middle',
      fill: hlColor || 'var(--cc-text-1)', 'font-size': '12.5px', 'font-weight': '600'
    }, titleText))

    if (meta.sub) {
      svg.append(svgEl('text', {
        x: gn.x, y: gn.y + 14, 'text-anchor': 'middle',
        fill: 'var(--cc-text-3)', 'font-size': '10.8px'
      }, meta.sub))
    }
  }

  const wrap = el('div', 'kbgraph-wrap')
  const scroll = el('div', 'kbgraph-scroll')
  scroll.append(svg)
  wrap.append(scroll)
  return wrap
}
