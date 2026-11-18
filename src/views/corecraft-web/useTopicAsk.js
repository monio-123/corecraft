/**
 * 对这条知识提问（AI，零写入）——**详情页问答抽屉的整套逻辑**。
 *
 * ## 为什么搬出来
 *
 * 以前这一整块（抽屉宽度 + 流式问答 + 消息列表 + 渲染节流）塞在
 * `AppTopicDetailView.vue` 里，那个文件已经 2400 行，四块互不相干的业务挤在一起：
 * 正文检索 / **AI 问答** / 随堂测验 / 选区提问写入。
 *
 * 这一块**边界最清楚**：
 * - 问什么：用户在输入框敲的、或引导问题给的；
 * - 怎么问：`ask()`（`useAi.js` 的流式出口）；
 * - 怎么显示：消息列表 + 思考框 + 答案渲染；
 * - **零写入**：答完一个字都不回写到 `kp_topic`（写入是另一条路，见「应用到正文」）。
 *
 * ⇒ 它自成一块 ⇒ 搬进这个 composable，详情页只留"调用它"的几行。
 *
 * ## 依赖从哪来（全部注入，不反向依赖页面）
 *
 * 这个 composable **不知道详情页有什么**，要什么就传什么进来：
 * - `askAiCall` —— 流式问答的出口（`useAi.js` 的 `ask`）；
 * - `topicId` —— 当前这条知识的 id（问的时候要带上）；
 * - `baseMdOf` —— 当前正文本体（问的时候当材料）；
 * - `docOf` —— markdown → doc（**用正文编辑器自己的解析器**，见 `renderAnswer`）。
 *
 * ⚠️ **渲染器只有这一个**：另找一个 markdown 渲染器就是两套渲染
 * （标题层级、表格、代码块全对不上）—— 那种坑已经踩过一次。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ask as askAiCall, aiQuestions, ensureAiQuestionsLoaded } from './useAi'
import { buildEditMaterial, buildEditQuestion, loadEditSpec } from './aiEdit'
import { htmlOf } from './docDiff'
import { getPrefSpec, getUiPrefs, saveUiPrefs } from '../../utils/uiPrefs'

/**
 * @param {object} opts
 * @param {() => number} opts.topicId 当前这条知识的 id
 * @param {() => string} opts.baseMdOf 当前正文本体（当材料）
 * @param {(md: string) => object|null} opts.docOf markdown → ProseMirror doc（用编辑器自己的解析器）
 * @param {() => void} [opts.onAskAi] 正文里选区提问进来的那一轮（转交出去）
 */
export function useTopicAsk({ topicId, baseMdOf, docOf }) {
const ASK_GUIDE_FALLBACK = [
  { key: 'comb:none', label: '梳理重点' },
  { key: 'polish:none', label: '精简润色' },
  { key: 'examples:none', label: '补充实例' },
  { key: 'pick_category:categories', label: '关联目录' },
  { key: 'rename:none', label: '提炼标题' }
]

const askOpen = ref(false)
const askInput = ref('')

/**
 * 引导问题优先用配置树上那组的（带 key，后端据此附目录/标签清单、套对应的描述规则）；
 * 配置树拉不到时退回这五条本地兜底项。
 */
const guideQuestions = computed(() =>
  aiQuestions.value.length ? aiQuestions.value : ASK_GUIDE_FALLBACK
)

// 抽屉宽度。**面板滑块、拖动把手、双击归位三处必须同源**，所以上下限和默认值
// 都从配置树上那一项读，不在本文件另写一份 420。
const ASK_KEY = 'ui.detail.detailAskW'
const ASK_W = computed(() => getPrefSpec(ASK_KEY))
const askWidth = ref(getUiPrefs()[ASK_KEY] ?? ASK_W.value?.defaultValue)

/** 上限跟着视口走：抽屉盖过七成，正文就没法看了——那还不如关掉抽屉 */
function clampAskW(w) {
  // 规格取不到（配置树还没到）就不夹：宁可宽一点，也别因为 min/max 是 undefined 变成 NaN
  if (!ASK_W.value) return Math.round(w)
  const cap = Math.min(ASK_W.value.max, Math.round(window.innerWidth * 0.7))
  return Math.min(Math.max(Math.round(w), ASK_W.value.min), cap)
}

/**
 * 真正给 `:size` 的值。
 *
 * **必须在这里 clamp，不能只在拖动时 clamp**：上限跟着视口走，用户把抽屉拖到
 * 883px 视口下的 618px，换到 706px 的窄视口就该显示 494。存的值不变、显示的值
 * 要变——那 `askWidth` 就不能直接绑到 `:size`，否则窄视口下画出来的是 618
 * 被容器裁掉的状态；更要命的是拖动基准会取到那个"看不见的 618"，拖一下就跳。
 */
const askW = computed(() => clampAskW(askWidth.value))

/**
 * 双击把手 = 归位到默认宽度。
 *
 * <p>⚠️ 归位取的是**配置上的默认值**（`defaultValue`），不是上一版模板里写的
 * `ASK_W.def` —— spec 里根本没有 `def` 这个字段 ⇒ 那个表达式恒为 undefined
 * ⇒ 双击把宽度设成 undefined ⇒ `clampAskW(undefined)` 出 NaN ⇒ 抽屉宽度变成 NaN。
 * 归位这个动作一直是坏的，只是不常点所以没人发现。
 *
 * <p>取不到默认值时**不动**（宁可不归位，也不要归到一个 NaN）。
 */
function resetAskWidth() {
  const def = ASK_W.value?.defaultValue
  if (typeof def !== 'number' || !Number.isFinite(def)) return
  askWidth.value = def
  saveUiPrefs({ [ASK_KEY]: def })
}

function onGripDown(e) {
  // 抓住把手本身，而不是往 window 上挂监听：把手只有 22px 宽，
  // 指针一移出去事件就落到别的元素身上，拖到一半会断。
  const el = e.currentTarget
  const startX = e.clientX
  // 基准取**显示值**不是存档值：所见才能所拖
  const startW = askW.value
  el.setPointerCapture(e.pointerId)
  document.body.classList.add('is-col-resizing')
  // 往左拖 = 变宽（抽屉贴右边），所以位移取反
  const move = (ev) => {
    const w = clampAskW(startW - (ev.clientX - startX))
    // **拖动过程中就落盘**，而不是等 pointerup：指针在窗口外松开、或被系统
    // 打断时那个事件根本不来，宽度就只停在内存里，刷新一下白拖。
    // 阈值 8px 是为了别每帧都 setItem——那是同步 IO，会卡主线程。
    if (Math.abs(w - askWidth.value) < 8) return
    askWidth.value = w
    saveUiPrefs({ [ASK_KEY]: w })
  }
  // pointercancel 也得收尾：指针被系统抢走时不会补一个 pointerup
  const up = () => {
    document.body.classList.remove('is-col-resizing')
    el.removeEventListener('pointermove', move)
    el.removeEventListener('pointerup', up)
    el.removeEventListener('pointercancel', up)
    saveUiPrefs({ [ASK_KEY]: askWidth.value }) // 最后一次精确值
  }
  el.addEventListener('pointermove', move)
  el.addEventListener('pointerup', up)
  el.addEventListener('pointercancel', up)
}
const asking = ref(false)
const messages = ref([])
const askBodyRef = ref(null)
let askAbort = null

const canAsk = computed(() => !asking.value && askInput.value.trim().length > 0)

/**
 * 流式期间跟着往下滚。
 *
 * 不跟着滚的话，长回答是**在视野下方一截一截长出来的**——用户盯着屏幕只看到"正在思考…"，
 * 内容全在折叠线以下，看起来就像卡住了。
 *
 * 但不能无条件滚：用户往回翻想重读上一轮时，每来一帧就把他拽回底部，比不滚还烦。
 * 所以只在"本来就贴着底部"时跟随——想读的人已经自己滚上去了，那就不打扰。
 * 阈值给 80px：流式时 DOM 每帧都在长，差值天然大于 0，卡太死会一帧都不跟。
 */
function followStream() {
  const el = askBodyRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight > 80) return
  el.scrollTop = el.scrollHeight
}

// 挂在 received 上而不是 answer 上：思考型模型的正文可能短到只有几个字，
// 但思考过程照样在逐帧长，只盯正文会漏掉"思考还在流"的那一大段。
//
// ⚠️ flush: 'post' 是必须的：默认的 pre-flush 在 DOM 更新**之前**跑，那时
// scrollHeight 还是上一帧的高度，滚了个寂寞。加了 post 才量得到新内容。
watch(() => messages.value[messages.value.length - 1]?.received, followStream, { flush: 'post' })

/**
 * 拉引导问题（配置树那组）。
 *
 * <p>引导问题优先用配置树上那组的（带 key，后端据此附目录/标签清单、套对应的描述规则）；
 * 配置树拉不到时退回这三条纯读取的本地问题——它们不需要任何附加料，缺了配置树也能用。
 * 配置树不是锦上添花，不该因为它没拉到就让空态什么都不剩。
 */
async function loadGuideQuestions() {
  await ensureAiQuestionsLoaded()
}

function askNow(q) {
  // 点一下直接问，不先填进输入框：它是"一键动作"。填进去的话用户一改字，
  // questionKey 和实际问的内容就对不上了（key 还在、描述还是那条，用户的原话被丢掉）
  askInput.value = q.label
  submitAsk(q.key)
}

// 「复制」直接用 utils/clipboard 的统一出口，这里不再包一层。
// 原来包出来只为了挂一句 ElMessage.success('已复制')——而反馈现在归 copyText 管，
// 链接工具条那个复制按钮也是同一个出口，两边行为一致了。

/**
 * 抽屉里这一轮问答走的那条「可改写」约定。
 *
 * **约定本体拆成两半，各归其位**：
 * - 「怎么答」（判据 + 输出格式）在**代码里**（`aiEdit.js` 的 `ANSWER_GATE`）——
 *   它必须跟用户的话**一起落在最后一条消息**，模型才认得出"这次要什么"；
 * - 「改写规范」（保留什么、结构怎么跟、术语要解释…）在**配置里**
 *   （`sys_config` 的 `ai.prompt.edit.body`，配置页可改），由**前端**读出来
 *   （后端那条路会把用户自己敲的问题整段替换掉，见 `aiEdit.js` 的注释）。
 *
 * 于是「对原文的任意修改」这条链就通了：AI 自己定改什么、怎么改（提示词说了算），
 * 应用侧只认那两行标记 —— 有 ⇒ 整篇替换，没有 ⇒ 照旧追加一段。
 */

/**
 * 发起一轮问答。
 *
 * ⚠️ **返回"有没有真的发起"**（true / false）。
 * 早退的原因只有两个：问题为空、**上一轮还在流**。
 * 早退时这一轮**根本不存在** ⇒ 调用方不能把它当"已经在跑了"——
 * 上一版「重新生成」就是踩了这个：它先把"正在生成"置上，再调这里；
 * 这里一早就退（上一轮还在流），那个标志**永远没人复位** ⇒ 按钮转圈到天荒地老，
 * 右边的内容当然也不会变。**返回布尔值就是把"没跑起来"这件事说出去的通道。**
 */
function submitAsk(questionKey = '', opts = {}) {
  // **问题可以由外面给**（选区提问那一条：问题已经拼好，不必再过输入框）。
  const question = (opts.question !== undefined ? opts.question : askInput.value).trim()
  if (!question || asking.value) return false
  askInput.value = ''
  // 写入锚点：这一轮没指定就**继承上一轮的** ——
  // 「不满意就让 AI 接着调、调好了再写」这条路径要求追问也带着同一个锚点，
  // 否则用户只能回到第一轮去找那个按钮，而那一轮的答案已经被追问顶掉了。
  const spot = opts.spot !== undefined
    ? opts.spot
    : (messages.value[messages.value.length - 1]?.spot || null)
  // **走「可改写」约定的提问**（用户自己敲的、选段问的）：
  // **要求走 question**（= 怎么答 + 改写规范 + 用户这次的原话），
  // **材料只带【原文】**（这次处理的是哪份内容）。
  //
  // ⚠️ **要求为什么必须走 question**（2026-11-05 实测两次才定下来）：
  // 后端拼出来的 user 消息是 `材料 + "\n\n" + 这一轮的要求` ⇒
  // **要求永远在最后一条，材料钉在第一条**。
  // - 早先把要求塞进材料 ⇒ 模型最后看到的是**上一轮**的旧问题
  //   ⇒ 它去答了旧问题（实测「改写 2.2」被当成了「解释上一段」）；
  // - 走 questionKey（完整键）更糟：后端 `current = 配置里的规范`
  //   ⇒ **用户自己敲的问题被整段替换掉**（`instruction.isEmpty() ? req.getQuestion() : instruction`）。
  // ⇒ 只能走 question，规范也由前端自己读配置（考试那组就是这么干的）。
  //
  // ⚠️ **引导问题不走这条**（rename / pick_category / organize…）：
  // 它们自带输出格式（名字 / 目录 / 标签），套上"改写正文"的约定会打架。
  const editable = !questionKey
  // 这次发给 AI 的正文本体。默认是**屏幕上那一版**（含还没保存的编辑）；
  // 「让 AI 再调一版」那条传的是当前这一版（见 onInsertAdjust）。
  const baseMd = opts.baseMd !== undefined ? opts.baseMd : baseMdOf()
  // **一轮 = 一条消息**（question + prompt + reasoning + answer + 复制），不是几条平铺的消息。
  // 平铺的话提问气泡和纯文字回答形态差太远、间距又一样，看不出哪句答哪句
  //
  // ⚠️ 必须是 reactive(...)，不能先造裸对象再 push 进 ref([])。
  // push 进 ref 的是**裸对象**，而模板 v-for 读到的是它的响应式代理；回调里往裸对象写
  // （turn.answer += delta）Vue 收不到任何通知 → 渲染不重跑 → 流式期间界面一个字都不动，
  // 全部内容堆到 .finally 改 asking 那一次渲染里一次性蹦出来。
  // 用户看到的是"点完卡住不动，然后唰一下全出"——和"后端把整段缓冲住了才给我们"
  // **完全同一种现象**，但根因在前端，跟 SSE 一点关系没有（这个坑真的踩过一轮，
  // 一度以为是 Tomcat 缓冲，方向整个反了）。
  // 历史：/chat/completions 是**无状态**协议，模型这一轮知道的全部就是这一次请求里的
  // messages 数组——之前问过的不一起发过去，它就真的不记得（曾经就是这样：同一个抽屉里
  // 接着追问，AI 却答"我没有上下文记忆"）。后端不落库、也拿不到，所以只能在这儿交上去。
  //
  // 只带**已经问完**的（done）：还在流的那一轮还没有答案，发过去等于让模型接着自己续写。
  // 出错的也不带——那轮的 answer 是报错文案，不是模型说过的话。
  const history = messages.value
    .filter((m) => m.done && !m.error && m.answer)
    .map((m) => ({ question: m.question, answer: m.answer }))

  const turn = reactive({
    question,
    prompt: '',
    reasoning: '',
    answer: '',
    // 回答渲染成的 HTML（markdown → **正文同一套**渲染器）。
    // 单独一个字段，不直接拿答案当 HTML：答案在流式期间是半截的，
    // 每来一个 delta 就要重渲染一次（节流见 scheduleRender）。
    html: '',
    // 本轮已收到的总字数（思考 + 正文都算）。**不是调试输出**：流式到底通没通，
    // 从界面上一眼就能看出来——数字一路往上爬 = 真的在流；从 0 直接跳到全长 =
    // 上游把整段缓冲住了才给我们。没有它，"卡住了"这件事就只能靠猜。
    //
    // ⚠️ 必须把两个通道都算进来：只数 reasoning 的话，非思考模型（deepseek-flash 这类）
    // 一次都不会触发 onReasoning，计数器恒为 0 → 永远不渲染，恰好在最需要它的模型上是瞎的。
    received: 0,
    // 本轮收完了没（决定字数计数器还显不显示）。不能用全局 asking 顶替：
    // 它是抽屉级的，多轮之后会把早已结束的那一轮也点亮
    done: false,
    // 思考进行中 = 展开。正文一开始到达就转 false。**只增不回头**：
    // 有内容之后框就不再自动合上了，看原因见模板里 <details class="think"> 的注释
    thinking: true,
    error: false,
    questionKey,
    // 写入锚点（从正文里选了一段发起的那一轮才有）。追问会继承同一个锚点。
    spot,
    // **这一轮发给 AI 时用的正文本体**。
    // 改写模式下弹窗左栏拿它当"原文"（不是"此刻编辑器里是什么"——
    // 中间用户又编辑过的话，AI 的改写是基于旧正文的，写进去会盖掉他的修改）。
    bodyAtAsk: baseMd
  })
  messages.value.push(turn)
  // 这一轮建好了。**给"重新生成"用**：它要拿到这一轮才能读流式内容
  // （turn.html 是节流渲染好的 HTML），所以在请求发出去之前就给出去。
  opts.onStart?.(turn)

  asking.value = true
  askAbort = new AbortController()
  const signal = askAbort.signal
  // 改写规范从配置读一次（异步）。**读不到就省掉**：「怎么答」那段（判据 + 输出格式）
  // 在代码里 ⇒ 标记照样有，只是少了些写法上的讲究 ⇒ 不会整条链断掉。
  const specReady = editable ? loadEditSpec() : Promise.resolve('')
  specReady.then((spec) => {
    askAiCall(editable ? buildEditQuestion(question, spec) : question, {
      topicId: topicId(),
      // ⚠️ **这里不能给完整键**：questionKey 命中配置时，后端会把用户自己敲的问题
      // 整段替换掉 ⇒ 要求就改走 question 了（见上面的注释）。
      // 引导问题照旧走它们自己的键（那几个问题的"话"就是按钮本身，不需要用户原话）。
      questionKey,
      // 材料里只有正文本体 —— **要求不在材料里**（材料钉在第一条，
      // 要求放进去会被上一轮的问题盖过去）。
      material: editable ? buildEditMaterial(baseMd) : undefined,
      history,
      signal,
      onPrompt: (text) => { turn.prompt = text },
      onDelta: (delta) => {
        turn.answer += delta
        turn.received += delta.length
        turn.thinking = false
        scheduleRender(turn)
        opts.onDelta?.(delta)
      },
      onReasoning: (text) => { turn.reasoning += text; turn.received += text.length }
    })
      .catch((e) => {
        // 用户点"停止"不是错误，别把 AbortError 当失败显示出来
        if (e?.name === 'AbortError') {
          if (!turn.answer) messages.value.pop()
          return
        }
        turn.error = true
        turn.answer = e?.message || 'AI 调用失败'
      })
      .finally(() => {
        turn.thinking = false
        turn.done = true
        asking.value = false
        askAbort = null
        // 最后一帧直接渲染（不再等节流）：这一帧之后没有 delta 了
        turn.html = renderAnswer(turn.answer)
        // 这一轮收完了（成功失败都算）。**确认框里「重新生成」用它**把新答案接回去。
        opts.onDone?.(turn)
      })
  })
  // 真的发起了
  return true
}

/**
 * 停掉正在流的那一轮。
 *
 * **只 abort 请求，不动界面状态**：`submitAsk` 的 `.finally` 里会把 `asking`
 * 复位成 false、把那一轮标成 done —— abort 走的是同一个 finally 路径，
 * 所以这里不需要自己收尾（自己再收一遍反而会和 finally 抢）。
 */
function cancelAsk() {
  askAbort?.abort()
}

/**
 * 把一段 markdown 渲染成 HTML。**用正文编辑器自己的解析器** ——
 * 另找一个 markdown 渲染器就是两套渲染（标题层级、表格、代码块全对不上）。
 */
function renderAnswer(md) {
  if (!md) return ''
  const doc = docOf(md)
  return doc ? htmlOf(doc) : ''
}

/**
 * 流式期间重渲染的节流（毫秒）。
 *
 * ⚠️ **每个 delta 都重渲染一次会卡**：解析 + 序列化一次要几毫秒，而 delta 是
 * 每几十毫秒来一个 ⇒ 每秒几十次，界面跟着顿。150ms 一次（约 7 帧/秒），
 * 看着仍是"边流边出字"，但不卡。
 */
const RENDER_THROTTLE = 150
let renderTimer = null
let renderTarget = null

function scheduleRender(turn) {
  renderTarget = turn
  if (renderTimer) return
  renderTimer = setTimeout(() => {
    renderTimer = null
    const t = renderTarget
    renderTarget = null
    if (t) t.html = renderAnswer(t.answer)
  }, RENDER_THROTTLE)
}

  return {
    askOpen,
    askInput,
    askW,
    askWidth,
    resetAskWidth,
    asking,
    messages,
    askBodyRef,
    canAsk,
    guideQuestions,
    submitAsk,
    askNow,
    cancelAsk,
    onGripDown,
    loadGuideQuestions
  }
}
