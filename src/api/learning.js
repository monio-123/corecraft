import request from '../utils/request'

// ============ 学习状态（目录聚合 + 档位下发） ============
//
// 后端统一挂 /kp/learning/**（实施计划拍板 #1）。
//
// ⚠️ 单条口径的三个接口（标记已学 / 单条投影 / 单条证据列表）**有意不包装**：
// 详情页的「标记已学」按钮和档位入口 2026-11-12 已撤（掌握度改走"有证据即已学"），
// 前端零调用 ⇒ 不留包装函数占着（后端端点还在，真做 UI 时再按需加，见 FEATURES §10）。
//
// 掌握度是**两个维度**分开算的（设计 §4）：
//   ability（能力掌握 A）—— 做题证据按评分比聚合，只涨不随时间掉；
//   freshness（记忆新鲜度 R）—— 随时间衰减，复习/做对会刷新。
// 前端展示"掌握度"时是 A×R 的合成视角，但**别把两个数混成一个存**——
// "会但忘了"和"根本不会"是两种病，合成一个数就分不出来了。

/**
 * 目录三指标聚合（设计 §8.1）：**一次批量返回整棵树**，不让前端逐目录请求。
 *
 * <p>每条 = `{categoryId, eligibleCount, learnedCount, assessedCount, mastery}`：
 * 学习进度 = learned/eligible、评估覆盖 = assessed/learned，
 * 当前掌握度 = mastery（后端加权聚合的**权威值**，null = 没有合格条目 ⇒ 中性空态
 * 不是 0）。前端只做显示，不在客户端另算一套掌握度公式（目录无第二份真相）。
 * 未归类知识点不参与任何目录聚合 ⇒ 返回里没有「未归类」桶。
 *
 * @param {number} [rootCategoryId] 只要某棵子树时传；不传 = 整棵树
 */
export function getLearningTree(rootCategoryId) {
  return request.get('/kp/learning/tree', { params: rootCategoryId ? { rootCategoryId } : {} })
}

/**
 * 全部有评估的知识点的档位与掌握度值（2026-11-14，目录树知识点行数据源）。
 *
 * <p>每条 = `{topicId, level, mastery}`：level 是后端算好的档位词（null 的行不下发），
 * mastery 是当前掌握度 M（ability × 衰减后 R，0~100）——树行的圆点染色用 level、
 * 进度条填充用 mastery。**行数 = 考过的知识点数**（量小），树视图按 topicId 现查。
 * silent 不适用：树行要它做展示，失败就保持原样（静默降级在 store 层做）。
 */
export function getMasteryLevels() {
  return request.get('/kp/learning/levels', { silent: true })
}
