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

// ⚠️ **知识点关联的两个接口封装删掉了**（2026-10-21）：前端从上线到现在
// 没有建立关联的入口，`kp_topic_relation` 表恒为 0 条 ⇒ 这两个封装零调用。
// **后端接口还在**（`POST /kp/topic/{id}/relation/{rid}` / `DELETE` 同款），
// 真要做关联功能时按上面那两行的样子接回去即可（后端一行没动）。
// 别在没有任何调用方的时候留着"以后也许用得上"的封装 —— 那是死的。

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

// 批量导入目录树：一次提交整棵树（{ nodes: [{ name, children }] }），
// 服务端在一个事务里建完 —— 逐个建就是 N 次请求，中途失败留半棵树。
export function importCategories(data) {
  return request.post('/kp/category/import', data)
}

// 重命名目录：按 **id** 定位（{ id, newName }），不是按名字。
// 名字是可变字段，拿它当键等于"改名那一刻就换了身份证"；允许同名之后更是认不出改的是哪个。
// 同层重名由 DB 的 uk_user_parent_name 兜底。
export function renameCategory(data) {
  return request.put('/kp/category/rename', data)
}

// 移动目录到新父级：{ id, parentId }，parentId 传 null / 0 = 移到顶层。
// 落库只是改这一个字段——下级目录和知识点都靠引用，自动跟随，不用搬数据。
// 成环（移到自己的下级）和目标下重名由后端挡，前端的置灰只是提前告知。
export function moveCategory(data) {
  return request.put('/kp/category/move', data)
}

// 删除目录：**整棵子树一并删**；子树里每一层的知识点都回到"未归类"（知识点本身不删）
export function deleteCategory(id) {
  return request.delete(`/kp/category/${id}`)
}
