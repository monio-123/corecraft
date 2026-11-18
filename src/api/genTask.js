import request from '../utils/request'

// ============ 生成任务（服务端跑，关页面不断） ============

/**
 * 建任务并排队执行。
 *
 * <p>**任务 = 一串「材料 → 一次调用 → 一个落库动作」**：材料由前端算好
 * （复用 `buildMaterial`），后端只负责按序调用、落库、记进度。
 * 所以这里传的是**已经拼好的材料**，不是主题/目录这些业务参数。
 *
 * @param {object} data { type, title, promptKey, params, steps: [{ seq, title, topicId, material }] }
 * @returns {Promise} 任务 id
 */
export function createGenTask(data) {
  return request.post('/kp/gen-task', data)
}

/**
 * 任务列表（**不含每一步明细**）。
 *
 * @param {object} params { status?, type?, page?, size? }
 */
export function getGenTaskList(params) {
  return request.get('/kp/gen-task/list', { params })
}

/**
 * 任务详情（带每一步的状态、耗时、token、finish 原因）。
 *
 * <p>⚠️ **单独放宽超时**：全局是 5 秒，而带几十上百条步骤的详情实测要 8~9 秒
 * （后端曾把 material / prompt_snapshot 一起查出来，已改成只查列表要用的列，
 * 但历史上跑过的大任务仍可能偏慢）⇒ 用全局超时的话，表现是
 * **展开详情一直停在"加载中"**，不报错也不出来。
 */
export function getGenTaskDetail(id) {
  return request.get(`/kp/gen-task/${id}`, { timeout: 20000 })
}

/**
 * **暂停**（2026-11-12 用户口径：叫「暂停」不叫「停止」——
 * 叫「停止」会让用户以为"彻底不跑了、不能恢复"）。
 *
 * **正在写的那一条会写完**（不掐流，免得留半截正文），
 * 剩下的不再开始 ⇒ 状态可能短暂停在 `canceling`。
 */
export function cancelGenTask(id) {
  return request.post(`/kp/gen-task/${id}/cancel`)
}

/** **恢复**：接着上次没跑完的继续跑（成功的不重写）。暂停过的任务可以恢复。 */
export function resumeGenTask(id) {
  return request.post(`/kp/gen-task/${id}/resume`)
}

/**
 * **同一个任务继续往下跑**（v2 单任务化）。
 *
 * <p>用户确认完目录之后，正文那一步挂在**同一个任务**上接着跑 ——
 * 而不是新建第二张卡（「生成主题」本来就是一件事，拆成两张卡用户就看不到全貌）。
 *
 * @param {number} id
 * @param {{steps: Array, buildSummary?: string, endpointId?: number}} data
 */
export function continueGenTask(id, data) {
  return request.post(`/kp/gen-task/${id}/continue`, data)
}

/**
 * **人工节点办完了**：awaiting → succeeded（幂等，不是 awaiting 就直接返回）。
 * 归类的「采纳完」、出题的「作答建卷那一刻」都走它。
 */
export function ackGenTask(id) {
  return request.post(`/kp/gen-task/${id}/ack`)
}

/**
 * **重跑某个节点**（节点级重试）。
 *
 * <p>按**节点**（outline / body / classify）重跑，而不是整个任务重来 ——
 * 目录已经确认过了，没必要再花 4 分钟重出一遍。
 *
 * @param {number} id
 * @param {string} nodeKey 节点 key
 */
export function retryGenTaskNode(id, nodeKey) {
  return request.post(`/kp/gen-task/${id}/nodes/${nodeKey}/retry`)
}

/**
 * **删除任务**（软删）。
 *
 * <p>⚠️ **只删任务本身**：已经写进知识库的正文留在库里 ——
 * "删掉这个任务"≠"删掉生成的知识"。
 */
export function removeGenTask(id) {
  return request.delete(`/kp/gen-task/${id}`)
}

/**
 * ⚠️ **AI 归类的采纳端点已删**（2026-11-16 D2 AI 归类退役）：
 * 归类任务型、批量归类结果采纳整条链下线 ⇒ 这个端点没有后端对应路径了。
 */
