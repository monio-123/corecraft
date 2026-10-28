/**
 * 从路由对象上读业务参数。
 *
 * ## 为什么收成一处
 *
 * 「当前关键词 = URL 上的 `?q=`」这条推导原来写了三遍：
 * 壳（AppLayout）、首页（AppHomeView）、列表页（AppLearnView）。
 * 判据一改（多读一个参数、trim、归一大小写）要改三处，漏一处就出现
 * 「列表筛了、侧边栏条数没筛」——**而且界面上什么异常都看不到**。
 *
 * ⚠️ **三处必须一致**：壳和首页用它去拉结构索引（索引是"当前关键词下的全集"），
 * 列表页用它去拉当页数据。三边不是同一个词，侧边栏的条数和列表里的条数就对不上。
 *
 * @param {object} route vue-router 的 route 对象
 * @returns {string} 关键词（没有就是空串）
 */
export function keywordFromRoute(route) {
  return String(route?.query?.q || '')
}
