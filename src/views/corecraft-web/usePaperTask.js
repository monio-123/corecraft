/**
 * 出题后台任务的「提交 → 等「待作答」→ 消费 → 收档」骨架。
 *
 * 考试页（`AppExamView`）与随堂抽屉（`useQuiz`）共用——两处各写一份时，
 * 同一批坑修了一边漏一边（2026-11-15 实锤：随堂给消费失败补了作废、考试页没有 ⇒
 * 消费失败后任务停在 awaiting，对账 watch 每次轮询刷新都重新消费，永远出不来）。
 *
 * ## 骨架里的不变量（动这里之前先读懂）
 *
 * - **promptKey 必须带**（`CFG_KEYS.paper`）：不带 ⇒ 后端按默认问题「写知识点」问模型 ⇒
 *   它写的是知识梳理，不是题（2026-11-15 修随堂「出不了题」）。
 * - **消费完成就 ack（幂等）**：任务转已完成、任务卡角标灭；**消费失败也作废**——
 *   不作废的话任务停在 awaiting，本 composable 的对账 watch 每次刷新都重新消费。
 * - **人还等在这儿 ⇒ 待作答自动进卷**；人走开了（抽屉/页面重开）由调用方的
 *   「作答」按钮手动消费——两条入口都落 `consume(id)`。
 *
 * ## 回调
 *
 * - `onResult(r)`：消费完成。`r` = `consumePaperTask` 的原始结果——**中性结果
 *   （`r.error` + `r.quiet`，「出不了题」）也走这里**，由调用方决定怎么展示。
 * - `onFail(message)`：任务本身失败（status=failed）。
 * - `onVanish()`：任务被取消 / 在他处消费成 succeeded——调用方收口（回设置态 / 关抽屉），
 *   别让界面停在「正在出题」。
 */
import { computed, ref, watch } from 'vue'
import { CFG_KEYS, consumePaperTask } from './exam'
import { ackGenTask, createGenTask } from '../../api/genTask'
import { genTasks, refreshGenTasksNow } from './useGenTasks'

export function usePaperTask({ onResult, onFail, onVanish } = {}) {
  /** 本方在跟的出题任务 id（null = 没跟）。任务卡「作答」跳回的也落到它 */
  const taskId = ref(null)

  const task = computed(() =>
    taskId.value == null ? null : genTasks.value.find((t) => t.id === taskId.value) || null
  )

  watch(task, (t) => {
    if (!t || taskId.value == null) return
    if (t.status === 'awaiting') {
      consume()
    } else if (t.status === 'failed') {
      taskId.value = null
      onFail?.(t.error || '出题失败')
    } else if (['canceled', 'succeeded'].includes(t.status)) {
      taskId.value = null
      onVanish?.()
    }
  })

  /**
   * 提交一个出题任务。
   *
   * @param {object} opts
   * @param {'exam'|'quiz'} opts.type 任务类型（考试页 exam / 随堂 quiz）
   * @param {'exam'|'quiz'} opts.phaseKey 步骤组 key——必须对上节点链登记的那组（GenTaskNodes）
   * @param {string} opts.title 任务名（任务卡上显示）
   * @param {object} opts.params 持久化参数快照（kind/categoryId/endpointId/topicIds…）
   * @param {string} opts.material 出题材料（题量结构 + 范围 + 模板，`paperTaskPayload` 产的）
   * @returns {Promise<number>} 任务 id
   */
  async function submit({ type, phaseKey, title, params, material }) {
    const id = (
      await createGenTask({
        type,
        title,
        promptKey: CFG_KEYS.paper,
        params,
        steps: [{ seq: 1, phase: phaseKey, phaseSeq: 1, title: '出题', material }]
      })
    )?.data
    if (!Number.isFinite(Number(id))) throw new Error('任务没有创建成功')
    taskId.value = Number(id)
    await refreshGenTasksNow()
    return taskId.value
  }

  /** 在飞的那次消费（同 id 重复触发只跑一次） */
  let inFlight = null

  /**
   * 消费任务：拿原文 → 调用方解析建卷。默认消费在跟的那个；显式传 id 用于
   * 任务卡「作答」跳回（那一刻任务列表可能还没拉到，认不了 computed）。
   */
  function consume(id = taskId.value) {
    if (inFlight) return inFlight
    const tid = Number(id)
    if (!Number.isFinite(tid) || tid <= 0) return Promise.resolve(null)
    taskId.value = tid
    inFlight = (async () => {
      let r
      try {
        r = await consumePaperTask(tid)
      } catch (e) {
        r = { error: e?.message || '生成试卷失败' }
      }
      // 收档（幂等）：成了转已完成、角标灭；**失败也作废**（见文件头第二个不变量）。
      ackGenTask(tid).catch(() => {}).finally(refreshGenTasksNow)
      taskId.value = null
      onResult?.(r)
      return r
    })().finally(() => {
      inFlight = null
    })
    return inFlight
  }

  return { taskId, submit, consume }
}
