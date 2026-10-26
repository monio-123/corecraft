import request from '../utils/request'

/**
 * 系统配置（后端 sys_config 表：一行一个「键 → 值」）。
 *
 * **三种值，同一张表**：
 * - 系统默认（user_id=0）：管理员在「系统管理 → 系统配置」里改；
 * - 我自己的（user_id=当前用户）：在业务界面拖滑块时写；
 * - 生效值 = 我自己的 > 系统默认 > 代码里的兜底。
 *
 * 键的格式是「命名空间.键名」（例 `ui.sideWidth`），前缀用来分域。
 */

/**
 * 我这一份的生效值 + 系统默认（偏好面板用）。
 *
 * `values` = 生效值（我自己的 > 系统默认），`defaults` = 系统默认。
 * 两个都要：面板要显示当前值，也要知道"恢复系统默认"之后该回到哪。
 *
 * ⚠️ **silent: true** —— 这个请求每次进页面都发（会话引导里拉一次），
 * 后端没上新版 / 后端挂了的时候不该每次都弹一条红字：
 * 偏好有本地兜底，页面照常用。详见 utils/request.js 的响应拦截器。
 */
export function getMyConfig(prefix) {
  return request.get('/sys/config/mine', { params: { prefix }, silent: true })
}

/**
 * 存我自己的覆盖（批量）。
 *
 * 批量是因为滑块一次拖动会产生一串中间值 —— 攒到松手再一次性提交，
 * 而不是每一格都打一个接口。
 */
export function saveMyConfig(items) {
  return request.put('/sys/config/mine', { items })
}

/**
 * 删掉我自己的覆盖（按键名前缀）= 恢复系统默认。
 */
export function clearMyConfig(prefix) {
  return request.delete('/sys/config/mine', { params: { prefix } })
}

/** 管理端：查行（scope=system 看系统默认，scope=user 看各人的覆盖） */
export function listConfig(params) {
  return request.get('/sys/config/list', { params })
}

/** 管理端：改系统默认（批量，user_id=0） */
export function saveSystemConfig(items) {
  return request.put('/sys/config', { items })
}

/**
 * 管理端：删一行（不传 userId = 删系统默认那行） */
export function deleteConfig(key, userId) {
  return request.delete('/sys/config', { params: { key, userId } })
}

/**
 * 整棵配置树（KV 的分组与说明书）。
 *
 * **读 = 登录即可**：树是元数据（有哪些项、叫什么、范围多大），不含值。
 * 业务侧的偏好面板也要读它——面板上那几行叫什么、能拖到多少，是它决定的。
 *
 * ⚠️ **silent: true** —— 会话引导每次都拉它，后端没上新版时不该每次都弹一条红字。
 */
export function getConfigNodes() {
  return request.get('/sys/config/node/list', { silent: true })
}

/** 管理端：新建一个配置节点（组或一项） */
export function createConfigNode(data) {
  return request.post('/sys/config/node', data)
}

/** 管理端：改一个配置节点（短名 / 父节点 / 值类型不可改） */
export function updateConfigNode(data) {
  return request.put('/sys/config/node', data)
}

/** 管理端：删一个配置节点（连它的子树和它们各自的键值对一起删） */
export function deleteConfigNode(id) {
  return request.delete(`/sys/config/node/${id}`)
}
