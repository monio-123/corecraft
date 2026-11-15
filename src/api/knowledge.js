import request from '../utils/request'

// ============ 知识点 ============

export function getTopicList(params) {
  return request.get('/kp/topic/list', { params })
}

/**
 * 结构索引：全部知识点的**轻量投影，不含正文**。
 *
 * <p>目录树（知识点那一层要显示标题）、各处按类型/标签分类计数 ——
 * 这些都要"全集"，但一条正文都不需要。正文那 90 万字符是列表接口慢的真正原因
 * （实测：读全文 4416ms / 不读正文 78ms）。
 *
 * @param {object} params { q } —— q 是关键词，**服务端筛**（正文匹配只有服务端能做）
 */
export function getTopicIndex(params) {
  return request.get('/kp/topic/index', { params })
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

/**
 * 设置单条知识点是否参与掌握度（**三态**）。
 *
 * <p>⚠️ **为什么单独一个端点**（2026-11-12）：通用 `PUT /kp/topic` 的 null 是
 * "没这个字段"（不修改），而这里的 null 是**"恢复继承类型默认"**——两件事在
 * JSON 里长得一模一样，只能分成两个接口，否则"恢复继承"永远发不出去。
 *
 * <p>三态：true=参与 / false=明确不参与 / null=恢复继承类型默认。
 *
 * @param {number} id
 * @param {boolean|null} enabled
 */
export function setTopicMastery(id, enabled) {
  return request.put(`/kp/topic/${id}/mastery`, { enabled })
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

/**
 * 「删这个目录会连带删掉什么」的**预检**（不删任何东西）。
 *
 * <p>确认框里那个「N 条知识点」必须来自服务端现查 —— 前端那份结构索引是
 * **当前关键词下的快照**，还会过期。数错了用户就是"以为删 3 条、实际删 30 条"。
 *
 * @param {number} id 目录 id
 * @returns {Promise<{categoryId:number, categoryName:string, subCategoryCount:number, topicCount:number}>}
 */
export function getCategoryDeletePreview(id) {
  return request.get(`/kp/category/${id}/delete-preview`)
}

/**
 * 删除目录：**整棵子树一并删**。
 *
 * @param {number} id 目录 id
 * @param {boolean} [cascadeTopics=false] **true = 连这棵子树里的知识点一起删**（软删）。
 *   false = 子树里每一层的知识点都回到"未归类"（知识点本身不删，默认行为）。
 *   <p>为什么给这个开关：删目录后的待归类是**堆积源** —— 目录树改了，
 *   里面的知识点却整批掉进"待归类"桶里，越整理越脏。而批量清理待归类
 *   在服务端分页下语义不清（"全选"是当前页还是全部？）。
 *   ⇒ 从**源头**堵：删目录时让用户明确选择"这些知识点一起删掉"。
 */
export function deleteCategory(id, cascadeTopics = false) {
  return request.delete(`/kp/category/${id}`, {
    params: { cascadeTopics: cascadeTopics ? 'true' : 'false' }
  })
}
