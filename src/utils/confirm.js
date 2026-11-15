import { ElMessageBox } from 'element-plus'

/**
 * 确认框：**全站唯一出口**。调用方只给一个动作名，其余一律由这里生成。
 *
 * ⚠️ **口径（2026-11-13 用户定，别再"优化"回来）**：弹窗里只放三样东西 ——
 * 标题 = 动作名、一句 `你确认要 X 么？`、两个按钮「取消 / 确认」。
 * 不许塞：后果说明（"已经写好的保留"/"可以从数据库恢复"）、数量（"删除 2 条"）、
 * 对象名（"删除「张三」"）、界面上看得见的事实（"还有 3 道未作答"）、
 * 自定义按钮文案（"算了"/"暂停"/"继续答题"）、警告图标（`type: 'warning'`）。
 * 依据 brove-shared §8：**确认框是在问一个决定，不是在解释这个决定**。
 * 用户点的是某一行上那个明确的动作按钮，删的是谁、还剩几条，界面上本来就看得见；
 * 弹窗再复述一遍就是口水话，而且每个功能各写各的 ⇒ 全站十几种长相。
 *
 * ⚠️ **签名故意只收一个参数**：以前是 `confirmDanger(title, text, confirmText)`，
 * 三个口子刚好够每个调用方各写一句"贴心解释"、一个"贴切按钮名"。
 * 现在没有任何参数能带进额外文案 ⇒ 想犯这个错也犯不了。
 *
 * 另一件必须留在这里的事：Element Plus 的 `confirm` 在点「取消」/ 按 Esc 时是
 * **reject**，直接 `await` 不接住就是一条 unhandled rejection（控制台报错、界面没反应）。
 * 统一收口后取消变成 `false`，调用方一律 `if (!(await confirmAction('X'))) return`。
 *
 * @param {string} action 动作名，例「暂停任务」「删除用户」「交卷」
 * @returns {Promise<boolean>} true = 确认，false = 取消
 */
export async function confirmAction(action) {
  try {
    await ElMessageBox.confirm(`你确认要${action}么？`, action, {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}
