/**
 * 拖拽挪目录的**命中检测状态机**（从 `useCorecraftWebStore.js` 拆出，2026-11-15）。
 *
 * 职责边界：这里只有"指针落在哪 / 能不能放 / 放下之后叫谁"——
 * 树数据的读写（moveCategory / getCategory / getCategories）仍在 store，
 * Pointer Events 的接线在 `useCategoryDrag.js`。
 */
import { reactive } from 'vue'
import {
  ROOT_CATEGORY, getCategories, getCategory, getCategorySubtreeIds, moveCategory
} from './useCorecraftWebStore'

/**
 * 拖拽状态。**只挪目录**（把一个目录拖到另一个目录下面 = 换父级）。
 *
 * ⚠️ **知识点拖拽归类整套没了**（2026-11-05 用户：「图二是最早做的拖动归类 也直接删掉吧」）：
 * 卡片拖进目录、树里知识点行拖拽、触屏「归类到目录」菜单，三条路一起撤。
 * ⇒ 拖的东西只剩一种（目录），`topicId` / `picking` 两个状态字段也跟着没了。
 *
 * ⚠️ 这里**不用 HTML5 Drag and Drop**：那套 API 在触摸设备上不工作（iOS Safari 完全
 * 不支持，Android Chrome 长按会先触发原生文本选择菜单，dragstart 根本轮不到触发），
 * 平板上表现为"长按没反应"。现在由自建的 Pointer Events 接管三端，见 useCategoryDrag.js。
 */
export const dnd = reactive({
  live: false,      // 拖拽中
  catId: null,      // 正在挪哪个目录
  over: null,       // 当前命中的目标：目录 id，或 ROOT_CATEGORY（= 挪到顶层）
  overOk: false,    // 命中的目标能不能放（成环 / 重名时置灰）
  x: 0, y: 0,       // 指针当前位置（幽灵跟这个走）
  label: ''         // 幽灵上显示的一行文字
})

/**
 * 命中检测。**先找具体的目录行，再处理侧边栏目录区**：
 * 指针落在任意目录行上 → 那个目录 id（拖过去 = 挪到它下面）；
 * 落在侧边栏**书列上方**的目录区抬头 → ROOT_CATEGORY（挪成一级目录）。
 *
 * **顺序不能反**：`.app-cats__item` 是 `.app-side__section` 的后代，
 * `closest('[data-cat-root]')` 从那一行往上找**也会命中**那个整块。
 * 先判 data-cat-id 才有"拖到 JAVA 上 = 进 JAVA"这个说法。
 *
 * ⚠️ **没落在书行上就是「升为一级」**（2026-11-13 用户追加反馈）：
 * 「进某一本一级书内部」有**明显的深色 hover 高亮**，拖的人看得见自己落在哪，
 * 不需要拿空白去替他猜「其实想进哪本书」。
 * ⇒ 同日早那版「书列起点及以下所有留白按垂直距离吸附到最近书行」整个撤掉：
 * `.app-cats` 是 `flex:1` 吃满剩余高度，书列下方那一大片空白也在它里面，
 * 吸附会把那里判成「进最后一本书」⇒ 想把二级目录升为一级时只能挤到顶部
 * 抬头那一小条，直觉上说不通。
 * 现在**一个位置只有一个语义**：命中 `[data-cat-id]` = 进那一本书；
 * 还在 `[data-cat-root]` 内但没命中行（抬头 / 行间空隙 / 书列之后的整片留白）
 * = `ROOT_CATEGORY`，升为一级。
 *
 * ⚠️ 配套：`checkDrop` 加了「目标就是当前父级 ⇒ 不可放」—— 一本本来就顶层的
 * 书落到空白时后端 `moveCategory` 见 parent_id 没变会直接 return，界面若还
 * 显示「可放」就是零反馈。现在这种落点直接标红，不骗人。
 *
 * 侧边栏行当落点不是可选项，是**必须**：弹层铺的是 `childrenOf(那个目录)`、
 * 不含它自己，所以「把 Hashmap 拖回 JAVA」和「把知识点放进一个没有下级的目录」
 * 这两件事在只有弹层落点时**都无处可放**（后者更糟：没有下级时弹层压根不弹）。
 *
 * 拿不到坐标（0,0）时返回 null 而不是去命中 (0,0) 处的元素——
 * 视口原点通常没有落点，但真命中了就是一次"凭空归类"，比不命中更坏。
 */
function hitCategory(x, y) {
  if (!x && !y) return null
  const el = document.elementFromPoint(x, y)
  const node = el?.closest?.('[data-cat-id]')
  if (node) return Number(node.dataset.catId)
  const block = el?.closest?.('[data-cat-root]')
  if (!block) return null
  return ROOT_CATEGORY
}

/**
 * 这个目标能不能放。**判断在这里做一次、结果放进 `dnd.overOk`**，
 * 界面直接读那个布尔去置灰，不要在模板里每行重算一遍。
 *
 * 挡三种：
 * 1. **成环** —— 放到自己或自己的后代下，之后这棵树再也遍历不出根
 * 2. **重名** —— 撞 DB 的 uk_user_parent_name
 * 3. **没挪窝** —— 目标父级就是它现在的父级。后端 `moveCategory` 见两边相等
 *    会直接 return（连失败都不算）⇒ 界面若还显示「可放」，松手就是零反馈，
 *    看着像拖不动。这里提前判掉，标红比假装成功诚实。
 *
 * 前两条后端也会再挡一次（HTTP 入口是外部输入边界）。前端置灰只是提前告知，
 * 真正的保证在后端。
 */
function checkDrop(over) {
  if (over == null) return false
  if (over === dnd.catId) return false
  if (over !== ROOT_CATEGORY && getCategorySubtreeIds(dnd.catId).has(over)) return false
  const cat = getCategory(dnd.catId)
  if (!cat) return false
  const target = over || ROOT_CATEGORY
  if ((cat.parentId || ROOT_CATEGORY) === target) return false
  return !getCategories().some(
    (c) => c.id !== cat.id &&
      (c.parentId || ROOT_CATEGORY) === target &&
      c.name === cat.name
  )
}

/**
 * 起一次拖拽：把状态一次性摆好。
 *
 * ⚠️ **只挪目录**（知识点归类那套 2026-11-05 删了）⇒ 没有"拖什么"的选择题，
 * 也没有第二套打开方式（触屏点选）需要同步 —— 以前这里是两处各写一遍字段，
 * 漏一处就是"某种打开方式下幽灵还留着上一次的文字"。
 */
export function dndStart(catId, x, y, label = '') {
  dnd.live = true
  dnd.catId = catId
  dnd.over = null
  dnd.overOk = false
  dnd.x = x
  dnd.y = y
  dnd.label = label
}

export function dndMove(x, y) {
  dnd.x = x
  dnd.y = y
  dnd.over = hitCategory(x, y)
  dnd.overOk = checkDrop(dnd.over)
}

/** 放下：命中了、而且那是个合法落点，才真的动手。没命中/非法 = 取消。 */
export async function dndDrop() {
  const { catId, over, overOk } = dnd
  dndReset()
  if (over == null || !overOk) return
  await moveCategory(catId, over)
}

export function dndReset() {
  dnd.live = false
  dnd.catId = null
  dnd.over = null
  dnd.overOk = false
}
