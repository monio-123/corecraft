import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearAuth, getToken, getTokenType } from './auth'

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
    
    const token = getToken()
    const tokenType = getTokenType()
    if (token && tokenType) {
      config.headers['Authorization'] = `${tokenType} ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

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
          // 清除登录态并跳转到登录页
          clearAuth()
          router.push('/login')
          ElMessage.error('登录已过期，请重新登录')
          break
        default:
          // ElMessage.error(error.response.data.error_description || '请求失败')
      }
    } else {
      // ElMessage.error('网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default service 
