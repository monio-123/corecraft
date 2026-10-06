import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { authHeaders, clearAuth, hasToken } from './auth'

const service = axios.create({
  baseURL: '/api',
  timeout: 5000
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

// 响应拦截器
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
          notifyError(error.response?.data?.message || `请求失败（${error.response.status}）`)
      }
    } else {
      // 无 response = 请求根本没到服务端：断网 / 后端没起 / 5 秒超时（timeout: 5000）。
      // 这三种和 5xx 一样之前是静默的。
      notifyError('网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default service 
