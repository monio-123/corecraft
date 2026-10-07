import request from '../utils/request'
import { authHeaders } from '../utils/auth'

/**
 * AI 配置（每人一份）。
 *
 * **路径不带 userId**：后端从登录态取，前端传了也没用，传了反而像能改别人的。
 *
 * **apiKey 明文进出**（2026-10-07 用户拍板）：设置页把它当普通字段显示和编辑，
 * 打开编辑框就是库里那个值。原来是"只进不出 + 显示『已填写，留空表示不改』"，
 * 用户要核对 / 复制的时候拿不到——藏起来是负担，不是保护。
 * ⚠️ 代价：密钥随列表响应一起下发到浏览器，且站点走 HTTP。
 * 要收回这条得靠 HTTPS + 密钥加密存储，不是改这一行能解决的。
 */

/** 读当前用户的配置。没配过时后端返回 enabled=false 的空壳，不是 null */
export function getAiConfig() {
  return request.get('/ai/config')
}

export function saveAiConfig(data) {
  return request.put('/ai/config', data)
}

// ---------- API 列表（每人多个） ----------

export function listAiEndpoints() {
  return request.get('/ai/endpoint')
}

export function createAiEndpoint(data) {
  return request.post('/ai/endpoint', data)
}

export function updateAiEndpoint(data) {
  return request.put('/ai/endpoint', data)
}

export function deleteAiEndpoint(id) {
  return request.delete(`/ai/endpoint/${id}`)
}

/**
 * 抽屉空态那几个引导问题。
 *
 * 来自字典 ai_topic_question，**改问题在管理后台的字典管理里改就行，不用发版**。
 * 字典拉不到时后端返回空列表，前端空态什么都不显示——不该为一个可有可无的引导列表
 * 让整页报错。
 */
export function listAiQuestions() {
  return request.get('/ai/chat/questions')
}

/**
 * 就某条知识提问，**流式**。
 *
 * **不走 axios**：axios 实例上有全局 `timeout: 5000`，会把正在进行的流直接掐断
 * （模型思考 + 逐字返回本来就经常超过 5 秒）。
 * **不走 EventSource**：它只能 GET、不能带请求头，token 只能塞进 URL——
 * 会进网关访问日志、浏览器历史和 Referer。
 *
 * 回调收到三种事件：
 * - `onPrompt(text)` 提交给模型的完整消息，**开流前发一次**。历史在不在里面一眼看得见
 * - `onReasoning(text)` 思考过程增量。**只有思考型模型才给**（deepseek-reasoner / o 系列），
 *   非思考模型（deepseek-flash 这类）一次都不会触发
 * - `onDelta(text)` 正文增量
 *
 * @param scene 问答场景，决定后端用哪段统一描述（字典 ai_system_prompt）。**必填**——
 *   场景写错时后端宁可报错，也不肯换一段描述顶上去（那样只是"答非所问"，看着完全正常）
 * @param history 之前几轮 `[{question, answer}]`。**协议无状态，不回传模型就不记得**——
 *   后端不落库也拿不到，所以这份历史只能由抽屉交上来
 * @param signal 传 AbortSignal 可以中途取消
 */
export async function streamChat(
  { topicId, question, endpointId, questionKey, scene, history },
  { onPrompt, onDelta, onReasoning } = {},
  signal
) {
  const res = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, question, endpointId, questionKey, scene, history }),
    signal
  })
  // 这一层只在"还没开始流"时才可能失败（401 / 参数校验 / 后端开流前就抛了异常）。
  // 流开始之后的错误是走 SSE 的 error 事件，见下面的 evt.error —— 那种情况 HTTP 状态
  // 早就是 200 了。
  if (!res.ok || !res.body) throw new Error(await readError(res))

  // ⚠️ 必须校验 content-type。后端开流前抛异常时，框架异常处理器兜底回的是**一整段 JSON**
  // （{code, message, data}），不是事件流——而 HTTP 状态照样是 200。
  // 不校验的话下面按 data: 解帧会一行都读不出来，for 循环直接结束、函数正常返回，
  // 界面上一个字符都不显示、也不报错：用户看到的是"点了没反应"，排查时连日志都没有。
  // （这个坑踩过一次：同一页面上引导问题能出答案、手敲的问题却"什么都没发生"。）
  const ctype = res.headers.get('content-type') || ''
  if (!ctype.includes('text/event-stream')) throw new Error(await readError(res))

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim()
      buffer = buffer.slice(idx + 1)
      if (!line.startsWith('data:')) continue
      const payload = line.slice(5).trim()
      if (!payload) continue
      const evt = JSON.parse(payload)
      if (evt.error) throw new Error(evt.error)
      if (evt.done) return
      if (evt.prompt) onPrompt?.(evt.prompt)
      if (evt.reasoning) onReasoning?.(evt.reasoning)
      if (evt.delta) onDelta?.(evt.delta)
    }
  }
}

async function readError(res) {
  try {
    const body = await res.json()
    return body?.message || `AI 调用失败（${res.status}）`
  } catch {
    return `AI 调用失败（${res.status}）`
  }
}