/**
 * 「跳到某个知识目录的列表页」/「跳到某条知识点」这两个地址。
 *
 * ## 为什么收成一处
 *
 * 同一个地址原来写了三遍：侧边栏目录行的 `RouterLink :to`、
 * 侧边栏的 `goCategory`、知识点详情页的 `goCategory`。
 * 路由路径或 query 参数名一改要改三处 —— 漏一处就是"点了跳到别的页"，
 * 而且**不报错**。
 *
 * ⚠️ **和 `kb-editor.css` 里那个 `a[href^="/app/learn?category="]` 不是同一份**：
 * 那条是正文里目录链接的**字符串前缀**（markdown 里的 href 是拼出来的字符串，
 * 不是路由对象），CSS 属性选择器读不到 JS 常量，只能各写各的。
 * 那是被迫的副本，不是重复。
 *
 * @param {number|string} categoryId 目录 id
 * @returns {{path:string,query:object}} vue-router 的 location 对象
 */
import { canUseTree } from './viewPref'

/**
 * 「跳到某个目录的卡片视图」的地址。
 *
 * @param {number|string} categoryId 目录 id
 * @returns {{path:string,query:object}} vue-router 的 location 对象
 */
export function categoryLink(categoryId) {
  return { path: '/app/learn', query: { category: categoryId } }
}

/**
 * 「跳到某个**一级目录**的树视图」的地址。
 *
 * <p>和 {@link categoryLink} 是**两种形态**，不是同一个地址的两个写法：
 * - `?tree=<id>` = **树视图**：这一本书的整棵目录树（结构）。
 * - `?category=<id>` = **卡片视图**：那个目录（含子树）的知识点平铺（内容）。
 *
 * <p>⚠️ **侧边栏点书走树视图、正文里点目录走卡片视图** —— 前者是"我要看这本书
 * 有什么"，后者是"我要看这个目录里的内容"，问的不是一件事。
 *
 * <p>⚠️ **两个参数不再互斥**（2026-11-04）：`tree` = **哪本书**，`category` =
 * **正在看的那个目录**，各管一件事 ⇒ **树视图 = 有 tree 且没有 category**
 * （判据只有一份，见 AppLearnView 的 `isTree`）。
 * 写的时候**不再互相清掉**：清掉 `tree` 会让浏览器"返回"回不到刚才那棵树
 * （用户报的「返回的目录都不对了」）。
 *
 * @param {number|string} categoryId 一级目录 id
 * @returns {{path:string,query:object}} vue-router 的 location 对象
 */
export function treeLink(categoryId) {
  return { path: '/app/learn', query: { tree: categoryId } }
}

/**
 * 「跳到某个目录」——**按这个目录的展示偏好**决定去树视图还是卡片视图。
 *
 * <p>**不是第三种地址**，只是前两种按偏好挑一个：偏好是树 ⇒ `?tree=`，
 * 是卡片 ⇒ `?category=`。两种形态的地址本来就只有那两个（见上面的注释），
 * 这里只做"挑一个"，不新造第三种。
 *
 * <p>偏好在 {@link viewPref}（存在 `sys_config` 的"我的覆盖"那一层）。
 *
 * <p>⚠️ **搜索态不走这里**（AppLayout 的 `bookLink` 自己拼）：搜索时侧边栏只列命中的书
 * ⇒ 点进去要看的正是"这本书里命中的那几条" ⇒ **搜索词得跟着走**；
 * 而且正文档下"再点已选中那本"要**故意不带** `tree`（取消选中）——
 * 这两种形状 `viewLink` 表达不了（它总是带上 `tree` 或 `category`）。
 *
 * @param {{id:number, parentId?:number}} category 目录对象
 * @returns {{path:string,query:object}} vue-router 的 location 对象
 */
export function viewLink(category) {
  // ⚠️ 2026-11-09 用户：「这个配置还是要保留 只是切换现在是放到目录树上方的切换按钮了
  // 而已」——书（一级目录）**一律进 ?tree=**：树顶的「目录树|卡片」切换 + 每本书的
  // 偏好（ui.view.category.<id>）在那里决定形态（带 ?view= 走 URL，不带则跟偏好）。
  // 不再把偏好=卡片的书送进 ?category= 独立卡片页——那一页**没有切换开关**，
  // 切走了「没法恢复」（2026-11-09 用户报）。
  return canUseTree(category) ? treeLink(category.id) : categoryLink(category.id)
}

/**
 * 目录链接的 **href 前缀**（markdown 里那个字符串地址）。
 *
 * <p>正文里的目录链接是 markdown 里的 href，**只能拼成字符串**（路由对象没法
 * 写进 markdown）⇒ 这条前缀和 `categoryLink` 是两件不同的事：前者给字符串，
 * 后者给路由对象。**前缀只有这一份**，编辑器和 AI 生成的内容都从这儿取。
 *
 * <p>⚠️ CSS（`kb-editor.css` 的 `a[href^=...]`）里还有一份同样的字面量：
 * CSS 属性选择器读不到 JS 常量，只能各写各的。那是被迫的副本，不是重复。
 */
export const CATEGORY_HREF_PREFIX = '/app/learn?category='

/**
 * 目录链接的 href 字符串（写进 markdown 的那种）。
 *
 * @param {number|string} categoryId 目录 id
 * @returns {string}
 */
export function categoryHref(categoryId) {
  return CATEGORY_HREF_PREFIX + categoryId
}

/**
 * 知识点链接的 **href 前缀**（markdown 里那个字符串地址）。
 *
 * <p>和 `CATEGORY_HREF_PREFIX` 是一对：目录跳列表页、知识点跳详情页。
 * 正文里两种链接都要能被编辑器认出来（站内跳转 ⇒ 走 router，不开新标签），
 * 所以两条前缀住在同一个文件里 —— **站内地址只有这一处**。
 *
 * <p>⚠️ CSS 里同样有一份字面量（被迫的副本，见上）。
 */
export const TOPIC_HREF_PREFIX = '/app/topic/'

/**
 * 知识点链接的 href 字符串（写进 markdown 的那种）。
 *
 * @param {number|string} topicId 知识点 id
 * @returns {string}
 */
export function topicHref(topicId) {
  return TOPIC_HREF_PREFIX + topicId
}
