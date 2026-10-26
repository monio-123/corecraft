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
export function categoryLink(categoryId) {
  return { path: '/app/learn', query: { category: categoryId } }
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

/**
 * 跳到知识点详情页的路由地址（vue-router 的 location 对象）。
 *
 * @param {number|string} topicId 知识点 id
 * @returns {{path:string}}
 */
export function topicLink(topicId) {
  return { path: '/app/topic/' + topicId }
}
