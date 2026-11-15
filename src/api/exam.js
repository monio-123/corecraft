import request from '../utils/request'

// ============ 考试链路（paper → attempt → answer → grade-result） ============
//
// 生命周期（设计 §11）：出卷持久化成 paper（题目快照 + 归属快照 + 范围快照）
// → 开一次作答 attempt → 答案落 answer → 判分结果回填 grade-result
// （成绩、证据、学习投影、复习调度同事务提交）。
//
// **重做 = 新 attempt 指向旧 attempt**（replayOfAttemptId），原记录一个字不动：
// 同卷重做是有效回忆（novelty = 1/(1+重做序号)），但同题同答案重复提交
// 是完全无效证据（换答案也刷不动）——这两条判重在后端，前端只管传对 id。
//
// 题目一旦存进 paper 就有稳定 questionId，证据/归属都挂它 ⇒ 回看、改归属、
// 重做全都认这个 id，不认数组下标。

/** 出卷完成后先持久化，返回 paperId（进答题前必须先存，题目 id 由服务端发） */
export function savePaper(data) {
  return request.post('/kp/exam/papers', data)
}

/**
 * 考试历史列表（摘要，不含完整题干）。
 *
 * @param {object} params { kind?, categoryId?, status?, createdFrom?, createdTo?,
 *   unfinished?（只看有作答还没交的）, page?, size? }
 */
export function getPapers(params) {
  return request.get('/kp/exam/papers', { params })
}

/**
 * 随堂测验的锚（TODO-exam-lifecycle §三）：这条知识当前的卷 + 最新一次作答。
 * 返回 `{ paper: 摘要|null, attempt: {id, status}|null }`——「没有」是正常态不是错误。
 * silent：锚查不到 / 后端没上这接口都不该弹全局错误（调用方自己降级）。
 */
export function getQuizCurrent(topicId) {
  return request.get('/kp/exam/quiz-current', { params: { topicId }, silent: true })
}

/** 从考试历史删掉一份卷（软删：记录页 / 锚 / 回看不再出现，已产出的证据不动） */
export function deletePaper(id) {
  return request.delete(`/kp/exam/papers/${id}`)
}

/** 试卷详情（含题目：questionId、题型、分值、题干快照、归属状态） */
export function getPaper(id) {
  return request.get(`/kp/exam/papers/${id}`)
}

/**
 * 新建一次作答。body 可带 { replayOfAttemptId }（一般走 replayAttempt 更省事）。
 * 返回 attemptId。
 */
export function createAttempt(paperId, data) {
  return request.post(`/kp/exam/papers/${paperId}/attempts`, data ?? {})
}

/**
 * 按原卷重做：服务端从原 attempt 取 paperId 建新 attempt（不让调用方
 * 有机会拿两张不同的卷拼一次重做）。返回新 attemptId。
 */
export function replayAttempt(attemptId) {
  return request.post(`/kp/exam/attempts/${attemptId}/replay`)
}

// ⚠️ 单条 attempt 状态接口（GET /kp/exam/attempts/{id}）**有意不包装**：
// 恢复作答走 getAttemptReview（题干+作答+判定一起回），单独的状态查询前端零调用。

/**
 * 保存作答（自动保存 / 交卷前）。**PUT 全量覆盖**——每次都发完整答案集，
 * 后端删旧插新，不是增量补丁；中途只改一题也要带上没改的。
 *
 * @param {object} data { answers: [{ questionId, answerPayload }] }
 */
export function saveAnswers(attemptId, data) {
  return request.put(`/kp/exam/attempts/${attemptId}/answers`, data)
}

/** 交卷：进入判分 */
export function submitAttempt(id) {
  return request.post(`/kp/exam/attempts/${id}/submit`)
}

/**
 * 判分结果回填。resultId 幂等——同一个 resultId 重复提交直接短路，
 * 不会把证据算两遍；改判要换新 resultId 且 gradeRevision + 1。
 *
 * @param {object} data { resultId, gradeRevision, answers: [{ questionId, verdict, scoreRatio, score, explanation, gradePayload }] }
 */
export function gradeResult(attemptId, data) {
  return request.post(`/kp/exam/attempts/${attemptId}/grade-result`, data)
}

/**
 * 逐题回看：题干 + 你的作答 + 判定 + 得分 + 讲解。
 * 交完卷/考完试翻旧卷走这里，不用重新拉整张 paper。
 */
export function getAttemptReview(id) {
  return request.get(`/kp/exam/attempts/${id}/answers`)
}

// ⚠️ 题目知识归属的保存接口（POST /kp/exam/questions/{id}/attribution）**有意不包装**：
// 出题时归属随判分结果一起落（后端自己算），独立的"事后补归属"前端还没有入口（零调用）。
// 真做时再加包装；协议说明见 exam.js 里 saveAttribution 的旧注释（git 历史）。
