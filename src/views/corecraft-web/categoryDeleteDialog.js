/**
 * 「删目录」对话框的**交互流**（状态 + 发起 + 出口；从 `useCorecraftWebStore.js` 拆出，2026-11-15）。
 *
 * 职责边界：这里管"问用户删不删、删的时候连带不连带"；真删（`deleteCategory`
 * 数据操作、缓存刷新、dataVersion）仍在 store。
 */
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteCategory } from './useCorecraftWebStore'
import { getCategoryDeletePreview } from '../../api/knowledge'

/**
 * 「删目录」那个对话框的状态（**模块级一份**，不是组件里一份）。
 *
 * <p>为什么是模块级而不是 `CategoryRowMenu` 里：那个菜单在树上出现**三次**
 * （书名那行、章行、节行），每个都挂一个弹窗就是三个实例；
 * 而弹窗**必须只有一个**（它遮着整棵树）。
 * ⇒ 状态收在这里，`DeleteCategoryDialog.vue` 挂一次，菜单只负责"发起"。
 */
export const deleteCategoryState = reactive({
  visible: false,
  /** 要删的目录（{ id, name }） */
  category: null,
  /** 服务端现查的预检结果 { subCategoryCount, topicCount } */
  preview: null,
  saving: false,
  /** 本次调用的 resolve（用户答了什么） */
  resolver: null
})

/**
 * 打开「删目录」对话框，返回用户的选择。
 *
 * @param {object} c 要删的目录
 * @param {object} preview 服务端预检结果
 * @returns {Promise<boolean>} true = 确认删了，false = 取消
 */
export function openDeleteCategoryDialog(c, preview) {
  // 同一时刻只该有一个对话框：上一个没结掉就再开一个，前一个的 resolve 就丢了
  // ⇒ 上一个直接判成"取消"。
  if (deleteCategoryState.resolver) {
    deleteCategoryState.resolver(false)
  }
  deleteCategoryState.category = c
  deleteCategoryState.preview = preview
  deleteCategoryState.visible = true
  return new Promise((resolve) => {
    deleteCategoryState.resolver = resolve
  })
}

/** 对话框的出口：把用户的答复交回给发起方。**只有这一处改 visible** */
function closeDeleteCategoryDialog(answer) {
  const resolve = deleteCategoryState.resolver
  deleteCategoryState.visible = false
  deleteCategoryState.category = null
  deleteCategoryState.preview = null
  deleteCategoryState.resolver = null
  resolve?.(answer)
}

/** 用户点了「取消」 */
export function cancelDeleteCategory() {
  closeDeleteCategoryDialog(false)
}

/**
 * 用户点了「删除」：按勾选决定要不要连带删知识点。
 *
 * @param {boolean} cascadeTopics true = 连子树里的知识点一起删（软删）
 */
export async function submitDeleteCategory(cascadeTopics) {
  const c = deleteCategoryState.category
  if (!c || deleteCategoryState.saving) return
  deleteCategoryState.saving = true
  try {
    await deleteCategory(c.id, cascadeTopics)
    ElMessage.success('已删除')
    closeDeleteCategoryDialog(true)
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）
    closeDeleteCategoryDialog(false)
  } finally {
    deleteCategoryState.saving = false
  }
}

/**
 * 删除目录的交互流。入口在目录行「⋯」菜单里的"删除目录"。
 * 返回是否真的执行了：调用方据此决定要不要把整条树收走（删的可能正是它锚定的那个节点）。
 *
 * ## 为什么这个确认框要**自己画**而不是走 `confirmAction`
 *
 * 它要多一个**真实选项**：「同时删除 N 条知识点」（默认不勾）。
 * 统一确认框只接受一句纯文本，塞不进复选框 —— 这不是文案自由度，是功能。
 * ⇒ 自己画对话框（`DeleteCategoryDialog.vue`），但标题 / 正文 / 按钮 / 无图标
 * **全部照 `confirmAction` 那一套**，弹窗里除了这个复选框不放别的东西
 * （2026-11-13 用户口径，见 `utils/confirm.js`）。
 *
 * ## 数量为什么来自服务端
 *
 * 复选框标签里的「N 条」由服务端现查（`delete-preview`）：前端那份结构索引是
 * **当前关键词下的缓存快照**（用户搜着词时数出来偏小），还会过期。
 */
export async function confirmDeleteCategory(c) {
  let preview
  try {
    const res = await getCategoryDeletePreview(c.id)
    preview = res?.data || null
  } catch {
    // 预检拉不到就**不让删**：数不清代价的操作不该在没有数字的情况下执行。
    ElMessage.error('拿不到这个目录的删除预览，先不删')
    return false
  }
  return openDeleteCategoryDialog(c, preview)
}
