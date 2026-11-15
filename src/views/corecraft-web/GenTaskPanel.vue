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
      <!-- 筛选：**一行两个同样的下拉**（类型 + 状态）。
           类型原来是一排分段按钮 —— 类型一多就把抽屉顶满、「生成考试」被截成「生」，
           所以和状态统一成同一种 chip：两个并排、同档字号、同档内距。 -->
      <div class="tw__filter">
        <el-dropdown trigger="click" popper-class="task-filter-popper" @command="onTypeFilter">
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
        <el-dropdown trigger="click" popper-class="task-filter-popper" @command="onStatusFilter">
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
        <span class="tw__sum">{{ summary }}</span>
      </div>

      <!-- 首次加载：骨架（不要"还没有任务"闪一下再变列表）。
           ⚠️ 走**统一加载层**（2026-11-13）：原来是三块纯色灰矩形（.tw__skel-card），
           材质跟主题无关（换主题它还是那个灰），骨架统一读 --kb-skel-* ⇒ 跟别处一致。 -->
      <div v-if="!genTasksLoaded" class="tw__skel">
        <div v-for="i in 3" :key="i" class="tw__skel-card">
          <KbSkeleton variant="title" width="58%" height="17px" />
          <KbSkeleton variant="line" width="88%" height="10px" class="tw__skel-line" />
          <KbSkeleton variant="line-short" width="42%" height="10px" class="tw__skel-line" />
        </div>
      </div>

      <!-- 空态：**一句话**。原来"筛出 0 个 · 共 23 个"和这个框各说一遍，是同一件事。 -->
      <p v-else-if="!shownTasks.length" class="tw__empty">没有任务</p>

      <!-- 列表：**一页只渲染 PAGE_SIZE 张**。
           原来是把拉回来的 50 条一次铺完 —— 每张卡里还有节点链、下拉、进度条，
           任务一多就是"翻好久 + 点起来卡"（用户原话「要无限的显示任务么，翻个页好一点吧」）。 -->
      <TaskCard
        v-for="t in pageTasks"
        :key="t.id"
        :task="t"
        :steps="stepsOf(t.id)"
        :expanded="expanded === t.id"
        :loading-steps="expanded === t.id && !stepsOf(t.id).length"
        @toggle="toggle"
        @pause="pause"
        @resume="resume"
        @retry="retry"
        @confirm="onConfirm"
        @remove="remove"
      />

    </div>

    <!-- 分页放在 **drawer 的 footer**：固定在下沿，翻页不用先滚到底。
         一页装得下时整块不渲染（画个孤零零的"1"是噪音，与考试记录页同一口径）。 -->
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

  <!-- AI 归类的**采纳对话框**（全站一个实例）。
       ⚠️ 它**叠在抽屉上面**，抽屉不关 —— 用户从卡片点「确认」看完产物、
       取消之后要回到刚才那张卡（滚动位置、展开状态都在）。 -->
  <ClassifyAdoptDialog v-model="adoptOpen" :task="adoptTask" @done="onAdopted" />
</template>

<script setup>
/**
 * 任务中心：**看进度 / 停 / 继续 / 重跑节点 / 删**。
 *
 * ## 数据从 `useGenTasks` 来，不在这里自己拉列表
 *
 * 侧边栏「任务」的角标和这个抽屉用的是同一份数据（那个模块里一份 ref + 一个轮询）。
 * 这里只负责**切频率**：打开 3 秒、关闭 30 秒（角标用）。
 *
 * ## 筛选（v2）
 *
 * **类别 + 状态两个维度在客户端筛**（列表拉的是全量 50 条）——
 * 侧边栏角标和抽屉共用同一份列表，在后端筛会让角标跟着筛出来的那批跳变。
 *
 * ## 只为"进行中"的任务拉步骤明细
 *
 * 卡片上那句「正在写：G1」要的是 steps 里 `status=running` 的那一条。
 * 20 个任务全拉详情太重（每个几十条步骤），所以只拉进行中的那几个
 * （线程池 2 个 worker，实际同时最多 2 个）。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '../../utils/confirm'
import TaskCard from '../../components/corecraft-web/TaskCard.vue'
import KbSkeleton from '../../components/corecraft-web/KbSkeleton.vue'
import ClassifyAdoptDialog from './ClassifyAdoptDialog.vue'
import {
  getGenTaskDetail, cancelGenTask, resumeGenTask, retryGenTaskNode, removeGenTask
} from '../../api/genTask'
import {
  genTasks, genTasksLoaded, refreshGenTasks, startGenTaskPolling, POLL_FAST, POLL_SLOW
} from './useGenTasks'
import { bumpDataVersion, refreshTopicIndexes } from './useCorecraftWebStore'
import { isActive, STATUS_FILTERS, taskType } from '../../utils/taskTypes'

const emit = defineEmits(['openPlan', 'openAnswer'])
const visible = ref(false)
const expanded = ref(null)
/** taskId → steps（只为进行中 / 展开的任务填） */
const stepsByTask = ref(new Map())

/** 筛选（**类型 + 状态**，两个同形态的下拉） */
const filterType = ref('')
const filterStatus = ref('')

/** AI 归类的**采纳对话框**：开着没有 + 看的是哪个任务 */
const adoptOpen = ref(false)
const adoptTask = ref(null)

const tasks = computed(() => genTasks.value)

/**
 * 列表里显示的任务（**客户端筛选**）。
 *
 * <p>⚠️ **为什么不在后端筛**：侧边栏角标和这个抽屉**共用同一份列表**
 * （`useGenTasks` 里一份 ref）。后端筛了 ⇒ 角标就只数"筛出来的那批"，
 * 用户在抽屉里一筛，角标就跳变 ⇒ 角标变得没有意义。
 * ⇒ 列表**拉全量**（50 条），筛选只影响**显示**，不影响角标。
 */
const shownTasks = computed(() => {
  let list = tasks.value
  if (filterType.value) {
    list = list.filter((t) => t.type === filterType.value)
  }
  if (filterStatus.value) {
    list = list.filter((t) => t.status === filterStatus.value)
  }
  return list
})

/**
 * 下拉当前的显示名。
 *
 * <p>类型和状态两个下拉同形 ⇒ **只写这一份**：选项表的第一项就是"全部"，
 * 取不到就回落到它（`:key` 只会是表里的值，回落只是兜底）。
 */
function filterName(options, key) {
  return (options.find((o) => o.key === key) || options[0]).name
}

const typeFilterName = computed(() => filterName(typeFilters.value, filterType.value))

/**
 * 类型筛选项 = **这批任务里实际有的类型**（带条数），不是注册表里全部 7 个 ——
 * 用户只有「生成主题」时，下拉里列 7 个选项（判分/出题/每日测验…全是没影的）
 * 看着像"系统有好多东西"（2026-11-13 用户原话「怎么有这么多类型？」）。
 * 没有的类型筛了也是空的；真长出新类型的那天它自然会出现在这。
 */
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

/** 类型筛选（下拉选一项 ⇒ 只影响显示，不影响侧边栏角标） */
function onTypeFilter(key) {
  filterType.value = key
}

/** 状态筛选（同上） */
function onStatusFilter(key) {
  filterStatus.value = key
}

/**
 * 顶部计数（**和筛选同一行**）。
 *
 * <p>⚠️ 有筛选时先说"筛出多少"：只数全量的话，屏幕上只剩 11 张卡、顶上却写「共 23 个」，
 * 看着像筛选没生效。
 *
 * <p>⚠️ **一条都没有时不报数**：那时列表里已经有一句「没有任务」，
 * 顶上再写「筛出 0 个 · 共 23 个」是同一件事说两遍（用户原话：两处合并为一处）。
 */
const summary = computed(() => {
  if (!shownTasks.value.length) return ''
  const parts = []
  const active = tasks.value.filter(isActive).length
  if (active) parts.push(`${active} 个进行中`)
  if (filterType.value || filterStatus.value) parts.push(`筛出 ${shownTasks.value.length} 个`)
  parts.push(`共 ${tasks.value.length} 个`)
  return parts.join(' · ')
})

// ---------- 分页 ----------

/**
 * 一页多少张。
 *
 * <p>⚠️ **只影响渲染，不影响拉取**：列表仍然一次拉 50 条（`useGenTasks#LIST_SIZE`）——
 * 侧边栏角标和筛选都用那一份全量（见上面「为什么不在后端筛」）。
 * 分页解决的是"一次铺 25 张卡、每张都有节点链和下拉"带来的渲染负担。
 */
const PAGE_SIZE = 6

const page = ref(1)
const twRef = ref(null)

/** 当前页的任务（**先筛后分页**：筛出来的结果自己分页，不是"分页后再筛"） */
const pageTasks = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return shownTasks.value.slice(start, start + PAGE_SIZE)
})

/** 最大页码（至少 1 页） */
const maxPage = computed(() => Math.max(1, Math.ceil(shownTasks.value.length / PAGE_SIZE)))

/**
 * 翻页：**收起展开的卡 + 回到顶部**。
 *
 * <p>不收起的话，翻到下一页时 `expanded` 还记着上一页那张卡的 id，
 * 一旦它恰好也在本页会莫名其妙"自己展开"；滚动位置留在原处则会落在列表中间。
 */
function onPageChange(p) {
  page.value = p
  expanded.value = null
  const body = twRef.value?.closest('.el-drawer__body')
  if (body) body.scrollTop = 0
}

/**
 * 页码越界时自动回退。
 *
 * <p>会发生：在第 3 页把仅剩的几张删完 / 筛出更少的结果 / 任务列表被轮询刷新。
 * 不回退的话是一屏空白，用户以为"任务全没了"。
 */
watch(maxPage, (m) => {
  if (page.value > m) page.value = m
})

/** 换筛选条件 ⇒ 回到第 1 页（停在第 3 页看新条件的结果是空的） */
watch([filterType, filterStatus], () => {
  page.value = 1
})

const stepsOf = (id) => stepsByTask.value.get(id) || []

/**
 * 拉某几个任务的步骤。
 *
 * <p>**串行**：一次网络往返比并行少几个连接，而这里最多 2~3 个 id。
 *
 * <p>⚠️ **内容没变就不换 Map**：换 Map 会让所有卡片的 `steps` 引用变化 ⇒ 全部重画；
 * 而这张卡上的步骤大部分时候是不动的（只有正在写的那一条在变）。
 */
let stepsInflight = false
/** 在途期间又要求拉的 id（拉完这一批再补，避免"刚好展开的那张没拉到"） */
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
        // **列表里已经没有它就别再问**：串行一批可能横跨一次删除（批次是换数组前收的），
        // 问一个已删除的任务只会得到「任务不存在或已删除」，request.js 会把它弹给用户。
        if (!genTasks.value.some((x) => x.id === id)) continue
        try {
          const res = await getGenTaskDetail(id)
          const fresh = res?.data?.steps || []
          if (!stepsDiffer(stepsByTask.value.get(id), fresh)) continue
          // 换一个新 Map 才会触发刷新（ref 里放 Map，直接 set 不会引起依赖更新）
          const next = new Map(stepsByTask.value)
          next.set(id, fresh)
          stepsByTask.value = next
        } catch (e) {
          // 单条拉不到就保持上一次的（不把"正在写：X"抹掉）；留一条 debug 便于查
          console.warn('[task] 拉步骤失败', id, e?.message)
        }
      }
    }
  } finally {
    stepsInflight = false
  }
}

/** 两组步骤"看起来一样"吗（只比卡片会画的字段：状态 + 标题 + 摘要） */
function stepsDiffer(a, b) {
  if (!a || !b || a.length !== b.length) return true
  for (let i = 0; i < a.length; i++) {
    if (a[i].status !== b[i].status) return true
    if (a[i].title !== b[i].title) return true
    if (a[i].outputSummary !== b[i].outputSummary) return true
  }
  return false
}

/**
 * 需要明细的：**正在跑的**（卡片上要写"正在处理哪一条"）+ **当前展开的**。
 *
 * <p>⚠️ 等确认的任务**不再批量拉**：它的产物要靠人点开，列表里展示的只是
 * 节点摘要（列表接口已经带回来了）⇒ 23 个任务里那 6 个 awaiting 全拉一遍，
 * 每 3 秒 6 次串行请求，正是"点菜单卡一下"的来源。
 */
function needStepIds() {
  const ids = tasks.value.filter(isActive).map((t) => t.id)
  if (expanded.value && !ids.includes(expanded.value)) ids.push(expanded.value)
  return ids
}

// 列表**真的变了**才顺手刷步骤（`refreshGenTasks` 会复用没变的对象，签名一样就不触发）。
// ⚠️ **抽屉关着就不拉**：详情接口带几百条步骤，关着时拉它只是白占连接、白解析大响应；
// 打开那一刻由 `onOpen` 补一次，之后跟着 3 秒轮询走。
watch(tasks, () => { if (visible.value) refreshSteps(needStepIds()) }, { deep: false })

function onOpen() {
  // 打开：3 秒一跳，能看到进度在动。
  // **已有缓存就不在这一刻拉**：抽屉进场动画 + 6 张卡首帧已经要做，再叠一个
  // 50 条请求与解析会掉帧；缓存先渲染，下一拍（3 秒）再对账。第一次打开才立刻拉。
  startGenTaskPolling(POLL_FAST, { immediate: !genTasksLoaded.value })
  // 关着的时候不拉步骤（见下面那条 watch）⇒ 打开时补一次
  refreshSteps(needStepIds())
}

function onClose() {
  expanded.value = null
  // 关掉后降到 30 秒：只为侧边栏角标（页面隐藏时那个轮询会自己跳过）。
  // **关闭这一刻也不拉** —— 退场动画期间再发一次请求没有意义。
  startGenTaskPolling(POLL_SLOW, { immediate: false })
}

/**
 * 本地改一条任务（**乐观更新**）。
 *
 * <p>⚠️ **换数组而不是改对象属性**：`tasks` 那个 watch 是 `deep: false`，
 * 只有数组引用变了才触发（要让卡片和角标都跟着动）。
 *
 * <p>⚠️ **不拿它替代后端**：停止/重试是异步的，立刻拉一次拿到的还是旧状态，
 * 反而会把刚变好的界面打回去 ⇒ 界面先动，下一次轮询（3 秒）对账。
 */
function patchTask(id, patch) {
  genTasks.value = genTasks.value.map((x) => (x.id === id ? { ...x, ...patch } : x))
}

function toggle(id) {
  if (expanded.value === id) {
    expanded.value = null
    return
  }
  expanded.value = id
  // **展开就拉一次**（原来只在没缓存时拉）：缓存可能是几小时前的中途快照——
  // 任务完成后列表签名不再变化 ⇒ watch 不触发 ⇒ 步骤永远停在最后一次轮询
  // （2026-11-13 用户实测：头部「已完成 291/291」，下面 154 还「进行中」、一墙「待跑」）。
  // 一次 detail 请求换确定性。
  refreshSteps([id])
}

/**
 * **暂停任务**（2026-11-12 用户口径「暂停」，不是「停止」）。
 *
 * <p>⚠️ **它不是终止**：暂停之后能恢复、也能删除。
 * 而"停止"这个词会让用户以为"彻底不跑了、不能恢复" ⇒ 文案统一改「暂停」。
 *
 * <p>⚠️ **乐观更新**：立刻显示「正在暂停」，点击马上有反馈。
 * 不去立刻拉列表：暂停是异步的，拉回来还是 running，会把刚变的界面打回去
 * （真实状态由下一次 3 秒轮询对账）。
 */
async function pause(t) {
  if (!(await confirmAction('暂停任务'))) return
  try {
    await cancelGenTask(t.id)
    patchTask(t.id, { status: 'canceling' })
  } catch (e) {
    ElMessage.error(e?.message || '暂停失败')
  }
}

/**
 * **恢复任务**（把暂停的接着跑）。
 *
 * <p>⚠️ **不弹确认框**：用户点的就是「恢复」这个明确的动作按钮，
 * 再问一句"要恢复吗"是把同一个决定问两遍（只有"删除"这种不可逆的才确认）。
 */
async function resume(t) {
  try {
    await resumeGenTask(t.id)
    // **乐观更新**：立刻回到「排队中」，按钮马上切回主操作；进度等下一拍再对。
    patchTask(t.id, { status: 'queued', error: '' })
  } catch (e) {
    ElMessage.error(e?.message || '恢复失败')
  }
}

/**
 * **删除任务**（软删）。
 *
 * <p>⚠️ **弹窗只问一句，不解释后果**（2026-11-13 用户定的口径，见 `utils/confirm.js`）：
 * 「删任务只删记录、不动知识库」是接口语义，不是该塞进确认框的一段说明。
 *
 * <p>⚠️ **进行中的任务没有这个按钮**（先暂停再删）——
 * 执行线程还在写步骤，删了它下一步会把步骤写回来。
 */
async function remove(t) {
  if (!(await confirmAction('删除任务'))) return
  try {
    await removeGenTask(t.id)
    // **乐观更新**：先从列表摘掉，卡片立刻消失；下次轮询再对账（真没删成功它会回来）。
    // ⚠️ **删的是展开的那张 ⇒ 先清 expanded、再换数组**（2026-11-13 用户报：
    // 确认删除后弹的不是结果而是「任务不存在或已删除」）：下面那条 watch 在换数组
    // 那一拍调 needStepIds()，它会**无条件**把 expanded 补进拉取清单 —— 不先清掉，
    // 等于删完立刻去拉一个已删除任务的详情，后端必然报这条错、request.js 弹给用户。
    if (expanded.value === t.id) expanded.value = null
    genTasks.value = genTasks.value.filter((x) => x.id !== t.id)
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

/**
 * **重试**（统一入口，不分任务类型）。
 *
 * <p>⚠️ **先看节点、再看任务**：
 * <ol>
 *   <li>有失败 / 没跑完的**模型节点** ⇒ 走节点级重跑（目录任务走 resume 会被后端拒绝，
 *       因为它会把骨架再生成一遍 ⇒ 任务里冒出两套章节）。</li>
 *   <li>没有整节点可重跑（零散失败步骤）⇒ 走任务级 resume，只补没成的那些。</li>
 * </ol>
 *
 * <p>⚠️ **不弹确认框**：用户点的就是「重试」这个明确的动作按钮，
 * 再问一句"要重试吗"是把同一个决定问两遍（只有"删除"这种不可逆的才确认）。
 */
async function retry(t) {
  const badNode = (t.nodes || []).find(
    (n) => n.status === 'failed' || (n.status === 'pending' && n.total > 0)
  )
  try {
    if (badNode) {
      await retryGenTaskNode(t.id, badNode.key)
    } else {
      await resumeGenTask(t.id)
    }
    // **乐观更新**：立刻回到「排队中」，按钮马上切回主操作；进度等下一拍再对。
    patchTask(t.id, { status: 'queued', error: '' })
  } catch (e) {
    ElMessage.error(e?.message || '重试失败')
  }
}

/**
 * **「确认」= 打开那个人工节点的产物**（不分任务类型，只看**节点 key**）。
 *
 * <p>节点是后端给的语义，不是前端按类型猜的：
 * <ul>
 *   <li>{@code adopt}（采纳归类建议）⇒ 采纳对话框</li>
 *   <li>其余人工节点（{@code confirm}：确认目录）⇒ 目录弹窗第 2 步</li>
 * </ul>
 *
 * <p>⚠️ **抽屉不关**：用户看完产物、取消之后要回到刚才那张卡
 * （滚动位置、展开状态都得在）。弹窗 z-index 天然高于抽屉，叠着没问题。
 */
async function onConfirm(task, nodeKey) {
  if (nodeKey === 'adopt') {
    adoptTask.value = task
    adoptOpen.value = true
    return
  }
  // 出题任务的人工节点：跳去对应页面「作答」（去哪由类型注册表说，见 taskTypes）
  if (nodeKey === 'answer') {
    emit('openAnswer', task)
    return
  }
  await openPlan(task)
}

/**
 * **确认目录**：把树交给「生成主题」弹窗的第 2 步。
 *
 * <p>树在 `task.result` 里，只有**详情**接口带它 ⇒ 这里先拉一次。
 * 弹窗只认识"一段大纲文本"，不该自己去请求任务（那是任务中心的职责）。
 */
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

/**
 * 采纳完了：**刷新任务列表 + 刷索引 + 让列表页重拉那一页**。
 *
 * <p>采纳改的是知识点（目录 / 标题 / 标签）⇒ 列表页那一页得跟着变。
 * 而任务抽屉在壳（AppLayout）里，列表页在另一个视图里 ⇒ 两边只能通过
 * store 的**数据版本号**说话（视图 watch `getDataVersion` 重拉）。
 *
 * <p>⚠️ **索引缓存也得刷**：结构索引（目录归属 / 计数）是**一份缓存**，
 * 不刷的话侧边栏的目录计数还是旧的（"这个目录下有 12 条"实际已经变了）。
 */
async function onAdopted() {
  adoptTask.value = null
  refreshGenTasks()
  await refreshTopicIndexes()
  bumpDataVersion()
}

defineExpose({
  open() {
    visible.value = true
  }
})
</script>

<style scoped>
/* 筛选：一行两个同形的下拉（类型 + 状态） */
.tw__filter {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-10);
}
/* 两个下拉共用这一套：同档字号、同档内距、同档描边 ——
   **不要给某一个加 margin-left:auto**（那是把两个控件拉到两端，
   同一行两种间距，看着像两组不相干的东西） */
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
.tw__sum {
  font-size: 12px;
  color: var(--cc-text-3);
  /* 计数跟在下拉后面（同一行），**不占一端**：它不是第二个筛选器 */
  margin-left: auto;
}
/* 首次加载的骨架卡：**内容结构对着真实任务卡**（标题 + 两行），
   卡片外框仍用 surface-2（真实卡是 surface + 描边，抽屉里 3 张并排太重）。
   ⚠️ 材质（骨架块本身）统一走 --kb-skel-*，见 KbSkeleton。 */
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
/* 分页：在 drawer 的 footer 里（固定下沿，翻页不用滚到底） */
.tw__pager {
  display: flex;
  justify-content: center;
}
/* 抽屉窄（420px），EP 默认的分页按钮偏大 ⇒ 收到跟卡片同档的密度 */
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
/* 空态：**一行**，不要"标题 + 引导语"两级（用户原话：合并为一处、简洁地说没有任务） */
.tw__empty {
  margin: var(--sp-16) 0;
  text-align: center;
  font-size: 12px;
  color: var(--cc-text-4);
}
</style>

<style>
/* 开关更跟手（2026-11-12）：EP 默认过渡 300ms，用户报「打开再关闭再打开」发滞。
   ⇒ 收到 180ms（**只改过渡时长，交互逻辑不变**）。遮罩同步收，否则
   「抽屉已经到位、背景还在慢慢变暗」看着更拖。 */
.el-overlay:has(> .taskdrawer),
.taskdrawer {
  --el-transition-duration: 0.18s;
}

/* 抽屉内部留白：el-drawer 的 body 默认 padding 偏大，这里收紧一点 */
.taskdrawer .el-drawer__header {
  margin-bottom: 0;
  padding: var(--sp-14) var(--sp-16) var(--sp-8);
}
.taskdrawer .el-drawer__body {
  /* 底部留白交给 footer（它有自己的 padding），否则分页器上下留白叠两遍 */
  padding: var(--sp-8) var(--sp-16) var(--sp-10);
  overflow-y: auto;
}
.taskdrawer .el-drawer__footer {
  padding: var(--sp-8) var(--sp-16);
  border-top: 1px solid var(--cc-border-card);
}

/* 筛选下拉的弹层：**字号必须跟触发器一致（12px）**。
   Element Plus 的默认菜单项是 14px —— 12px 的小按钮点开 14px 的菜单，
   视觉上像"点小字弹出大字"（用户原话）。同一屏里字号的档位要有数。 */
.task-filter-popper .el-dropdown-menu {
  padding: 4px 0;
}
.task-filter-popper .el-dropdown-menu__item {
  font-size: 12px;
  line-height: 1.4;
  padding: 5px 14px;
}
</style>
