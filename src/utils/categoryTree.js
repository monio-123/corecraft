/**
 * 目录树（`el-tree-select` 的 data 形状）。**收在这里而不是各选择器各建一份**：
 * 「AI 归类」的批量选择器和正文里的「目录链接」要的是同一棵树，
 * 两处各建一遍，改排序或孤儿处理就得改两处。
 * 父不在列表里的按顶层放（都是自己的数据，那样出现是脏数据）。
 *
 * @param {Array<{id:number, name:string, parentId:number}>} categories 目录列表（平铺的）
 * @returns {Array<{value:number, label:string, children?:Array}>} 树
 */
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
    path.length = d
    if (r.kind === 'cat') path[d] = r.c.id
    if (!hidden) out.push(r)
  }
  return out
}
