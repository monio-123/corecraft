<template>
  <!-- **抽屉，不是居中弹窗**：任务是"在旁边瞥一眼"的东西，
       居中弹窗会挡住正在读的正文，而任务存在期间用户多半在读别的条目。 -->
  <el-drawer
    v-model="visible"
    title="任务"
    size="420px"
    class="taskdrawer"
    @open="onOpen"
    @close="onClose"
  >
    <div ref="twRef" class="tw">
      <!-- 筛选：**一行两个同样的下拉**（类型 + 状态）。 -->
      <div class="tw__filter">
        <el-dropdown
          trigger="click"
          popper-class="task-filter-popper"
          :persistent="ddKeepMounted"
          @visible-change="onDropdownShow"
          @command="onTypeFilter"
        >
          <button class="tw__sel">{{ typeFilterName }} ▾</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="t in typeFilters"
                :key="t.key"
                :command="t.key"
              >
                {{ t.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown
          trigger="click"
          popper-class="task-filter-popper"
          :persistent="ddKeepMounted"
          @visible-change="onDropdownShow"
          @command="onStatusFilter"
        >
          <button class="tw__sel">{{ statusFilterName }} ▾</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="s in STATUS_FILTERS"
                :key="s.key"
                :command="s.key"
              >
                {{ s.name }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-if="!genTasksLoaded" class="tw__skel">
        <div v-for="i in 3" :key="i" class="tw__skel-card">
          <KbSkeleton variant="title" width="58%" height="17px" />
          <KbSkeleton variant="line" width="88%" height="10px" class="tw__skel-line" />
          <KbSkeleton variant="line-short" width="42%" height="10px" class="tw__skel-line" />
        </div>
      </div>

      <p v-else-if="!shownTasks.length" class="tw__empty">没有任务</p>

      <TaskCard
        v-for="t in pageTasks"
        :key="t.id"
        :task="t"
        :steps="stepsOf(t.id)"
        :expanded="expanded === t.id"
        :loading-steps="expanded === t.id && !stepsOf(t.id).length"
        @toggle="toggle"
        @pause="pause"
        @resume="requeue($event, '恢复失败')"
        @retry="requeue($event, '重试失败')"
        @confirm="openPlan"
        @remove="remove"
      />
    </div>

    <template v-if="shownTasks.length > PAGE_SIZE" #footer>
      <div class="tw__pager">
        <el-pagination
          layout="prev, pager, next"
          background
          :total="shownTasks.length"
          :page-size="PAGE_SIZE"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '../../utils/confirm'
import { useDropdownKeepMounted } from '../../utils/dropdown'
import TaskCard from '../../components/corecraft-web/TaskCard.vue'
import KbSkeleton from '../../components/corecraft-web/KbSkeleton.vue'
import {
  getGenTaskDetail, cancelGenTask, resumeGenTask, removeGenTask
} from '../../api/genTask'
import {
  genTasks, genTasksLoaded, refreshGenTasks, startGenTaskPolling, POLL_FAST, POLL_SLOW
} from './useGenTasks'
import {
  matchesStatusFilter, STATUS_FILTERS, taskStatus, taskType
} from '../../utils/taskTypes'

const emit = defineEmits(['openPlan'])
const { keepMounted: ddKeepMounted, onDropdownShow } = useDropdownKeepMounted()
const visible = ref(false)
const expanded = ref(null)
const stepsByTask = ref(new Map())

const filterType = ref('')
const filterStatus = ref('')

const tasks = computed(() => genTasks.value)

function taskPriority(t) {
  const k = taskStatus(t).key
  if (k === 'awaiting') return 0
  if (k === 'failed') return 1
  if (k === 'partial') return 2
  if (k === 'running' || k === 'canceling' || k === 'queued') return 3
  if (k === 'canceled') return 4
  return 5
}

// 「1 个进行中 · 共 18 个」计数行与「清理已完」按钮 2026-11-18 用户拍板删除
// （§8⑥：卡片自己带状态，计数是重复；批量清理需要了再加回来——
//  后端 /clear-finished 端点保留，前端包装函数一并删了，要用时重写一个三行的）
const shownTasks = computed(() => {
  let list = tasks.value
  if (filterType.value) {
    list = list.filter((t) => t.type === filterType.value)
  }
  if (filterStatus.value) {
    list = list.filter((t) => matchesStatusFilter(t, filterStatus.value))
  }
  return [...list].sort((a, b) => taskPriority(a) - taskPriority(b) || (b.id - a.id))
})

function filterName(options, key) {
  return (options.find((o) => o.key === key) || options[0]).name
}

const typeFilterName = computed(() => filterName(typeFilters.value, filterType.value))

const typeFilters = computed(() => {
  const counts = new Map()
  for (const t of tasks.value) {
    const k = t.type || 'other'
    counts.set(k, (counts.get(k) || 0) + 1)
  }
  const opts = [{ key: '', name: '全部类型' }]
  for (const [k, n] of counts) {
    opts.push({ key: k, name: `${taskType(k).name} ${n}` })
  }
  return opts
})
const statusFilterName = computed(() => filterName(STATUS_FILTERS, filterStatus.value))

function onTypeFilter(key) {
  filterType.value = key
}

function onStatusFilter(key) {
  filterStatus.value = key
}

const PAGE_SIZE = 6
const page = ref(1)
const twRef = ref(null)

const pageTasks = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return shownTasks.value.slice(start, start + PAGE_SIZE)
})

const maxPage = computed(() => Math.max(1, Math.ceil(shownTasks.value.length / PAGE_SIZE)))

function onPageChange(p) {
  page.value = p
  expanded.value = null
  const body = twRef.value?.closest('.el-drawer__body')
  if (body) body.scrollTop = 0
}

watch(maxPage, (m) => {
  if (page.value > m) page.value = m
})

watch([filterType, filterStatus], () => {
  page.value = 1
})

const stepsOf = (id) => stepsByTask.value.get(id) || []

let stepsInflight = false
const stepsPending = new Set()

async function refreshSteps(ids) {
  for (const id of ids || []) stepsPending.add(id)
  if (stepsInflight || !stepsPending.size) return
  stepsInflight = true
  try {
    while (stepsPending.size) {
      const batch = [...stepsPending]
      stepsPending.clear()
      for (const id of batch) {
        if (!genTasks.value.some((x) => x.id === id)) continue
        try {
          const res = await getGenTaskDetail(id)
          const fresh = res?.data?.steps || []
          if (!stepsDiffer(stepsByTask.value.get(id), fresh)) continue
          const next = new Map(stepsByTask.value)
          next.set(id, fresh)
          stepsByTask.value = next
        } catch (e) {
          console.warn('[task] 拉步骤失败', id, e?.message)
        }
      }
    }
  } finally {
    stepsInflight = false
  }
}

function stepsDiffer(a, b) {
  if (!a || !b || a.length !== b.length) return true
  for (let i = 0; i < a.length; i++) {
    if (a[i].status !== b[i].status) return true
    if (a[i].title !== b[i].title) return true
    if (a[i].outputSummary !== b[i].outputSummary) return true
  }
  return false
}

/** 仅按需拉当前展开的那张卡的明细步骤，避免折叠卡无意义全量轮询 */
function needStepIds() {
  return expanded.value ? [expanded.value] : []
}

watch(tasks, () => { if (visible.value) refreshSteps(needStepIds()) }, { deep: false })

function onOpen() {
  page.value = 1
  if (tasks.value.some((t) => taskStatus(t).key === 'awaiting')) {
    filterStatus.value = ''
    filterType.value = ''
  }
  startGenTaskPolling(POLL_FAST, { immediate: !genTasksLoaded.value })
  refreshSteps(needStepIds())
}

function onClose() {
  expanded.value = null
  startGenTaskPolling(POLL_SLOW, { immediate: false })
}

function patchTask(id, patch) {
  genTasks.value = genTasks.value.map((x) => (x.id === id ? { ...x, ...patch } : x))
}

function toggle(id) {
  if (expanded.value === id) {
    expanded.value = null
    return
  }
  expanded.value = id
  refreshSteps([id])
}

async function pause(t) {
  if (!(await confirmAction('暂停任务'))) return
  try {
    await cancelGenTask(t.id)
    patchTask(t.id, { status: 'canceling' })
  } catch (e) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

/** 暂停后恢复与失败后重试统一走 resumeGenTask（后端已收口 PH_SKELETON result 清理与未成步骤重置） */
async function requeue(t, fallbackMsg) {
  try {
    await resumeGenTask(t.id)
    patchTask(t.id, { status: 'queued', error: '' })
  } catch (e) {
    ElMessage.error(e?.message || fallbackMsg)
  }
}

async function remove(t) {
  if (!(await confirmAction('删除任务'))) return
  try {
    await removeGenTask(t.id)
    if (expanded.value === t.id) expanded.value = null
    genTasks.value = genTasks.value.filter((x) => x.id !== t.id)
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function openPlan(task) {
  try {
    const res = await getGenTaskDetail(task.id)
    let outline = ''
    try {
      outline = JSON.parse(res?.data?.result || '{}')?.outline || ''
    } catch {
      outline = ''
    }
    if (!String(outline).trim()) {
      ElMessage.error('这个任务没有可用的目录，请重试')
      return
    }
    emit('openPlan', { task, outline })
  } catch (e) {
    ElMessage.error(e?.message || '打开失败')
  }
}

defineExpose({
  open() {
    visible.value = true
  }
})
</script>

<style scoped>
.tw__filter {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-10);
}
.tw__sel {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px var(--sp-8);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-sm);
  background: var(--cc-surface);
  font-size: 12px;
  color: var(--cc-text-3);
  cursor: pointer;
  white-space: nowrap;
}
.tw__sel:hover {
  color: var(--cc-text-1);
}
/* （.tw__sum / .tw__clear 已删——计数行与批量清理 2026-11-18 用户拍板下线，
   需要了从流水 #1028 找回） */
.tw__skel-card {
  height: 96px;
  margin-bottom: var(--sp-9);
  border-radius: var(--radius-md);
  background: var(--cc-surface-2);
  padding: var(--sp-12);
}
.tw__skel-line {
  margin-top: var(--sp-9);
}
.tw__pager {
  display: flex;
  justify-content: center;
}
.tw__pager :deep(.el-pagination) {
  --el-pagination-button-width: 24px;
  --el-pagination-button-height: 24px;
  --el-pagination-font-size: 12px;
  gap: 2px;
}
.tw__pager :deep(.el-pagination button),
.tw__pager :deep(.el-pager li) {
  font-size: 12px;
}
.tw__empty {
  margin: var(--sp-16) 0;
  text-align: center;
  font-size: 12px;
  color: var(--cc-text-4);
}
</style>

<style>
.el-overlay:has(> .taskdrawer),
.taskdrawer {
  --el-transition-duration: 0.18s;
}

.taskdrawer .el-drawer__header {
  margin-bottom: 0;
  padding: var(--sp-14) var(--sp-16) var(--sp-8);
}
.taskdrawer .el-drawer__body {
  padding: var(--sp-8) var(--sp-16) var(--sp-10);
  overflow-y: auto;
  scrollbar-gutter: stable;
}
.taskdrawer .el-drawer__footer {
  padding: var(--sp-8) var(--sp-16);
  border-top: 1px solid var(--cc-border-card);
}

.task-filter-popper .el-dropdown-menu {
  padding: 4px 0;
}
.task-filter-popper .el-dropdown-menu__item {
  font-size: 12px;
  line-height: 1.4;
  padding: 5px 14px;
}
</style>
