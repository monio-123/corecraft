/**
 * 目录树（`el-tree-select` 的 data 形状）。**收在这里而不是各选择器各建一份**：
 * 「AI 归类」的批量选择器和正文里的「目录链接」要的是同一棵树，
 * 两处各建一遍，改排序或孤儿处理就得改两处。
 * 父不在列表里的按顶层放（都是自己的数据，那样出现是脏数据）。
 *
 * @param {Array<{id:number, name:string, parentId:number}>} categories 目录列表（平铺的）
 * @returns {Array<{value:number, label:string, children?:Array}>} 树
 */
import { keywordFromRoute } from './routeQuery'
import { matchNameFromRoute } from './searchMode'

export function toCategoryTree(categories) {
  const list = categories || []
  const ids = new Set(list.map((c) => c.id))
  const byParent = new Map()
  for (const c of list) {
    if (!c.parentId || !ids.has(c.parentId)) continue
    if (!byParent.has(c.parentId)) byParent.set(c.parentId, [])
    byParent.get(c.parentId).push(c)
  }
  const node = (c) => {
    const kids = (byParent.get(c.id) || []).map(node)
    return kids.length ? { value: c.id, label: c.name, children: kids } : { value: c.id, label: c.name }
  }
  return list
    .filter((c) => !c.parentId || !ids.has(c.parentId))
    .sort((a, b) => String(a.name).localeCompare(String(b.name), 'zh'))
    .map(node)
}

// ==================== 展开 / 收起 ====================
//
// 下面两个是**浏览模式那棵树**（`CategoryTree.vue`）折叠时用的纯函数。
//
// **只有一份定义** —— 组件和验证脚本都用它。规则写在组件里的话，
// 验证脚本只能抄一份 ⇒ 抄的那份会漂（改了组件里的算法，脚本还在验旧算法），
// "验过了"这句话就不可信了。
//
// 树是**平铺的前序列表**（`flattenCategoryTree` 的输出）：父节点后面紧跟自己的子树。
// ⇒ 「收起一个目录」不需要知道子树长什么样，把它那一整段剔掉就行；
// 「这一行有哪些祖先」也不用另建父子表 —— 沿路碰到的那些目录按深度分层记着就是。

/**
 * 有下级的目录 id —— **能画出展开箭头的那批**。
 *
 * <p>判据是「下一行更深」：**前序列表里一个节点的子树紧跟在它后面**
 * ⇒ 下一行的深度比它大 ⇒ 它有下级；否则它就是叶子（既没子目录、也没挂着的知识点）。
 *
 * @param {Array<{kind:'cat'|'topic', depth:number}>} rows 前序行
 * @returns {Set<number>} 有下级的目录 id
 */
export function branchyIds(rows) {
  const ids = new Set()
  const all = rows || []
  for (let i = 0; i < all.length; i++) {
    const r = all[i]
    if (r.kind !== 'cat') continue
    const next = all[i + 1]
    if (next && next.depth > r.depth) ids.add(r.c.id)
  }
  return ids
}

/**
 * 前序列表的"祖先栈"：走一行就把 path 维护成**这一行的祖先链**。
 *
 * <p>**这是"祖先"这件事的唯一算法**：path[d] = 深度 d 上那个目录，
 * 某一行的祖先 = path[0..depth-1]。
 *
 * <p>两处用它：{@link visibleRows} 判"这一行被哪个祖先藏起来了"，
 * {@link ancestorIds} 答"这一行的祖先是谁"（返回时展开到那一条用）。
 * **各写一遍的话，两处对"祖先"的定义会漂** —— 表现是"树明明展开了，
 * 那条知识点还是不出现"，而界面上什么异常都看不到。
 *
 * @param {Array} path 复用同一个数组（调用方持有）
 * @param {{kind:'cat'|'topic', depth:number}} r 当前行
 * @returns {Array} path（就地维护）
 */
function pathStep(path, r) {
  path.length = r.depth
  if (r.kind === 'cat') path[r.depth] = r.c.id
  return path
}

/**
 * 画出来的行：**被收起的目录，它自己那棵子树整段不画**（它自己那一行留着）。
 *
 * <p>「这一行的祖先」= 前序走到这一行时沿路碰到的那些目录（按深度分层记着：
 * path[d] = 深度 d 上那个目录）。任一祖先被收起 ⇒ 这一行不画。
 *
 * @param {Array} rows 前序行
 * @param {Set<number>} collapsed 被收起的目录 id（空 = 全部展开）
 * @returns {Array} 可见行
 */
export function visibleRows(rows, collapsed) {
  const all = rows || []
  const off = collapsed || new Set()
  if (!off.size) return all
  const path = []
  const out = []
  for (const r of all) {
    const d = r.depth
    let hidden = false
    for (let i = 0; i < d; i++) {
      if (off.has(path[i])) { hidden = true; break }
    }
    pathStep(path, r)
    if (!hidden) out.push(r)
  }
  return out
}

/**
 * 某一行的**祖先目录 id**（根 → 父，从外往里）。
 *
 * <p>用途：从目录树点进一条知识点、再返回时，**把它的祖先逐个展开** ——
 * 不展开的话那一条根本不画（`visibleRows` 的规矩），返回过去是一片空白。
 *
 * @param {Array} rows 前序行
 * @param {number} index 那一行的下标
 * @returns {number[]} 祖先目录 id（没有祖先就是空数组）
 */
export function ancestorIds(rows, index) {
  const all = rows || []
  if (index < 0 || index >= all.length) return []
  const path = []
  for (let i = 0; i <= index; i++) pathStep(path, all[i])
  // path[d] 是深度 d 上的目录；这一行深度是 d ⇒ 它的祖先是 path[0..d-1]
  return path.slice(0, all[index].depth)
}

// ==================== 搜索：哪些目录留得住 ====================

/**
 * **关键词归一**：trim + 空串归 null。**只有这一处**。
 *
 * <p>⚠️ 「文字算不算命中」和「标哪几个字」必须用**同一个词** ⇒ 归一也只做一次。
 */
function normKeyword(keyword) {
  const kw = String(keyword || '').trim()
  return kw ? kw : null
}

/**
 * **一段文字里有没有那个关键词**（大小写不敏感的子串）。
 *
 * <p>⚠️ **这是"算不算命中"的唯一规则** —— 目录名命中（{@link hitCategoryIds}）、
 * 知识点标题命中（CategoryTree 的命中清单）都走它。
 * 各写一遍的话，改一边另一边就漂（症状是"标了字却没算命中"或者反过来，
 * 而界面上什么异常都看不出来）。
 *
 * @param {string} text 原文
 * @param {string} keyword 关键词（空 = 什么都不命中）
 * @returns {boolean}
 */
export function matchesKeyword(text, keyword) {
  const kw = normKeyword(keyword)
  if (!kw) return false
  return String(text ?? '').toLowerCase().includes(kw.toLowerCase())
}

/**
 * **目录名命中的那些**（名字里有那个词）。
 *
 * <p>⚠️ **判据只有一份**（{@link matchesKeyword}）—— 树的标字、侧边栏的"名字命中"
 * 都走它 ⇒ 不会出现"标了字却没算命中"。
 *
 * <p>⚠️ **它只管"名字"**：内容命中（底下有点名命中）是另一回事，
 * 那是服务端按关键词筛过的索引给的计数（{@link hitCategoryIds}）。
 *
 * @param {Array<{id:number,name:string,parentId:number}>} categories 目录（平铺）
 * @param {string} keyword 关键词（空 = 什么都不命中）
 * @returns {Set<number>} 命中的目录 id
 */
export function namedHitIds(categories, keyword) {
  const named = new Set()
  const kw = normKeyword(keyword)
  if (!kw) return named
  for (const c of categories || []) {
    if (matchesKeyword(c.name, kw)) named.add(c.id)
  }
  return named
}

/**
 * 搜索时**留得住哪些目录**、**哪些目录的名字命中了**。
 *
 * <p>**命中 = 内容命中 ∪ 名字命中**：
 * - **内容命中**：`counts[d] > 0` ⇔ "d 的子树里有点名命中"（计数沿祖先链累加，
 *   所以祖先也算命中 —— 它确实"含命中"，符合条件）。
 * - **名字命中**：目录名里有那个词（**本地就能判**，不等服务端）。
 *
 * ⚠️ **名字命中的那条目录，它的整条祖先链也得留着** —— 不留就成了"孤儿目录"
 * （爹被过滤掉了，它自己孤零零挂在树上，读不出它属于哪）。
 *
 * ⚠️ **祖先 ≠ 命中**：祖先只是为了"路径完整"被留下的，**它自己没命中**
 * （它下面那个目录名命中，不代表它自己名字里有那个词）。
 * ⇒ 返回值分两个集合：
 * - **`named` = 名字命中的那些**（**树上标的是它** —— 标的是**名字里那几个字**，
 *   不是整行；见 {@link highlightParts}）；
 * - **`keep` = 留得住的全部**（内容命中 ∪ 名字命中目录的祖先链）—— 侧边栏按它筛。
 *
 * <p>**这里只判"留不留 / 命不命中"，不判"画在哪"**：侧边栏只画一级、树里画整本，
 * 两边用**同一个判据**（一份定义），但取的范围不同。
 *
 * ⚠️ **内容命中那一支不再单独给一份"命中集"**（2026-11-05 用户：
 * 「我让你标蓝是标蓝命中的文字 你把他对应的目录标蓝」）：
 * 标的是**文字**，命中单位是"名字里含那个词的那几个字" ⇒ 一个集合就够，
 * 再给一份"整行都算命中"的集合只会被下一个人拿去当"标蓝整行"用。
 *
 * @param {Array<{id:number,name:string,parentId:number}>} categories 目录（平铺）
 * @param {object} counts 内容命中的计数（getCategoryCountMap 的结果）
 * @param {string} keyword 关键词（空 = 什么都不命中）
 * @param {boolean} matchName **要不要把目录名也算命中**（「搜目录」那档要；
 *        「搜正文」那档不要 —— 它只搜正文，目录名里有没有那个词不相干）
 * @returns {{keep:Set<number>, named:Set<number>}}
 *          keep = 留得住的目录 id；named = 名字命中的
 */
export function hitCategoryIds(categories, counts, keyword, matchName = true) {
  const keep = new Set()

  // 内容命中：计数 > 0 的那些（祖先链已经被计数带上了 ⇒ 直接就是"路径完整"的集合）
  //
  // ⚠️ **counts 是"当前关键词下的结构索引"**（服务端按 `q` 筛过的）
  //   ⇒ **关键词为空时它就是全集** ⇒ 每个目录 counts 都 > 0。
  //   ⇒ 所以 `keep` 只在"有搜索词"时才有意义（`visibleRootCategories` 词为空时直接返回全部）。
  for (const id of Object.keys(counts || {})) {
    if (counts[id] > 0) keep.add(Number(id))
  }
  // 名字命中：只有「搜目录」那档才算（正文那档只搜正文，目录名里有没有那个词不相干）。
  const named = matchName ? namedHitIds(categories, keyword) : new Set()
  if (!named.size) return { keep, named }

  // 名字命中的那条，**整条祖先链也得留住**（祖先进 keep，不进 named）
  const byId = new Map()
  for (const c of categories || []) byId.set(c.id, c)
  for (const id of named) {
    const seen = new Set()
    let cur = byId.get(id)
    while (cur && !seen.has(cur.id)) {
      seen.add(cur.id)
      keep.add(cur.id)
      cur = byId.get(cur.parentId)
    }
  }
  return { keep, named }
}

/**
 * **把一段文字按关键词切成"命中的字"和"其余的字"**。
 *
 * <p>用户 2026-11-05：「我让你标蓝是标蓝命中的文字 你把他对应的目录标蓝」
 * ⇒ 标的是**那几个字**，不是它所在的整行。
 *
 * <p>⚠️ **匹配规则和判"命中"的那一处必须一致**：判命中用的是
 * {@link matchesKeyword}（大小写不敏感的子串），这里也是
 * ⇒ 否则会出现"命中了却一个字都没标"或者"标了却没算命中"。
 *
 * @param {string} text 原文
 * @param {string} keyword 关键词（空 = 整段都是"没命中"）
 * @returns {Array<{text:string, hit:boolean}>} 切好的片段（顺序拼起来还是原文）
 */
export function highlightParts(text, keyword) {
  const s = String(text ?? '')
  const kw = normKeyword(keyword)
  if (!kw) return [{ text: s, hit: false }]
  const lower = s.toLowerCase()
  const k = kw.toLowerCase()
  const out = []
  let i = 0
  while (i < s.length) {
    const at = lower.indexOf(k, i)
    if (at < 0) break
    if (at > i) out.push({ text: s.slice(i, at), hit: false })
    // 切片的**长度按原词算**（不是小写后的那份）—— 少数字符小写后长度会变。
    out.push({ text: s.slice(at, at + kw.length), hit: true })
    i = at + kw.length
  }
  if (i < s.length) out.push({ text: s.slice(i), hit: false })
  return out.length ? out : [{ text: s, hit: false }]
}

/**
 * **侧边栏那一列：现在显示哪些一级目录。**
 *
 * <p>⚠️ **只有一份实现** —— 侧边栏（AppLayout）和学习页（拿"第一本命中的书"当树的根）
 * 都要它：各写一遍的话，改一边另一边就漂
 * （症状是"侧边栏剩三本，树却长在第四本上"，而界面上看不出哪儿不对）。
 *
 * @param {Array<{id:number}>} roots 一级目录
 * @param {Array<{id:number,name:string,parentId:number}>} categories 全部目录（平铺）
 * @param {object} counts 命中计数（getCategoryCountMap 的结果）
 * @param {string} keyword 关键词（空 = 全列）
 * @param {boolean} matchName 目录名算不算命中（「搜目录」那档要）
 * @returns {Array} 要显示的一级目录
 */
export function visibleRootCategories(roots, categories, counts, keyword, matchName) {
  const kw = normKeyword(keyword)
  if (!kw) return roots || []
  const { keep } = hitCategoryIds(categories, counts, kw, matchName)
  return (roots || []).filter((c) => keep.has(c.id))
}

/**
 * **在集合里加/去一个 id，返回新集合**（不改原来那个）。
 *
 * <p>⚠️ **只有这一份**：树的"展开/收起"有三处都写了一遍
 * "拷贝 Set → add/delete → 整体赋回"（toggleOne / openChapter / expandToSpot），
 * 漏改一处 ⇒ 收起状态不更新，而界面上看不出哪儿不对。
 *
 * @param {Set<number>} set 原集合
 * @param {number} id 要加/去的那一个
 * @returns {Set<number>} 新集合
 */
export function toggleInSet(set, id) {
  const next = new Set(set || [])
  if (next.has(id)) next.delete(id)
  else next.add(id)
  return next
}

/**
 * **从集合里去掉若干 id，返回新集合**（不改原来那个）。
 *
 * @param {Set<number>} set 原集合
 * @param {Iterable<number>} ids 要去掉的那些
 * @returns {Set<number>} 新集合
 */
export function removeFromSet(set, ids) {
  const next = new Set(set || [])
  for (const id of ids || []) next.delete(id)
  return next
}

/**
 * **把「1.2 分层体系结构」拆成序号和名字**（`{ num: '1.2', rest: '分层体系结构' }`）。
 *
 * <p>老稿里序号是独立元素（章 = 圆角徽章、节 = 主题色小字），名字里不该再带一遍。
 * 没有序号前缀的名字返回 null（徽章/节号整个不画，名字原样显示）。
 * 目录名是用户能改的 ⇒ **拆不出来是正常路径，不是错误**。
 *
 * <p>⚠️ **只有这一份**：树的模板里要用它拆名字（章行徽章、节号、标题行入口文字），
 * 各处各写一遍正则的话，改一边另一边就漂（表现是"徽章显示 1.2、名字里还带 1.2"）。
 *
 * @param {string} name 目录名
 * @returns {{num:string, rest:string}|null}
 */
export function catNum(name) {
  const m = /^(\d+(?:\.\d+)*)\.?\s+(.+)$/.exec(String(name || '').trim())
  return m ? { num: m[1], rest: m[2] } : null
}

/**
 * **按 URL 推导**的那一版 {@link visibleRootCategories}。
 *
 * <p>⚠️ **为什么需要这一层**（2026-11-11 收敛）：侧边栏（AppLayout）与
 * 学习页（AppLearnView）都要"这一档显示哪几本书"，两边传进来的参数是
 * **同一组**（一级目录 / 全部目录 / 命中计数 / 关键词 / 目录名算不算命中），
 * 前四个尤其容易走神（少传一个 counts 就变成"永远显示全部"）。
 *
 * <p>⇒ 现在"从 URL 读哪些参数"这一步也收进这里：两个调用点各剩一行，
 * **不会再有一边漏传某个参数**（症状是"侧边栏剩三本，树却长在第四本上"，
 * 而界面上看不出哪儿不对）。
 *
 * @param {object} route vue-router 的 route 对象
 * @param {Array} roots 一级目录
 * @param {Array} categories 全部目录（平铺）
 * @param {object} counts 命中计数（getCategoryCountMap 的结果）
 * @returns {Array} 要显示的一级目录
 */
export function visibleRootCategoriesOf(route, roots, categories, counts) {
  return visibleRootCategories(
    roots,
    categories,
    counts,
    keywordFromRoute(route),
    matchNameFromRoute(route)
  )
}
