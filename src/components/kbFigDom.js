/**
 * kbfig v2 DOM 布局类图型渲染器：layers / stages / pairs / anatomy / matrix / memory / timeline。
 */
import { el } from './kbCodeFigure'
import { LIMIT, cut } from './kbFigCore'

export function renderLayers(data) {
  const root = el('div', 'kbfig__layers')
  const groups = Array.isArray(data.groups) ? data.groups.slice(0, LIMIT.groups) : []
  groups.forEach((g, idx) => {
    const items = Array.isArray(g?.items) ? g.items.slice(0, LIMIT.items) : []
    const isHl = Boolean(g?.hl) || (groups.length > 1 && idx === 1 && items.some((it) => Array.isArray(it?.tags) && it.tags.length))
    const box = el('div', 'kbfig__group' + (isHl ? ' is-hl' : ''))
    const head = el('div', 'kbfig__group-h')
    head.append(el('span', 'kbfig__group-n', cut(g?.name, LIMIT.name)))
    if (g?.note) head.append(el('span', 'kbfig__group-note', cut(g.note, LIMIT.desc)))
    box.append(head)

    const list = el('div', 'kbfig__items')
    for (const it of items) {
      const cell = el('div', 'kbfig__cell')
      cell.append(el('div', 'kbfig__cell-t', cut(it?.t, LIMIT.title)))
      if (it?.d) cell.append(el('div', 'kbfig__cell-d', cut(it.d, LIMIT.desc)))
      if (Array.isArray(it?.tags) && it.tags.length) {
        const tags = el('div', 'kbfig__tags')
        for (const t of it.tags.slice(0, LIMIT.tags)) tags.append(el('span', 'kbfig__tag', cut(t, LIMIT.name)))
        cell.append(tags)
      }
      list.append(cell)
    }
    if (items.length) box.append(list)
    root.append(box)
  })
  return root
}

function renderHorizontalStages(data) {
  const wrap = el('div', 'kbfig__hwrap')
  const rail = el('div', 'kbfig__hflow')
  data.steps.slice(0, 5).forEach((s, i) => {
    const card = el('div', 'kbfig__hstep' + (s?.hl ? ' is-hl' : ''))
    const top = el('div', 'kbfig__htop')
    top.append(el('span', 'kbfig__hnum', String(i + 1)))
    card.append(top)
    const body = el('div', 'kbfig__hbody')
    body.append(el('div', 'kbfig__ht', cut(s?.t, LIMIT.title)))
    if (s?.d) body.append(el('div', 'kbfig__hd', cut(s.d, LIMIT.desc)))
    if (s?.note) body.append(el('div', 'kbfig__rnote', cut(s.note, LIMIT.desc)))
    card.append(body)
    rail.append(card)
  })
  wrap.append(rail)
  if (data.loop) wrap.append(el('div', 'kbfig__loop', cut(data.loop, 64)))
  return wrap
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function renderRail(data) {
  const root = el('div', 'kbfig__rail')
  const steps = Array.isArray(data.steps) ? data.steps.slice(0, LIMIT.steps) : []
  steps.forEach((s, i) => {
    const row = el('div', 'kbfig__row')
    const sub = Array.isArray(s?.steps) ? s.steps.slice(0, LIMIT.subSteps) : []
    row.append(el('span', 'kbfig__k', sub.length ? `${pad(i + 1)} – ${pad(i + sub.length)}` : pad(i + 1)))
    if (sub.length) {
      const group = el('div', 'kbfig__rsub')
      for (const c of sub) {
        const line = el('div', 'kbfig__rline')
        line.append(el('strong', null, cut(c?.t, LIMIT.title)))
        if (c?.d) line.append(el('em', null, cut(c.d, LIMIT.desc)))
        group.append(line)
      }
      row.append(group)
    } else {
      const body = el('div', 'kbfig__v')
      body.append(el('strong', null, cut(s?.t, LIMIT.title)))
      if (s?.d) body.append(el('em', null, cut(s.d, LIMIT.desc)))
      if (s?.note) body.append(el('span', 'kbfig__rnote', cut(s.note, LIMIT.desc)))
      row.append(body)
    }
    root.append(row)
  })
  if (data.loop) root.append(el('div', 'kbfig__loop', cut(data.loop, 64)))
  return root
}

function renderFlow(data) {
  const root = el('div', 'kbfig__flow')
  const steps = Array.isArray(data.steps) ? data.steps.slice(0, LIMIT.steps) : []
  steps.forEach((s, idx) => {
    const row = el('div', 'kbfig__frow')
    if (s?.from || s?.note) {
      const col = el('div', 'kbfig__fcol')
      if (s.from) {
        const src = el('div', 'kbfig__side kbfig__side--src')
        src.append(el('span', 'kbfig__side-t', cut(s.from, LIMIT.title)))
        col.append(src)
      }
      if (s.note) col.append(el('div', 'kbfig__note', cut(s.note, LIMIT.desc)))
      row.append(col)
    }
    const step = el('div', 'kbfig__step' + (s?.hl ? ' is-hl' : ''))
    step.append(el('span', 'kbfig__fn', String(idx + 1)))
    step.append(el('span', 'kbfig__step-t', cut(s?.t, LIMIT.title)))
    if (s?.d) step.append(el('span', 'kbfig__step-d', cut(s.d, LIMIT.desc)))
    if (Array.isArray(s?.steps) && s.steps.length) {
      step.append(el('span', 'kbfig__step-d', s.steps.map((c) => cut(c?.t, LIMIT.name)).join(' → ')))
    }
    row.append(step)
    if (s?.out) {
      const out = el('div', 'kbfig__side kbfig__side--out')
      out.append(el('span', 'kbfig__side-t', cut(s.out, LIMIT.title)))
      row.append(out)
    }
    root.append(row)
  })
  if (data.loop) root.append(el('div', 'kbfig__loop', cut(data.loop, 64)))
  return root
}

export function renderStages(data) {
  const hasSide = data.steps.some((s) => s && (s.from || s.out))
  if (hasSide) return renderFlow(data)
  const hasNested = data.steps.some((s) => Array.isArray(s?.steps) && s.steps.length)
  if (!hasNested && data.steps.length <= 5) return renderHorizontalStages(data)
  return renderRail(data)
}

export function renderPairs(data) {
  const root = el('div', 'kbfig__pairs')
  if (data.left || data.right) {
    const head = el('div', 'kbfig__prow kbfig__prow--head')
    head.append(el('span', 'kbfig__ph kbfig__ph--l', cut(data.left, LIMIT.name)))
    head.append(el('span', 'kbfig__pd'))
    head.append(el('span', 'kbfig__ph kbfig__ph--r', cut(data.right, LIMIT.name)))
    root.append(head)
  }
  const rows = Array.isArray(data.rows) ? data.rows.slice(0, LIMIT.rows) : []
  for (const row of rows) {
    const line = el('div', 'kbfig__prow')
    line.append(el('span', 'kbfig__pl', cut(row?.l, LIMIT.title)))
    line.append(el('span', 'kbfig__pd'))
    line.append(el('span', 'kbfig__pr', cut(row?.r, LIMIT.desc)))
    root.append(line)
  }
  return root
}

export function renderAnatomy(data) {
  const root = el('div', 'kbanat')
  if (data.center && data.center.t) {
    const c = el('div', 'kbanat__center')
    const head = el('div', 'kbanat__head')
    head.append(el('span', 'kbanat__ct', cut(data.center.t, LIMIT.title)))
    if (data.center.tag) head.append(el('span', 'kbanat__tag', cut(data.center.tag, LIMIT.name)))
    c.append(head)
    if (data.center.d) c.append(el('div', 'kbanat__cd', cut(data.center.d, LIMIT.desc)))
    root.append(c)
  }
  const parts = Array.isArray(data.parts) ? data.parts.slice(0, LIMIT.parts) : []
  const grid = el('div', 'kbanat__grid')
  for (const p of parts) {
    const card = el('div', 'kbanat__part' + (p?.hl ? ' is-hl' : ''))
    card.append(el('div', 'kbanat__pt', cut(p?.t, LIMIT.title)))
    if (p?.d) card.append(el('div', 'kbanat__pd', cut(p.d, LIMIT.desc)))
    grid.append(card)
  }
  if (grid.childElementCount) root.append(grid)
  return root
}

function matrixCellClass(val) {
  const s = String(val || '').trim()
  if (/^(不受影响|是|支持|避免|O\(1\)|安全|推荐)/.test(s)) return 'kbmat__pill is-ok'
  if (/^(变化可见|可能|否|需注意|O\(n\)|阻塞|锁)/.test(s)) return 'kbmat__pill is-warn'
  return ''
}

export function renderMatrix(data) {
  const root = el('div', 'kbmat')
  const cols = Array.isArray(data.cols) ? data.cols.slice(0, LIMIT.cols) : []
  const rows = Array.isArray(data.rows) ? data.rows.slice(0, LIMIT.mRows) : []
  const table = el('table', 'kbmat__table')

  const thead = el('thead')
  const hr = el('tr')
  hr.append(el('th', 'kbmat__th kbmat__th--corner', ''))
  for (const c of cols) hr.append(el('th', 'kbmat__th', cut(c, LIMIT.name)))
  thead.append(hr)
  table.append(thead)

  const tbody = el('tbody')
  for (const r of rows) {
    const tr = el('tr', 'kbmat__row')
    tr.append(el('th', 'kbmat__rn', cut(r?.name ?? r?.l, LIMIT.title)))
    const cells = Array.isArray(r?.cells) ? r.cells : []
    for (let i = 0; i < cols.length; i++) {
      const val = cut(cells[i] ?? '', LIMIT.cell)
      const td = el('td', 'kbmat__td')
      const cls = i === cols.length - 1 ? matrixCellClass(val) : ''
      if (cls) td.append(el('span', cls, val))
      else td.textContent = val
      tr.append(td)
    }
    tbody.append(tr)
  }
  table.append(tbody)
  root.append(table)
  return root
}

export function renderMemory(data) {
  const root = el('div', 'kbmem')
  const stackList = Array.isArray(data.stack) ? data.stack.slice(0, 4) : []
  if (stackList.length) {
    const col = el('div', 'kbmem__stack')
    col.append(el('div', 'kbmem__cap', '栈帧 · 引用'))
    for (const s of stackList) {
      const card = el('div', 'kbmem__scard')
      card.append(el('div', 'kbmem__sname', cut(s?.name, LIMIT.name)))
      if (s?.ref) card.append(el('div', 'kbmem__sref', cut(s.ref, LIMIT.title)))
      col.append(card)
    }
    root.append(col)
  }

  const container = data.container && typeof data.container === 'object'
    ? data.container
    : { name: '堆内存容器', slots: data.slots || [] }
  const heap = el('div', 'kbmem__heap')
  heap.append(el('div', 'kbmem__cap', cut(container.name || '堆内存', LIMIT.title)))
  const slots = Array.isArray(container.slots) ? container.slots.slice(0, LIMIT.slots) : []
  const list = el('div', 'kbmem__slots')
  for (let i = 0; i < slots.length; i++) {
    const sl = slots[i]
    const row = el('div', 'kbmem__srow')
    const box = el('div', 'kbmem__slot' + (sl?.hl ? ` is-${sl.hl}` : ''))
    box.append(el('span', 'kbmem__idx', `[${sl?.index != null ? sl.index : i}]`))
    box.append(el('span', 'kbmem__val', cut(sl?.val, LIMIT.title)))
    row.append(box)

    if (Array.isArray(sl?.chain) && sl.chain.length) {
      const chain = el('div', 'kbmem__chain')
      for (const node of sl.chain.slice(0, 4)) {
        chain.append(el('span', 'kbmem__arrow', '→'))
        const nc = el('div', 'kbmem__node')
        nc.append(el('div', 'kbmem__nt', cut(node?.t, LIMIT.name)))
        if (node?.d) nc.append(el('div', 'kbmem__nd', cut(node.d, LIMIT.desc)))
        chain.append(nc)
      }
      row.append(chain)
    } else if (sl?.tree && typeof sl.tree === 'object') {
      const treeBox = el('div', 'kbmem__treebox')
      treeBox.append(el('span', 'kbmem__arrow', '→'))
      const tMini = el('div', 'kbmem__tmini')
      tMini.append(el('span', 'kbmem__tnode is-root', cut(sl.tree.root || 'Root', LIMIT.name)))
      if (sl.tree.left || sl.tree.right) {
        const sub = el('div', 'kbmem__tkids')
        if (sl.tree.left) sub.append(el('span', 'kbmem__tnode', cut(sl.tree.left, LIMIT.name)))
        if (sl.tree.right) sub.append(el('span', 'kbmem__tnode', cut(sl.tree.right, LIMIT.name)))
        tMini.append(sub)
      }
      treeBox.append(tMini)
      if (sl.tree.note) treeBox.append(el('div', 'kbmem__tnote', cut(sl.tree.note, LIMIT.desc)))
      row.append(treeBox)
    }
    list.append(row)
  }
  heap.append(list)
  root.append(heap)

  const wrap = el('div', 'kbmem-wrap')
  wrap.append(root)
  return wrap
}

export function renderTimeline(data) {
  const events = Array.isArray(data.events) ? data.events.slice(0, LIMIT.events) : []
  if (!events.length) return null
  const root = el('div', 'kbtl')
  const track = el('div', 'kbtl__track')
  for (const ev of events) {
    const item = el('div', 'kbtl__item' + (ev?.hl ? ' is-hl' : ''))
    const top = el('div', 'kbtl__top')
    top.append(el('span', 'kbtl__tag', cut(ev?.tag, LIMIT.name)))
    top.append(el('span', 'kbtl__dot'))
    item.append(top)
    const card = el('div', 'kbtl__card')
    card.append(el('div', 'kbtl__t', cut(ev?.t, LIMIT.title)))
    if (ev?.d) card.append(el('div', 'kbtl__d', cut(ev.d, LIMIT.desc)))
    item.append(card)
    track.append(item)
  }
  root.append(track)
  return root
}
