/**
 * 「这个目录用哪种形态展示」——**按用户 + 目录**记住。
 *
 * ## 为什么它是"偏好"而不是"设定"
 *
 * 一级目录（书）有两种看法：**目录树**（结构：这本书里有什么）和
 * **卡片**（内容：这个目录下的知识点）。哪种是默认的**因人而异、因书而异** ——
 * 它既不是全站设定，也不是界面布局，而是"我怎么看这一本书"的个人选择
 * ⇒ 存在 `sys_config` 的**"我的覆盖"**那一层（`/sys/config/mine`）。
 *
 * ## 三层，生效顺序固定
 *
 * 1. **我的覆盖**：`ui.view.category.<目录id>`（user_id=我）
 * 2. **系统默认**：`ui.view.category`（user_id=0，管理员设）
 * 3. **代码兜底**：一级目录 = 树，其余 = 卡片
 *
 * ⚠️ **系统默认和用户覆盖不是一回事**（用户 2026-11-03 明确）：
 * 系统默认是"全站都这样"，用户覆盖是"就这个目录我偏要那样"。后者永远压前者。
 * ⇒ 这两件事**不能混成一层**：混了的话管理员改一下默认，所有人的个人选择就没了。
 *
 * ## 兜底为什么是"一级目录 = 树，其余 = 卡片"
 *
 * 树视图**只对一级目录成立**（二级以下点进去本来就是卡片 —— `isTree` 要求
 * `!parentId`）⇒ 一级目录默认给树（AI 生成的书就是这样），其余给卡片。
 *
 * ⇒ **老数据（一级目录）不用迁移**，行为一个字没变
 * （用户 2026-11-03：「AI 生成的默认是树，其他场景默认是卡片，老数据不用管」）。
 * 「手工建的一级目录」是"其他场景" ⇒ 建好的时候写一份覆盖 = 卡片
 * （见 CategoryNameDialog）。
 */
import { getUiPref, setUiPref } from './uiPrefs'

/** 形态取值。**只有这两种** —— 树视图只对一级目录成立，再多就是多一份要维护的语义。 */
export const VIEW_TREE = 'tree'
export const VIEW_CARD = 'card'

/** 配置键前缀。**带 id 的是"这个目录"，不带的是系统默认。 */
const NS = 'ui.view.category'

/** 某个目录的偏好键 */
function keyOf(categoryId) {
  return `${NS}.${categoryId}`
}

/**
 * 这个目录**能不能**用树视图。
 *
 * <p>**树视图的根永远是一级目录**（一棵"书"的整棵目录树）。二级目录没有自己的树 ——
 * 它所属那本书才有。所以判据只有一条：**这个目录是不是一级**。
 *
 * <p>⚠️ **这条判据只有这一份**：原来写在两个地方（这里的兜底 + `AppLearnView` 的
 * `isTree`）⇒ 两边漂了的话，侧边栏会把二级目录送成 `?tree=<id>`，而页面判它不是树
 * ⇒ **那一屏退成全量平铺**，界面上一点异常都看不出来。
 *
 * @param {{id:number, parentId?:number}|null} category 目录对象
 * @returns {boolean}
 */
export function canUseTree(category) {
  return !!category && !category.parentId
}

/**
 * 这个目录该用哪种形态。
 *
 * @param {{id:number, parentId?:number}} category 目录对象（没有就当卡片）
 * @returns {'tree'|'card'}
 */
export function viewOf(category) {
  if (!category) return VIEW_CARD
  const mine = getUiPref(keyOf(category.id))
  if (mine === VIEW_TREE || mine === VIEW_CARD) return mine
  const sys = getUiPref(NS)
  if (sys === VIEW_TREE || sys === VIEW_CARD) return sys
  return canUseTree(category) ? VIEW_TREE : VIEW_CARD
}

/**
 * 记住：这个目录用这种形态。
 *
 * <p>写的是**"我的覆盖"**（user_id=我），系统默认那一行不动 ——
 * 删掉这份覆盖就回到系统默认（`clearMyConfig`）。
 *
 * @param {number} categoryId 目录 id
 * @param {'tree'|'card'} view 形态
 */
export async function setView(categoryId, view) {
  await setUiPref(keyOf(categoryId), view)
}
