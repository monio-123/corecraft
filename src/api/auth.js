import axios from 'axios'

export function login(username, password) {
  return axios.post('/api/oauth2/token', {
    grant_type: 'password',
    username,
    password
  }, {
    headers: {
      'Authorization': 'Basic Y29yZWNyYWZ0OmNvcmVjcmFmdA==',
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    transformRequest: [(data) => {
      // 将对象转换为form-urlencoded格式
      return Object.entries(data)
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join('&')
    }]
  }).then(response => {
    console.log('Login response:', response.data)
    return response.data
  }).catch(error => {
    console.error('Login error:', error)
    console.error('Error config:', error.config)
    console.error('Error response:', error.response)
    console.error('Error message:', error.message)
    throw error
  })
} 