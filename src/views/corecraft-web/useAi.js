import { ref, computed } from 'vue'
// api 层的方法统一改名（fetchXxx / saveApi / createApi…）：本文件对外导出的同名封装
// （saveAiConfig）和它们在模块顶层撞名，会变成 "Identifier has already been declared"
// ——白屏，且只在控制台报错。统一改名之后，"这一行属于哪一层"在文件里是一眼能看出来的。
import {
  getAiConfig as fetchAiConfig,
  listAiEndpoints as fetchEndpoints,
  saveAiConfig as saveApi,
  createAiEndpoint as createApi,
  updateAiEndpoint as updateApi,
  deleteAiEndpoint as deleteApi,
  streamChat,
  listAiQuestions as fetchQuestions
} from '../../api/ai'

/**
 * AI 能力的唯一入口：开关 + API 列表 + 调用，全在这一个模块里。
 *
 * **对外一律叫「API」不叫「接口」**（界面上是这么叫的，注释跟着界面对齐）：
 * 用户配的是一个具体的模型服务商地址，"接口"这个词项目里还用来指后端 HTTP 接口，
 * 混用时读者得自己猜这里说的是哪一种。
 *
 * ## 为什么单独一个文件，不进 useCorecraftWebStore
 *
 * 那边装的是**业务数据**（知识点 / 目录 / 标签），跟着壳一起加载、被 onAuthChange 一起作废。
 * AI 配置是**个人配置**：一个开关决定界面里所有 AI 入口出不出现，它跟着用户走，
 * 不跟着业务数据走。混进去会让"业务数据"和"个人偏好"两条生命周期搅在一起。
 *
 * ## 为什么"不开启就不显示"而不是"不开启就禁用"
 *
 * 关闭的语义是**这个功能对他不存在**，不是"它坏了"。所以界面各处一律用 `aiEnabled` 控制
 * `v-if`，不画那个按钮——没开启的人不该看见 AI 的影子，更不该点了之后被告知"未配置"。
 *
 * 两个字段分开，别混用：
 * - `enabled` —— 用户想不想用（控制按钮显不显示）
 * - `ready` —— 开了之后有没有配全的 API（决定调用前要不要拦一道）
 *
 * ## 「当前选中哪个 API」为什么存 localStorage 不落库
 *
 * 选哪个 API 是**当下的临时选择**，不是用户资料。存后端要多一列 + 一次更新，
 * 换来的只是"多设备同步一个下拉选择"，而这是个单人知识库，不值当。
 * 退出登录时 clearAuth() 会 localStorage.clear()，顺带清掉，不会把 A 的选择带给 B。
 */

const CURRENT_KEY = 'ai-current-endpoint'

const aiConfig = ref({ enabled: false })
// 列表直接给界面用（下拉、设置页列表），不再包一层 getter——那才是第 2 处重复
export const aiEndpoints = ref([])
/** 引导问题，来自字典 ai_topic_question。key 是 `key:contexts` 复合值，前端只当不透明字符串 */
export const aiQuestions = ref([])
/** 当前选中的 API id。设置页的"当前"标记直接读它，不用再包一个 computed */
export const currentEndpointId = ref(readCurrentId())

let loaded = false
let inflight = null

function readCurrentId() {
  const v = localStorage.getItem(CURRENT_KEY)
  return v ? Number(v) : null
}

/** 跟 store 里那几个 ensure 一个套路：幂等 + 共享在途请求 + 失败不清标志位 */
export async function ensureAiConfigLoaded(force = false) {
  if (loaded && !force) return { config: aiConfig.value, endpoints: aiEndpoints.value }
  if (inflight && !force) return inflight
  // 开关和列表是一次装载的：分开 await 会让首页多等一个 RTT
  inflight = Promise.all([fetchAiConfig(), fetchEndpoints()])
    .then(([configRes, endpointRes]) => {
      aiConfig.value = configRes?.data || { enabled: false }
      aiEndpoints.value = endpointRes?.data || []
      // 选中的那条可能已经被删了，兜回第一条能用的
      if (!aiEndpoints.value.some((e) => e.id === currentEndpointId.value)) {
        const first = aiEndpoints.value.find((e) => e.ready) || aiEndpoints.value[0]
        useEndpoint(first ? first.id : null)
      }
      loaded = true
      return { config: aiConfig.value, endpoints: aiEndpoints.value }
    })
    .finally(() => {
      // 必须清：不清的话首次失败会把 inflight 钉成一个 rejected promise，
      // 之后每次调用都导向它，配置永远加载不出来（和 #25 同一个坑）
      inflight = null
    })
  return inflight
}

export async function saveAiConfig(data) {
  // 用后端回的形状覆盖：只有后端算得准 enabled
  const res = await saveApi(data)
  aiConfig.value = res?.data || aiConfig.value
  loaded = true
  return aiConfig.value
}

export async function addAiEndpoint(data) {
  await createApi(data)
  const { endpoints } = await ensureAiConfigLoaded(true)
  // 新加的直接选中，省得用户还要再点一下
  useEndpoint(endpoints[endpoints.length - 1]?.id ?? null)
  return endpoints
}

export async function editAiEndpoint(data) {
  await updateApi(data)
  return ensureAiConfigLoaded(true)
}

export async function removeAiEndpoint(id) {
  await deleteApi(id)
  return ensureAiConfigLoaded(true)
}

export function useEndpoint(id) {
  currentEndpointId.value = id ?? null
  if (id) localStorage.setItem(CURRENT_KEY, String(id))
  else localStorage.removeItem(CURRENT_KEY)
}

/** 界面各处控制 AI 入口显不显示，一律用这个 */
export const aiEnabled = computed(() => Boolean(aiConfig.value.enabled))

/** 当前选中的那一条（可能不存在——列表被清空过） */
export const currentEndpoint = computed(
  () => aiEndpoints.value.find((e) => e.id === currentEndpointId.value) || null
)

/**
 * 就某条知识提问，**流式**。**零写入**：答完一个字都不回写到 kp_topic。
 *
 * "能不能调"的判据收敛在这里一处，界面各处不必各自再判一遍：
 * 开关开没开 → 用的那条自己配全没配全。
 *
 * @param onPrompt 提交给模型的完整消息，开流前给一次
 * @param onDelta 每收到一段正文调用一次
 * @param onReasoning 每收到一段思考过程调用一次（**非思考模型不会触发**）
 * @param signal  传 AbortSignal 可以中途取消
 * @param questionKey 引导问题的字典 value（`key:contexts`）；用户自己敲的题不传
 */
export async function ask(question, { topicId, onPrompt, onDelta, onReasoning, signal, questionKey } = {}) {
  if (!aiEnabled.value) {
    throw new Error('AI 开关没打开，去「侧边栏头像 → AI 设置」里启用')
  }
  if (!currentEndpoint.value?.ready) {
    throw new Error('还没选好 API，去「侧边栏头像 → AI 设置」里添加一个并选中')
  }
  return streamChat(
    { topicId, question, endpointId: currentEndpoint.value.id, questionKey },
    { onPrompt, onDelta, onReasoning },
    signal
  )
}

// ---------- 引导问题（字典） ----------
// 独立于配置加载：配置拉不到（没配 AI）时不该顺带把引导问题也拖垮，
// 而且引导问题只服务详情页那一个抽屉，不进首页的壳级预加载。

let questionsLoaded = false
let questionsInflight = null

export async function ensureAiQuestionsLoaded(force = false) {
  if (questionsLoaded && !force) return aiQuestions.value
  if (questionsInflight && !force) return questionsInflight
  questionsInflight = fetchQuestions()
    .then((res) => {
      aiQuestions.value = res?.data || []
      questionsLoaded = true
      return aiQuestions.value
    })
    .finally(() => { questionsInflight = null })
  return questionsInflight
}

/** 退出登录时跟着作废。AI 配置是个人偏好，A 登出后 B 不该接着用 A 的 key */
export function resetAiConfig() {
  aiConfig.value = { enabled: false }
  aiEndpoints.value = []
  aiQuestions.value = []
  questionsLoaded = false
  currentEndpointId.value = null
  loaded = false
  inflight = null
}