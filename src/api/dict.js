import request from '../utils/request'

export function getDictTypeList(params) {
  return request.get('/dict/type/list', { params })
}

export function createDictType(data) {
  return request.post('/dict/type', data)
}

export function updateDictType(data) {
  return request.put('/dict/type', data)
}

export function deleteDictType(id) {
  return request.delete(`/dict/type/${id}`)
}

export function getDictItemList(params) {
  return request.get('/dict/item/list', { params })
}

export function createDictItem(data) {
  return request.post('/dict/item', data)
}

export function updateDictItem(data) {
  return request.put('/dict/item', data)
}

export function deleteDictItem(id) {
  return request.delete(`/dict/item/${id}`)
}
