import request from '../utils/request'

export function getUserPage(params) {
  return request.get('/sys/user/page', { params })
}

export function createUser(data) {
  return request.post('/sys/user', data)
}

export function updateUser(data) {
  return request.put('/sys/user', data)
}

export function deleteUser(id) {
  return request.delete(`/sys/user/${id}`)
}

export function assignUserRole(userId, roleId) {
  return request.put(`/sys/user/${userId}/role`, { roleId })
}
