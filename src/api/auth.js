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
      return Object.entries(data)
        .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
        .join('&')
    }]
  }).then(response => response.data)
}
