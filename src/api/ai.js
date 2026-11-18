import request from '../utils/request'
import { authHeaders } from '../utils/auth'

/**
 * AI 配置（每人一份）。
 *
 * **路径不带 userId**：后端从登录态取，前端传了也没用，传了反而像能改别人的。
 *
 * **apiKey 明文回传**（2026-11-11）：GET /ai/endpoint 直接带回来，编辑弹窗回填，
 * 输入框默认掩码、点小眼睛才显明文。⚠️ 这条推翻了 2026-10-26「密钥不下发」的决定
 * （当时编辑框留空 = 不改，用户看不出配的是哪把 key）；保存时留空仍是"不改"，
 * 所以这条只影响回显，不影响"清空 = 不改"的语义。
 */

/** 读当前用户的配置。没配过时后端返回 enabled=false 的空壳，不是 null */
/**
 * 某条提示词**展开 `【引用】` 之后**的最终文本。
 *
 * ⚠️ **别用 `getMyConfig` 取用来当提示词的配置**：那返回的是原文，配置里若写着
 * `【引用】ai.skill.writing`，原样送进 prompt 就只有这一行字面量，被引的规范一个字都到不了
 * （2026-11-12 实测踩到）。凡是「要拿去当提示词」的读取，都走这里。
 */
export function getComposedPrompt(key) {
  return request.get('/ai/prompt', { params: { key }, silent: true })
}

export function getAiConfig() {
  return request.get('/ai/config')
}

/**
 * 把某条 API 设为当前（**账号级**，跨设备一致）。
 *
 * <p>⚠️ 不要只写 localStorage：那只对这台浏览器有效 ⇒ 换浏览器/换机器登录就掉回默认，
 * 用户看到的是「我明明配了 mimo，怎么又是 deepseek」（2026-11-13 用户报的）。
 */
export function setCurrentAiEndpoint(endpointId) {
  return request.post('/ai/config/current', { endpointId }, { silent: true })
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
 * @param data { vendor, baseUrl?, apiKey?, protocol?, endpointId? }
 *   - `apiKey`：编辑时前端已有明文（回显到编辑框），会原样带回来；留空则后端用
 *     这条端点自己存的那把。新增端点时还没存进库，必须传。
 *   - `protocol`：决定拉清单的地址和请求头，见上一段。
 *   - `endpointId`：告诉后端"用这条端点存的地址、密钥和协议"
 */
export function refreshAiModels(data) {
  return request.post('/ai/vendor/models/refresh', data)
}

/**
 * 测试一条 API 配置能不能真调通（地址 / 密钥 / 协议 / 模型名一起验）。
 *
 * **上游不通是测试结果，不是系统故障**：后端返回 `{ok:false, message:<上游原文>}`
 * 而不抛业务异常，全站错误提示出口因此不会弹一条多余红字。
 * `silent:true` 是防网络层失败（超时 / 断网）也弹一条 —— 那类同样由界面的
 * 内联结果统一呈现。15 秒够一次最小对话；上游真挂住时先由这里断，按钮不会一直转。
 */
export function testAiEndpoint(data) {
  return request.post('/ai/endpoint/test', data, { timeout: 15000, silent: true })
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
 * @param maxTokens 这次要多少**输出**额度（token）。**不传 = 服务端兜底值**
 *   （现在后端显式给了 8192，以前是厂商默认值）。只有「写知识点」这种一次要吐几千字、
 *   且被截断后要**涨额度重试**的调用才需要传（服务端 `KpGenTaskServiceImpl` 撞 `length` 时会补一次）
 * @param signal 传 AbortSignal 可以中途取消
 * @returns {Promise<string>} 这次收到的**完整正文**
 */
export async function streamChat(
  { topicId, topicIds, question, endpointId, questionKey, material, history, thinking, maxTokens },
  { onPrompt, onDelta, onReasoning } = {},
  signal
) {
  const res = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, topicIds, question, endpointId, questionKey, material, history, thinking, maxTokens }),
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
  // 每收到一帧就重新计时；到点直接 cancel reader（read() 会以 done 结束循环）
  let idleFired = false
  let idleTimer = null
  const armIdle = () => {
    clearTimeout(idleTimer)
    idleTimer = setTimeout(() => {
      idleFired = true
      reader.cancel().catch(() => {})
    }, IDLE_TIMEOUT_MS)
  }
  armIdle()
  // 3 分钟内一个正文字都没有 ⇒ 别等了（到点先看 full 有没有内容，有就不动）
  let noContent = false
  let noContentTimer = setTimeout(() => {
    if (!full) {
      noContent = true
      reader.cancel().catch(() => {})
    }
  }, NO_CONTENT_TIMEOUT_MS)
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    armIdle()
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
          throw truncatedError(finishReason, full)
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
  clearTimeout(idleTimer)
  clearTimeout(noContentTimer)
  // 我们主动断的（模型长时间没输出）⇒ 说人话，别报成"连接中断"
  if (idleFired && !sawDone) throw idleError(full)
  if (noContent && !sawDone) {
    const err = new Error('模型一直在思考、没开始写目录，已中断（关掉「开启思考」再试）')
    err.idle = true
    err.partial = ''
    throw err
  }
  // 读到 EOF 却**没收到 done 事件** ⇒ 流被中途掐了（服务端超时 / 连接断了）⇒ 内容不完整。
  if (!sawDone) throw truncatedError('', full)
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

/**
 * 截断的 Error：**把已经收到的正文一起带出去**。
 *
 * <p>为什么要带：`length` 不等于"什么都没拿到"——模型可能写了半棵树就被掐了。
 * 而这一步（生成规划）的产物是一棵**可删改的树**，半棵也远比"回退到第一步、白等两分钟"有用。
 * 上层拿到 `partial` 就能接着用；不带的话它只能看到一个 message 字符串，什么也做不了。
 *
 * <p>三种情况要分开（看 `partial` 是不是空）：
 * - 有正文 + 截断 ⇒ 用半份，提示"不全"
 * - **没正文** + 截断 ⇒ 思考把额度吃光了（实测 `reasoning_tokens` = 整个 max_tokens，
 *   正文 0 字），这时候"不全"是错的描述，得说"没拿到"
 */
/**
 * **卡住的判定**：这么久一个事件都没收到，就当这条流死了。
 *
 * <p>为什么要它：没有这个闸，上游挂了 / 模型卡在思考里不出来时，前端会**一直转圈**
 * （实测用户在 minimax 上等了 10 分钟，最后等到的是浏览器层的一句 "network error"）。
 * 60 秒的依据：正常规划在思考阶段也会持续吐 reasoning 事件（每秒级），
 * 而一帧都没有只可能是连接死了或模型停了 —— 这时候越早断越好。
 */
const IDLE_TIMEOUT_MS = 60_000

/**
 * **只思考不产出**的判定：这么久一个正文字都没有，就不等了。
 *
 * <p>和上面那条的区别：空闲是"连帧都没有"，这条是"有帧、但全是思考"。
 * 实测 minimax 开着思考时能连续吐十分钟 reasoning、正文 0 字（用户等到最后是浏览器层的
 * 一句 network error）—— 那种情况空闲闸永远不会响，只能按"迟迟不产出"判。
 */
const NO_CONTENT_TIMEOUT_MS = 180_000

function idleError(partial) {
  const err = new Error(`模型 ${Math.round(IDLE_TIMEOUT_MS / 1000)} 秒没有输出，已中断（可以换个模型，或关掉「开启思考」再试）`)
  err.idle = true
  err.partial = partial || ''
  return err
}

function truncatedError(reason, partial) {
  const err = new Error(truncatedMessage(reason))
  err.truncated = true
  err.reason = reason || 'eof'
  err.partial = partial || ''
  return err
}

async function readError(res) {
  try {
    const body = await res.json()
    return body?.message || `AI 调用失败（${res.status}）`
  } catch {
    return `AI 调用失败（${res.status}）`
  }
}