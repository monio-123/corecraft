/**
 * 结构化图（```kbfig 代码块）的渲染：**模型只填内容，布局归组件**。
 *
 * ## 为什么要有这条路
 *
 * mermaid 的天花板不在配色，在**布局器**：`graph TD` 走 dagre 自动布局，
 * 节点只能是方块 + 箭头 + 文字标签，视觉层级全靠人肉在标签里表达。
 * 所以「一个整体包含哪几块」「每一步吃什么吐什么」「两组东西一一对应」
 * 这类内容，mermaid 要么画不了，要么画出来是一团长标签。
 *
 * 用户 2026-11-09 的原话：「生成的内容对于某些内容生成的图的效果很差，
 * 优美质感基本为 0，且表达效果也不好」。换肤解决了配色，布局得靠这条路。
 *
 * ## 为什么不让模型写 HTML
 *
 * 四个硬问题，都不是理论问题：
 * - **模型不会摆位置**：手写 HTML 图等于让它算宽度、坐标、换行，最容易出的
 *   就是节点重叠、文字溢出、手机上炸；mermaid 的核心价值恰恰是「布局交给布局器」。
 * - **宽度是活的**：图区宽度随窗口变（有断点、有沉浸模式），写死 px 会溢出，
 *   写百分比会塌。
 * - **安全与编辑**：raw HTML 进 ProseMirror 要么被当纯文本、要么得 sanitize
 *   并改 schema；编辑时是一坨不可读的标签。
 * - **失败是静默的**：mermaid 写错会抛错，我们有「图没画出来」；HTML 写歪
 *   只会显示成一张"排版怪异的卡片"，用户以为内容本来就长这样。
 *
 * ⇒ 模型输出 **JSON（只含语义）**，样式与布局由本文件决定。这样同时拿到：
 * HTML 的表现力、mermaid 的可靠性、失败可判定、文字可选中可搜索。
 *
 * ## 三种形状，形状由**数据**决定（2026-11-12 补 pairs）
 *
 * - `layers` —— 分层 / 包含：「一个整体分成哪几块，块里还有什么」；
 * - `stages` —— 顺序 / 阶段：「按什么顺序走」，带 `from`/`out` 时换三列布局；
 * - `pairs` —— **两组一一对应**：「概念 ↔ 说明」「旧方案 ↔ 新方案」。
 *
 * ⚠️ **`type` 只是首选线索，不是唯一入口**：写歪或漏写时按字段形状认
 * （`rows` → pairs / `groups` → layers / `steps` → stages）。理由不是"兜底",
 * 是这三条字段名本身就是语义 —— **一个拼错的 `type` 不该把一张画得出来的图
 * 报废成"图没画出来"**（原来 unknown type 直接走降级）。
 *
 * ⚠️ **布局也不让模型选**：`stages` 有没有侧翼由数据决定，`layers` 每组只有一个
 * 条目时横排；模型少一个选项，就少一次选错的机会（这是本文件一贯的原则）。
 *
 * ## 主题为什么不需要重画
 *
 * 这里产出的是普通 DOM，颜色全部走 `--cc-*` CSS 变量 ⇒ 切主题时浏览器自己
 * 重算，不需要 mermaid 那套 `MutationObserver` 重画机制（那是给写死颜色的 SVG 用的）。
 *
 * ## 降级链
 *
 * 解析失败 → 图区写一句「图没画出来：JSON 有误」，**源码始终留在下面可改**
 * （和 mermaid 一致）。绝不静默留空，也绝不塞一张错误卡片。
 */
import { createCodeFigureView, el } from './kbCodeFigure'
import { figStats } from './kbFigureStats'

const LANG = 'kbfig'

/**
 * 渲染层的硬截断。
 *
 * <p>**为什么不信提示词**：提示词里写着「标题 ≤14 字、步骤 ≤6 个」，但那是
 * 「希望」；模型偶尔会给 12 个步骤、80 字说明。**「解析成功但内容失控」比
 * 「解析失败」更危险** —— 它不报错，只是把排版挤爆，看起来像是组件写坏了。
 * ⇒ 这里自己兜底：超了截断加省略号，至少版面是干净的。
 */
const LIMIT = {
  steps: 8,
  subSteps: 8,
  groups: 5,
  items: 6,
  tags: 6,
  rows: 8,
  title: 20,
  desc: 44,
  name: 12
}

/**
 * 超长截断（含省略号），并保证一定返回字符串。
 *
 * <p>⚠️ **每次真截断都记一笔**（`figStats.truncated`）：截断是"解析成功但内容失控"
 * 的唯一痕迹，而它在画面上只表现为一个省略号 —— 不计数就永远不知道它发生了多少次。
 */
function cut(value, max) {
  const s = String(value == null ? '' : value).trim()
  if (s.length <= max) return s
  figStats.truncated++
  return s.slice(0, max) + '…'
}

/**
 * 解析代码块内容成图数据。
 *
 * <p>模型有三种常见的坏法，这里做**最小修复**后重试一次（不做猜测性重构）：
 * ① 整段被额外的 ``` 包了一层；② 用了中文/智能引号；③ 尾随逗号。
 * 修不好就判定失败，走降级。
 *
 * @param {string} text 代码块原文
 * @returns {object|null} 图数据；解析不了返回 null
 */
function parseFig(text) {
  const raw = String(text || '').trim()
  if (!raw) return null

  // 有的模型会把围栏也写进代码块里
  const fenced = /^```[a-z]*\s*\n([\s\S]*?)\n?```$/i.exec(raw)
  const body = fenced ? fenced[1] : raw

  try {
    return JSON.parse(body)
  } catch (e) {
    /* 落到下面的修复重试 */
  }
  try {
    const repaired = body
      .replace(/[\u201c\u201d]/g, '"')
      .replace(/,\s*([\]}])/g, '$1')
    const data = JSON.parse(repaired)
    // 救回来了：记一笔 —— 涨得快说明提示词那边该明说"必须合法 JSON"
    figStats.repaired++
    return data
  } catch (e) {
    figStats.parseFail++
    return null
  }
}

/* ============================================================
 * layers：分层 / 包含
 * ============================================================ */

/**
 * @param {object} data `{ title, groups: [{ name, note, items: [{ t, d, tags }] }] }`
 */
function renderLayers(data) {
  const root = el('div', 'kbfig__layers')
  const groups = Array.isArray(data.groups) ? data.groups.slice(0, LIMIT.groups) : []

  for (const g of groups) {
    const box = el('div', 'kbfig__group')
    const head = el('div', 'kbfig__group-h')
    head.append(el('span', 'kbfig__group-n', cut(g?.name, LIMIT.name)))
    if (g?.note) head.append(el('span', 'kbfig__group-note', cut(g.note, LIMIT.desc)))
    box.append(head)

    const list = el('div', 'kbfig__items')
    const items = Array.isArray(g?.items) ? g.items.slice(0, LIMIT.items) : []
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
  }
  return root
}

/* ============================================================
 * stages：顺序（可选：分组子步骤 / 左右侧翼）
 * ============================================================ */

/**
 * 纵向轨道（纯顺序）。子步骤用虚线框包起来，表示「这几个同属一个阶段」——
 * 这是 mermaid 画不出来的（它只能把 7 个步骤平铺成 7 个箭头）。
 */
function renderRail(data) {
  const root = el('div', 'kbfig__rail')
  const steps = Array.isArray(data.steps) ? data.steps.slice(0, LIMIT.steps) : []

  steps.forEach((s, i) => {
    const row = el('div', 'kbfig__row')
    const sub = Array.isArray(s?.steps) ? s.steps.slice(0, LIMIT.subSteps) : []
    row.append(el('span', 'kbfig__k', sub.length ? `${pad(i + 1)} – ${pad(i + sub.length)}` : pad(i + 1)))

    if (sub.length) {
      const box = el('div', 'kbfig__group-body')
      const h = el('div', 'kbfig__group-n', cut(s.t, LIMIT.title))
      if (s.d) h.append(el('span', 'kbfig__group-note', cut(s.d, LIMIT.desc)))
      box.append(h)
      for (const c of sub) {
        const line = el('div', 'kbfig__sub')
        line.append(el('b', null, cut(c?.t, LIMIT.title)))
        if (c?.d) line.append(el('em', null, cut(c.d, LIMIT.desc)))
        box.append(line)
      }
      row.append(box)
    } else {
      const body = el('div', 'kbfig__body')
      body.append(el('b', null, cut(s?.t, LIMIT.title)))
      if (s?.d) body.append(el('em', null, cut(s.d, LIMIT.desc)))
      row.append(body)
    }
    root.append(row)
  })
  return root
}

/**
 * 主干 + 侧翼。三列 grid：左来源 / 中步骤 / 右产出。
 *
 * <p>**为什么这不是另一种图型**：骨架仍然是「顺序」，只是每步多了可选
 * 的 `from` / `out` / `note`。所以数据上仍是一个 `type: "stages"`，
 * 只是渲染时换布局 —— 模型少一个选项，就少一次选错的机会。
 */
function renderFlow(data) {
  const root = el('div', 'kbfig__flow')
  const steps = Array.isArray(data.steps) ? data.steps.slice(0, LIMIT.steps) : []

  for (const s of steps) {
    const row = el('div', 'kbfig__frow')

    // 左列：来源（可能没有）；旁注挂它下面，作为同一个整体垂直居中
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

    // 中列：步骤本体
    const step = el('div', 'kbfig__step' + (s?.hl ? ' is-hl' : ''))
    step.append(el('span', 'kbfig__fn', String(countSoFar(root) + 1)))
    step.append(el('span', 'kbfig__step-t', cut(s?.t, LIMIT.title)))
    if (s?.d) step.append(el('span', 'kbfig__step-d', cut(s.d, LIMIT.desc)))
    // flow 模式下的嵌套：压成一行小字（三列布局放不下嵌套框）
    if (Array.isArray(s?.steps) && s.steps.length) {
      step.append(el('span', 'kbfig__step-d', s.steps.map(c => cut(c?.t, LIMIT.name)).join(' → ')))
    }
    row.append(step)

    // 右列：产出
    if (s?.out) {
      const out = el('div', 'kbfig__side kbfig__side--out')
      out.append(el('span', 'kbfig__side-t', cut(s.out, LIMIT.title)))
      row.append(out)
    }
    root.append(row)
  }

  if (data.cap) root.append(el('div', 'kbfig__cap', cut(data.cap, 60)))
  return root
}

/** 已渲染的行数（给 flow 的序号用；flow 里步骤不做分组，序号就是行号） */
function countSoFar(root) {
  return root.childElementCount
}

/** 两位序号 */
function pad(n) {
  return String(n).padStart(2, '0')
}

/* ============================================================
 * pairs：两组一一对应
 * ============================================================ */

/**
 * 左 A ↔ 右 B 的对齐列表（左侧右对齐、右侧左对齐，中轴一条虚线把每行钉在一起）。
 *
 * <p>**为什么不能塞进 layers / stages**：这类内容（概念 ↔ 作用、旧方案 ↔ 新方案、
 * 状态码 ↔ 语义）的语义就是"这一行两边的两个词是同一个东西的两面"。
 * `layers` 是"包含"（嵌套关系），`stages` 是"先后"（时间关系）——
 * 硬塞进去的结果是一列框、每框一个条目，读者看不出左右是配对的。
 *
 * <p>**为什么不是 markdown 表格**：表格有竖线、有斑马纹，读起来是"数据清单"；
 * 这里要的是"两个词之间的关系"，所以只用一条中轴虚线 + 行内结点，
 * 边框一条都不给（比 layers 的框轻一档）。
 *
 * <p>**表头只在给了组名时画**：没给 `left`/`right` 就不画一行空表头 ——
 * 宁可少一行，也不要出现一个没有文字的格子。
 *
 * @param {object} data `{ left, right, rows: [{ l, r }], cap }`
 */
function renderPairs(data) {
  const root = el('div', 'kbfig__pairs')

  // 组名（如「概念 / 作用」）：两列表头缺一不可读，所以整行要么都有、要么都不画
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
    // 中轴上的小圆点：每行一个，让眼睛知道"这两个词是一组"
    line.append(el('span', 'kbfig__pd'))
    line.append(el('span', 'kbfig__pr', cut(row?.r, LIMIT.desc)))
    root.append(line)
  }

  if (data.cap) root.append(el('div', 'kbfig__cap', cut(data.cap, 60)))
  return root
}

/* ============================================================
 * 视图
 * ============================================================ */

/**
 * 认形状：`type` 优先；认不出来（漏写 / 拼错 / 大小写）就**按字段形状认**。
 *
 * <p>⚠️ 这**不是"猜"**。猜是凭风格补齐缺失的信息；这里三选一的依据全在数据里 ——
 * `rows` / `groups` / `steps` 这三个字段名本身就是语义，模型的意思不会因为
 * 它把 `type` 写成 `pair` 就消失。原来的写法对这类数据直接降级成「图没画出来」，
 * 用户看到一张空图 + 一坨 JSON，而内容其实是完整的。
 *
 * @returns {'pairs'|'layers'|'stages'|null}
 */
function shapeOf(data) {
  if (!data || typeof data !== 'object') return null
  const t = String(data.type || '').trim().toLowerCase()
  if (t === 'pairs' || t === 'layers' || t === 'stages') return t
  if (Array.isArray(data.rows)) return 'pairs'
  if (Array.isArray(data.groups)) return 'layers'
  if (Array.isArray(data.steps)) return 'stages'
  return null
}

/** 把图数据渲染成 DOM；数据不合法返回 null */
function renderFig(data) {
  const shape = shapeOf(data)
  if (!shape) return null
  const filled = (list) => Array.isArray(list) && list.length > 0
  let node = null
  // 类型标签和图一起返回：**判形状的地方只有一个**，别让标签另写一遍判断
  // （否则加了形状、标签没跟上，界面上就是「有图、没名字」）
  let kind = ''
  if (shape === 'pairs') {
    node = filled(data.rows) ? renderPairs(data) : null
    kind = '对应'
  } else if (shape === 'layers') {
    node = filled(data.groups) ? renderLayers(data) : null
    kind = '分层'
  } else if (filled(data.steps)) {
    const stages = renderStages(data)
    node = stages.node
    kind = stages.kind
  }
  if (!node) return null
  // 记选型分布：某一类恒为 0、或全落同一类 ⇒ 先查提示词的分工判据，别急着加形状
  figStats.types[shape] = (figStats.types[shape] || 0) + 1
  return { node, kind }
}

/**
 * `stages` 的两种布局：**由数据决定，不让模型选**。
 *
 * <p>有任何一个步骤带侧翼（`from` / `out`）⇒ 三列（左来源 / 中步骤 / 右产出）；
 * 否则纵向轨道。模型少一个选项，就少一次选错的机会。
 *
 * <p>标签跟着布局走：三列讲的是「每一步吃什么、吐什么」，轨道讲的是「按什么顺序」。
 * 同一种 `type`，读者看到的其实是两种图。
 *
 * @returns {{node: HTMLElement|null, kind: string}}
 */
function renderStages(data) {
  const hasSide = data.steps.some(s => s && (s.from || s.out))
  return hasSide
    ? { node: renderFlow(data), kind: '输入产出' }
    : { node: renderRail(data), kind: '阶段' }
}

/**
 * 一个 kbfig 代码块的视图。结构与 mermaid 一致：**图在上、源码在下**。
 *
 * <p>**骨架是共用的**（`kbCodeFigure.js`）：DOM 结构、「源码」开关、
 * 缓存与作废判定、画失败的表现都在那里一份 ⇒ 这里只提供"怎么把 JSON 画成图"。
 *
 * @param {import('@milkdown/prose/model').Node} node code_block 节点
 */
export function figView(node) {
  return createCodeFigureView(node, {
    prefix: 'kbfig',
    paint: ({ canvas, text }) => {
      const data = parseFig(text)
      const rendered = renderFig(data)
      if (!rendered) {
        // 解析失败已经记进 parseFail 了，这里只记「解析出来了、但没有可画的形状」
        // —— 两个数混在一起就分不清该改提示词还是该加形状
        if (data) figStats.renderFail++
        // 和 mermaid 同一套话术：说清为什么没画出来，源码就在下面
        return {
          error: data
            ? '图没画出来：数据不完整（rows / steps / groups 都没有内容）'
            : '图没画出来：JSON 格式有误'
        }
      }
      canvas.append(rendered.node)
      // 图名交给骨架渲染（在类型标签右边一格）⇒ 两条通道的头部是同一份 DOM，
      // 不用各写一套「标题 + 让位给源码按钮」的排版
      return { kind: rendered.kind, title: data.title ? cut(data.title, LIMIT.title) : '' }
    }
  })
}

/**
 * kbfig 插件。
 *
 * ⚠️ **不要再单独注册一个 `code_block` 的 nodeView**：ProseMirror 合并多个
 * 插件的 `nodeViews` 时是「后注册的覆盖先注册的整张表」，两个插件各注册一次
 * 会有一个静默失效（mermaid 或 kbfig 其中之一永远画不出来）。
 * ⇒ 由 `mermaidPlugin()` 统一注册，两个分支在同一个 nodeView 里判断。
 */
export const FIG_LANG = LANG
