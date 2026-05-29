import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearAuth, getToken, getTokenType } from './auth'

const service = axios.create({
  baseURL: '/api',
  timeout: 5000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
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
