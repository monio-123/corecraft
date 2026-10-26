import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { authHeaders, clearAuth, hasToken } from './auth'

const service = axios.create({
  baseURL: '/api',
  // 5 秒对「点一下就该回」的接口够用，但它是**全局**值：一个稍慢的请求
  //（后端刚启动时 Druid 初始化要几十秒、或某条慢查询）会被它掐断。
  // 而超时抛的是 ECONNABORTED、**没有 response**，和「断网」落在同一个 else 分支里 ——
  // 于是一个纯粹等太久的请求被报成「网络错误，请稍后重试」，用户去查网线，
  // 查不出任何东西。30 秒足够覆盖冷启动，又不会让真挂掉的请求一直转圈。
  // 天然就慢的请求（文件上传）自己再放宽，见 api/file.js。
  timeout: 30000
})

// crypto.randomUUID 仅在安全上下文（HTTPS / localhost）可用，HTTP + IP 访问时不存在
function generateTraceId() {
  const c = globalThis.crypto
  const id = typeof c?.randomUUID === 'function'
    ? c.randomUUID()
    : `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`
  return id.replace(/-/g, '').substring(0, 16)
}

// 请求拦截器
service.interceptors.request.use(
  config => {
    config.headers['X-Trace-Id'] = generateTraceId()

    // 登录请求不需要添加token
    if (config.url === '/oauth2/token') {
      return config
    }

    Object.assign(config.headers, authHeaders())
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 同一条错误在短时间内只弹一次。
// 启动时 AppLayout / AppHomeView 会并行打 5 个请求（菜单树 / 资料 / 权限 / 知识点 / 目录），
// 后端一挂就是 5 个 5xx 同时回来，不收敛的话用户看到的是 5 条一模一样的「请求失败」——
// 比不提示还糟。401 那条不需要这个：它靠 hasToken() 天然幂等（第一个清完 token，
// 后面的自动降级为静默 reject），5xx 没有这种天然收敛。
let lastErrorMsg = ''
let lastErrorAt = 0
function notifyError(msg) {
  const now = Date.now()
  if (msg === lastErrorMsg && now - lastErrorAt < 2000) return
  lastErrorMsg = msg
  lastErrorAt = now
  ElMessage.error(msg)
}

// 响应拦截器。
//
// ⚠️ **这里是全站唯一的错误提示出口**，调用方不要在自己的 catch 里再弹一次。
// 一个失败弹两条、内容还不一样（「网络错误，请稍后重试」+「文件上传失败」）
// 是最糟的形态：用户既不知道该信哪条，也不知道下一步做什么。
// 走了本实例的请求 → 提示归这里；调用方的 catch 只负责回滚状态（撤掉半插入的节点、清 loading）。
// 反过来，**不走本实例的请求（原生 fetch 的 AI 流、剪贴板操作）仍然要自己弹** ——
// 那种失败没人统一弹，漏了就是彻底静默。
//
// ⚠️ **`silent: true` 的请求不弹提示**（少数几处，见各自调用点的说明）：
// 界面偏好在后端没上新版 / 后端挂了的时候，**每次进页面都弹一条红字**并不能帮上忙 ——
// 偏好有本地兜底、页面照常用，弹提示只会变成噪音。真正的故障有别的地方报。
service.interceptors.response.use(
  response => {
    const data = response.data
    if (data && typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, 'code')) {
      if (data.code !== 200) {
        const msg = data.message || '请求失败'
        ElMessage.error(msg)
        return Promise.reject(new Error(msg))
      }
    }
    return data
  },
  error => {
    const silent = error.config?.silent
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // 清登录态 + 跳登录页 + 提示是一次性副作用，必须幂等：
          // token 过期时**在途请求会同时收到多个 401**（bootstrapSession 并行 3 个，
          // AppHomeView/AppLayout 并行 5 个），不收敛的话用户会看到 N 个「登录已过期」。
          // 判据取「本地还有没有 token」而不是标志位：第一个 401 执行 clearAuth 后，
          // 后续 401 自动降级为静默 reject，不重复提示、不重复跳转。
          // 不用标志位 → 重新登录后再次过期照样正常处理，无需手动复位。
          if (hasToken()) {
            clearAuth()
            router.push('/login')
            ElMessage.error('登录已过期，请重新登录')
          }
          break
        default:
          // 之前这里是注释掉的，等于 5xx 全静默 reject：后端挂了界面不报错、
          // 不跳转，只是各处数据停在空状态（用户看到的是"显示未登录但还能建目录"，
          // 根本猜不到是后端的问题）。字段用 message 和上面成功分支的口径一致。
          if (!silent) notifyError(error.response?.data?.message || `请求失败（${error.response.status}）`)
      }
    } else if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      // 超时和断网是两回事：请求**确实发出去了**，只是回来得太晚。
      // 合成一句「网络错误」会让用户去查网线 / 重启路由，而真正该做的是稍后重试。
      if (!silent) notifyError('请求超时，请稍后重试')
    } else {
      // 无 response 且不是超时 = 请求根本没到服务端：断网 / 后端没起。
      if (!silent) notifyError('网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default service 
