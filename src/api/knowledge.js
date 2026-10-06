import request from '../utils/request'

// ============ 知识点 ============

export function getTopicList(params) {
  return request.get('/kp/topic/list', { params })
}

export function getTopicById(id) {
  return request.get(`/kp/topic/${id}`)
}

export function createTopic(data) {
  return request.post('/kp/topic', data)
}

export function updateTopic(data) {
  return request.put('/kp/topic', data)
}

export function deleteTopic(id) {
  return request.delete(`/kp/topic/${id}`)
}

export function addTopicRelation(topicId, relatedTopicId) {
  return request.post(`/kp/topic/${topicId}/relation/${relatedTopicId}`)
}

export function removeTopicRelation(topicId, relatedTopicId) {
  return request.delete(`/kp/topic/${topicId}/relation/${relatedTopicId}`)
}

// ============ 标签（独立主表，按 user 隔离） ============

// 获取当前用户的所有 tag（含孤儿：不被任何 topic 引用的 tag）
export function getTagList() {
  return request.get('/kp/tag/list')
}

// 重命名 tag。后端 KpTagRenameReq 只有 { oldName, newName } 两个字段，
// 按**名字**定位（不按 id）；原来这条注释写的 "oldCategory::oldName → newCategory::newName"
// 是更早一版带分类前缀的签名，后端早就改了。
export function renameTag(data) {
  return request.put('/kp/tag/rename', data)
}

// 删除 tag：同时清掉所有 topic 关联（topic 本身不删）
export function deleteTag(id) {
  return request.delete(`/kp/tag/${id}`)
}

// ============ 知识目录（树的骨架） ============

// 获取当前用户的所有目录
export function getCategoryList() {
  return request.get('/kp/category/list')
}

export function createCategory(data) {
  return request.post('/kp/category', data)
}

// 重命名目录：按 **id** 定位（{ id, newName }），不是按名字。
// 名字是可变字段，拿它当键等于"改名那一刻就换了身份证"；允许同名之后更是认不出改的是哪个。
// 同层重名由 DB 的 uk_user_parent_name 兜底。
export function renameCategory(data) {
  return request.put('/kp/category/rename', data)
}

// 删除目录：其下知识点回到"未归类"（知识点本身不删）
export function deleteCategory(id) {
  return request.delete(`/kp/category/${id}`)
}
