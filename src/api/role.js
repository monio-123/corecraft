import request from '../utils/request'

export function getRoleList(params) {
  return request.get('/role/list', { params })
}

export function getRoleDetail(params) {
  return request.get('/role', { params })
}

export function createRole(data) {
  return request.post('/role', data)
}

export function updateRole(data) {
  return request.put('/role', data)
}

export function deleteRole(id) {
  return request.delete(`/role/${id}`)
}
