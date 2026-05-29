import request from '../utils/request'

export function getMyProfile() {
  return request.get('/sys/user/me/profile')
}

export function updateMyProfile(data) {
  return request.put('/sys/user/me/profile', data)
}

export function updateMyPassword(data) {
  return request.put('/sys/user/me/password', data)
}

export function uploadMyAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sys/user/me/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getMyMenuTree() {
  return request.get('/sys/user/me/menu-tree')
}
