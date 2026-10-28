import request from '../utils/request'
import { authHeaders } from '../utils/auth'

/**
 * AI 配置（每人一份）。
 *
 * **路径不带 userId**：后端从登录态取，前端传了也没用，传了反而像能改别人的。
 *
 * **apiKey 不再回传**（2026-10-26 改）：设置页只说"配了没有"，不给明文。
 * 改的理由不是"密钥该藏着"这种洁癖，是**刷新模型这件事本来就得由后端做**——
 * 厂商接口要用密钥，前端拿不到密钥就调不了 ⇒ 密钥没有必要下发到浏览器。
 * ⚠️ 这条推翻了 2026-10-07 那次"明文回传"的决定。
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

// ---------- 厂商与模型清单（全局字典） ----------

/**
 * 厂商清单（连各自的模型）。
 *
 * **厂商 = 一个字典类型**（code 前缀 ai_vendor_），**模型 = 那个类型下的字典项**。
 * 全局的（字典表没有 user_id），所以路径不带 userId。
 */
export function listAiVendors() {
  return request.get('/ai/vendor')
}

/**
 * 从厂商接口拉最新模型清单，**写回字典**。
 *
 * @param data { vendor, baseUrl?, apiKey?, endpointId? }
 *   - `apiKey`：编辑已有端点时**不用传**——密钥不再下发到浏览器，后端用这条端点自己存的那把。
 *     新增端点时还没存进库，才需要前端把刚填的密钥传上去。
 *   - `endpointId`：告诉后端"用这条端点存的地址和密钥"
 */
export function refreshAiModels(data) {
  return request.post('/ai/vendor/models/refresh', data)
}

/**
 * 抽屉空态那几个引导问题。
 *
 * 来自配置树上的「引导问题」那组，**改问题在管理后台的系统配置里改就行，不用发版**。
 * 配置树拉不到时后端返回空列表，前端空态什么都不显示——不该为一个可有可无的引导列表
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
 * @param material 这次要处理的素材（用户自己敲的一段文字）。不传 = 用这次指定的知识。
 *   给了就用给的，**两者不同时出现**
 * @param topicIds 这次处理**哪几条**知识（考一个目录时）。传了它就以它为准，topicId 忽略
 * @param history 之前几轮 `[{question, answer}]`。**协议无状态，不回传模型就不记得**——
 *   后端不落库也拿不到，所以这份历史只能由抽屉交上来
 * @param endpointId 用哪个 API 调。**不传就用全局当前选中的那个**（AI 设置里选的那条）
 * @param thinking 这次要不要关掉模型的思考。`false` = 关；不传 = 不干预
 * @param signal 传 AbortSignal 可以中途取消
 * @returns {Promise<string>} 这次收到的**完整正文**
 */
export async function streamChat(
  { topicId, topicIds, question, endpointId, questionKey, material, history, thinking },
  { onPrompt, onDelta, onReasoning } = {},
  signal
) {
  const res = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, topicIds, question, endpointId, questionKey, material, history, thinking }),
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
  // **完整正文在这里攒一份**：流式是"边收边给"，但调用方要的是"收完的那一整段"。
  // 以前每个调用方自己写一遍 `let text = ''` + `onDelta: (t) => { text += t }`
  // （出卷、判分、生成骨架、写知识点、挑标签……散在四个文件里），
  // 改一处只改一半 ⇒ **这条规则收在这里，只此一份**。
  //
  // ⚠️ 只攒 `delta`（正文），**不攒 `reasoning`（思考过程）**：
  // 思考过程不是模型给的答案，混进去会污染解析器的输入。
  let full = ''
  // **上游为什么停**（stop / length …）和**流有没有正常收尾** ——
  // 这两件事决定内容是不是**截断的**。
  //
  // ⚠️ 截断以前是**静默**的：服务端超时（Spring MVC 的 async timeout，从请求开始算）
  // 会直接结束响应，前端读到 EOF 就正常返回 ⇒ 界面显示"生成完了"，
  // 库里那条知识却少了一截，**没有任何异常**。
  // 现在两种截断都变成**报错**：① 上游说 length；② 流没收到 done 事件就断了。
  let finishReason = ''
  let sawDone = false
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
      if (evt.finish) finishReason = evt.finish
      if (evt.done) {
        sawDone = true
        if (finishReason && finishReason !== 'stop') {
          throw new Error(truncatedMessage(finishReason))
        }
        return full
      }
      if (evt.prompt) onPrompt?.(evt.prompt)
      if (evt.reasoning) onReasoning?.(evt.reasoning)
      if (evt.delta) {
        full += evt.delta
        onDelta?.(evt.delta)
      }
    }
  }
  // 读到 EOF 却**没收到 done 事件** ⇒ 流被中途掐了（服务端超时 / 连接断了）⇒ 内容不完整。
  if (!sawDone) throw new Error(truncatedMessage(''))
  return full
}

/**
 * 截断时给用户看的那句话。**短、只说事实**（准则见 brove-shared §8）：
 * 用户要知道的只有一件事 —— "这次的内容不全"。
 */
function truncatedMessage(reason) {
  if (reason === 'length') return '可能没生成完（达到输出上限）'
  if (!reason) return '可能没生成完（连接中断）'
  return `可能没生成完（上游以 ${reason} 结束）`
}

async function readError(res) {
  try {
    const body = await res.json()
    return body?.message || `AI 调用失败（${res.status}）`
  } catch {
    return `AI 调用失败（${res.status}）`
  }
}