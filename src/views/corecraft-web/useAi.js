import { ref, computed } from 'vue'
import { createLoadSlot, loadOnce, resetSlot } from '../../utils/lazyLoad'
import { onAuthChange } from '../../utils/auth'
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
  listAiQuestions as fetchQuestions,
  listAiVendors as fetchVendors,
  refreshAiModels as refreshModels
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
/** 引导问题，来自配置树上的「引导问题」那组。key 是 `key:contexts` 复合值，前端只当不透明字符串 */
export const aiQuestions = ref([])
/** 厂商清单（厂商 + 各自的模型）。**全局的**（字典表没有 user_id），跟着登录态作废 */
export const aiVendors = ref([])
/** 当前选中的 API id。设置页的"当前"标记直接读它，不用再包一个 computed */
export const currentEndpointId = ref(readCurrentId())

const aiSlot = createLoadSlot()

function readCurrentId() {
  const v = localStorage.getItem(CURRENT_KEY)
  return v ? Number(v) : null
}

/**
 * 幂等 + **共享在途请求** + 失败不清标志位。
 *
 * 共享在途那套在 `utils/lazyLoad.js`（和会话引导、store 的四份缓存同一份实现）——
 * 以前这里是手写的一份，store 那几份又漏了，于是壳和首页同一时刻各拉一遍接口。
 */
export async function ensureAiConfigLoaded(force = false) {
  return loadOnce(aiSlot, force, async (isCurrent) => {
    // 开关和列表是一次装载的：分开 await 会让首页多等一个 RTT
    const [configRes, endpointRes] = await Promise.all([fetchAiConfig(), fetchEndpoints()])
    const config = configRes?.data || { enabled: false }
    const endpoints = endpointRes?.data || []
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) {
      aiConfig.value = config
      aiEndpoints.value = endpoints
    }
    // 选中的那条可能已经被删了，兜回第一条能用的
    if (!endpoints.some((e) => e.id === currentEndpointId.value)) {
      const first = endpoints.find((e) => e.ready) || endpoints[0]
      useEndpoint(first ? first.id : null)
    }
    return { config, endpoints }
  })
}

export async function saveAiConfig(data) {
  // 用后端回的形状覆盖：只有后端算得准 enabled
  const res = await saveApi(data)
  aiConfig.value = res?.data || aiConfig.value
  // 缓存里的那份也得是最新的：**只改 `aiConfig` 不改缓存的话**，下一次
  // ensureAiConfigLoaded() 命中 'ready' 会把**旧**的那份原样返回
  // （loadOnce 直接 return slot.value），于是开关刚打开、过一会儿又变回关的，
  // 刷新一下才对。
  //
  // 改法用**重拉**而不是手工改缓存：缓存归 loadOnce 管（它的 value 是什么形状
  // 是它自己的事），手工塞一份进去等于在别处复制那条规则。
  // 和下面增删改 API 三个函数是同一个写法。
  await ensureAiConfigLoaded(true)
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

// ---------- 厂商与模型清单（全局字典） ----------
//
// **和 AI 配置分开装**：厂商清单是全局的（字典表没有 user_id），
// 跟"我配了哪些 API"不是一回事，生命周期也不同（跟着登录态作废，不跟着端点增删作废）。

const vendorSlot = createLoadSlot()

export async function ensureAiVendorsLoaded(force = false) {
  return loadOnce(vendorSlot, force, async (isCurrent) => {
    const res = await fetchVendors()
    const next = res?.data || []
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) aiVendors.value = next
    return next
  })
}

/**
 * 拉厂商最新模型清单（后端写回字典），把这个厂商换成最新的那份。
 *
 * @param payload { vendor, baseUrl?, apiKey?, endpointId? }
 * @returns 厂商最新的模型清单
 */
export async function refreshVendorModels(payload) {
  const res = await refreshModels(payload)
  const vendor = res?.data
  if (vendor) {
    const exists = aiVendors.value.some((v) => v.code === vendor.code)
    aiVendors.value = exists
      ? aiVendors.value.map((v) => (v.code === vendor.code ? vendor : v))
      : [...aiVendors.value, vendor]
  }
  return vendor
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
 * @param questionKey 引导问题的短名（`key:contexts`）；用户自己敲的题不传
 * @param endpointId **这次用哪个 API**。不传 = 用全局当前选中的那条；
 *        传了就用传的（生成弹窗里可以临时换一个模型，不动全局选择）
 * @param thinking 这次要不要关掉模型的思考（`false` = 关；不传 = 不干预）
 * @param material 这次要处理的素材（用户自己敲的一段文字）。不传 = 用这次指定的知识。
 *   给了就用给的，**两者不同时出现**——出题不必被锁在这条知识里
 * @param topicIds 这次处理**哪几条**知识（考一个目录时）。传了它就以它为准，topicId 忽略。
 *   归属由服务端按登录态收
 * @param history 之前几轮 `[{question, answer}]`。**协议无状态，不带过去模型就不记得**
 *   （曾经就是这样：抽屉里明明是同一个对话，AI 却答"我没有上下文记忆"）。
 *   带多少轮由后端 `HISTORY_TURNS` 决定，这儿只管把已完成的轮次交上去
 * @returns {Promise<string>} 这次收到的**完整正文**（流式层攒好的，调用方不用自己拼）
 */
export async function ask(
  question,
  { topicId, topicIds, material, history, onPrompt, onDelta, onReasoning, signal, questionKey, endpointId, thinking } = {}
) {
  if (!aiEnabled.value) {
    throw new Error('AI 开关没打开')
  }
  // **这次用哪个 API**：显式指定的优先（生成弹窗里可以临时换一个模型），
  // 否则用全局当前选中的那条。**两种都要先看配全没有**——
  // 配不全的端点调过去只会得到一句「还没配全」。
  const ep = endpointId != null
    ? aiEndpoints.value.find((e) => e.id === endpointId)
    : currentEndpoint.value
  if (!ep?.ready) {
    throw new Error('还没选好 API')
  }
  return streamChat(
    { topicId, topicIds, question, endpointId: ep.id, questionKey, material, history: history || [], thinking },
    { onPrompt, onDelta, onReasoning },
    signal
  )
}

// ---------- 引导问题（字典） ----------
// 独立于配置加载：配置拉不到（没配 AI）时不该顺带把引导问题也拖垮，
// 而且引导问题只服务详情页那一个抽屉，不进首页的壳级预加载。

const questionsSlot = createLoadSlot()

export async function ensureAiQuestionsLoaded(force = false) {
  return loadOnce(questionsSlot, force, async (isCurrent) => {
    const res = await fetchQuestions()
    const next = res?.data || []
    // 只有最新发起的那次才落缓存（见 lazyLoad 的「版本号」）
    if (isCurrent()) aiQuestions.value = next
    return next
  })
}

/**
 * 换账号 / 重新登录时把 AI 这份状态清掉。
 *
 * ⚠️ **模块级的内存状态不会跟着 localStorage 一起清**（`clearAuth()` 只清得到
 * localStorage）。不作废的话：**登进来看到的是上一个账号的开关和 key** ——
 * 症状是"AI 开关明明开着，界面显示关的；进一次设置页或刷新一下才对"。
 *
 * 以前这活儿挂在业务壳的"退出登录"回调上（`AppLayout.onSignedOut`），
 * 于是**重新登录那条路没人管** —— 登出清了、登进没清。
 * 现在挂在**登录态本身**上（`onAuthChange`：登录成功 / 退出 / 401 / 改完密码都会发），
 * 两条路一起管住 —— 和 `useCorecraftWebStore` / `utils/session` 的做法一致
 * （它们也是直接订阅、不额外导出一个 reset 函数）。
 */
onAuthChange(() => {
  aiConfig.value = { enabled: false }
  aiEndpoints.value = []
  aiQuestions.value = []
  aiVendors.value = []
  currentEndpointId.value = null
  resetSlot(aiSlot)
  resetSlot(questionsSlot)
  resetSlot(vendorSlot)
})