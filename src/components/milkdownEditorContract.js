/**
 * 编辑器的**接口契约**（2026-11-11 收敛）。
 *
 * ## 为什么要有这个文件
 *
 * `MilkdownEditor.vue`（Provider 薄封装）与 `MilkdownEditorBody.vue`（真正的实现）
 * 是一对父子：父包一层 `MilkdownProvider` 并把方法转发给子。
 *
 * 父的 props / emits **必须和子的完全一致** —— 父只是把收到的再递下去。
 * 以前两边各写一份 `defineProps({...})` / `defineEmits([...])`，
 * **逐字相同**：4 个 prop 的类型与默认值、5 个 emit 的事件名。
 *
 * ⇒ 抄两份的代价不是"多几行"：**改一个 prop 的默认值只改一处就分裂**
 * —— 父传下去的和子认的不是同一个，症状是"某个开关传了没反应"
 * （不报错，只是那一条路不通）。
 *
 * ⇒ 现在契约只有**这一份**，两边都从这里取。
 */

/**
 * 编辑器的 props 定义。父与子共用。
 */
export const editorProps = {
  modelValue: { type: String, default: '' },
  /**
   * 工具栏显不显示。**由使用方持有**（v-model:show-toolbar），
   * 因为开关按钮画在编辑器外面的顶栏上，状态得和使用方那份是同一份。
   */
  showToolbar: { type: Boolean, default: false },
  /**
   * 目录树（`utils/categoryTree.js` 的 toCategoryTree 产物），「设为目录链接」那个选择器用。
   *
   * **由使用方传进来，编辑器自己不取**：`components/` 里的组件一处都不 import `views/`
   * 下的 store（这是本项目现成的分层规矩），目录数据住在 `views/corecraft-web/`，
   * 取数得由使用方拿、算好递进来。
   *
   * 空数组 = 没有目录可引，「设为目录链接」按钮直接置灰——不是"点了没反应"。
   */
  categoryTree: { type: Array, default: () => [] },
  /**
   * 「选区提问」这个能力**能不能用**（通常是 AI 开没开）。
   *
   * **由使用方传进来，编辑器自己不问**：编辑器是通用组件，不该知道 AI 的开关在哪
   * （那是 useAi 的事，且 useAi 住在 views/ 下 —— components/ 不该依赖它）。
   * 关着的时候不弹那个浮层：没开的人不该看见 AI 的影子。
   */
  askEnabled: { type: Boolean, default: false }
}

/**
 * 编辑器的事件。父与子共用。
 *
 * - `update:modelValue` —— v-model 双向；
 * - `submit` —— 正文提交；
 * - `ready` —— 编辑器视图建好、正文已渲染完（外层靠它知道"现在量得到最终高度了"）；
 * - `outline` —— 正文里的标题结构（右侧大纲栏用）；
 * - `ask-ai` —— 正文里选中了一段 + 补了一句问题 ⇒ 交给上层去问 AI。
 *
 * ⚠️ **没有 `update:showToolbar`**：工具栏开关画在**父层**（那一排按钮不在编辑器里），
 * 状态由父层持有，编辑器只读。以前声明了却从不 emit，三个使用方却写的是
 * `v-model:show-toolbar` —— 看着是双向绑定，实际是单向，哪天在编辑器内部改它
 * 父层那份不会动、且不报错。
 */
export const editorEmits = ['update:modelValue', 'submit', 'ready', 'outline', 'ask-ai', 'export-md', 'export-file']
