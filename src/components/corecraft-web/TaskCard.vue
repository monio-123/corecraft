<template>
  <article class="card" :class="[`is-${state.key}`, { 'is-open': expanded }]">
    <!-- 第 1 行：类型 + 标题 + **状态（全卡唯一一处）** + 轻量生命周期图标 + 开合箭头 -->
    <header class="card__head" @click="toggleOpen">
      <span class="card__type">{{ type.name }}</span>
      <span class="card__title">{{ titleText || '未命名任务' }}</span>
      <span class="card__st" :class="`is-${state.key}`">
        <i v-if="state.key === 'running'" class="card__dot" />
        {{ statusText }}
      </span>
      <span class="card__quick" @click.stop>
        <KbTooltip v-if="task.status === 'canceling'" content="正在暂停…">
          <span class="card__qbtn is-busy"><KbLoader size="sm" /></span>
        </KbTooltip>
        <KbTooltip v-else-if="isActive(task)" content="暂停">
          <button type="button" class="card__qbtn is-warn" @click="emit('pause', task)">
            <KbSvgIcon name="pause" />
          </button>
        </KbTooltip>
        <KbTooltip v-else-if="task.status === 'canceled'" content="恢复">
          <button type="button" class="card__qbtn" @click="emit('resume', task)">
            <KbSvgIcon name="play" />
          </button>
        </KbTooltip>
        <KbTooltip v-else-if="state.key === 'failed' || state.key === 'partial'" content="重试">
          <button type="button" class="card__qbtn" @click="emit('retry', task)">
            <KbSvgIcon name="refresh" />
          </button>
        </KbTooltip>
        <KbTooltip v-if="!isActive(task)" content="删除">
          <button type="button" class="card__qbtn is-danger" @click="emit('remove', task)">
            <KbSvgIcon name="trash" />
          </button>
        </KbTooltip>
      </span>
      <i class="card__head-chev" :class="{ 'is-open': expanded }" />
    </header>

    <!-- 第 2 行：**总进度**（计数 + 进度条一行）。条本身就是百分比，不再印第二遍数字。 -->
    <div v-if="progressLabel" class="card__prog">
      <span class="card__prog-n">{{ progressLabel }}</span>
      <div class="card__bar">
        <i :style="{ width: percent }" :class="`is-${barState}`" />
      </div>
    </div>

    <!-- 第 3 行（收起态）：当前阶段一条 + 右侧总已用时。**收起态到此为止** ——
         任务描述、正在处理哪一条、预计还要，都不在收起态展示（2026-11-13 用户定，
         原「左下角只留已用时」）。节点定义仍由后端下发（task.nodes），前端只换展示。 -->
    <button
      v-if="!expanded && summaryNode"
      class="card__current"
      :class="`is-${summaryNode.status}`"
      :title="nodeTitle(summaryNode)"
      @click.stop="onNodeClick(summaryNode, false)"
    >
      <i class="card__current-ico" />
      <span class="card__current-main">
        <b>{{ summaryNode.name }}</b>
        <small>{{ summarySubText }}</small>
        <span v-if="summaryNode.status === 'awaiting'" class="node-hint node-hint--confirm">去确认 ›</span>
        <span v-else-if="summaryNode.status === 'failed'" class="node-hint node-hint--retry">重试 ↻</span>
      </span>
      <span class="card__current-side">
        <small v-if="totalElapsedText">已用时 {{ totalElapsedText }}</small>
        <i class="card__current-go" />
      </span>
    </button>
    <!-- 老任务没有节点链（nodes 为空）时时间也不能没地方放 -->
    <div v-else-if="!expanded && totalElapsedText" class="card__time-alone">
      已用时 {{ totalElapsedText }}
    </div>

    <!-- 失败原因：**这一行就是"点重试之前该知道的事"**（只在收起态汇总；
         展开的细节区里失败步骤下面逐条写着同一个原因，不重复）。 -->
    <p v-if="errorText && !expanded" class="card__err" :title="errorText">{{ errorText }}</p>

    <!-- 展开：**一次只显示一个节点**的细节区。
         点击别的节点即切换 —— 不是把全部堆在一起（正文几百条会把目录节点挤没）。
         ⚠️ **所有节点走同一个模板**（说明 → 这段的量 → 摘要 → 步骤平铺）：
         节点之间允许不同的只有内容（有没有 desc / 摘要 / 步骤），
         不允许因为「节点是谁」「有几个组」换布局（2026-11-13 用户定，见 task-protocol skill）。 -->
    <Transition name="cardx">
      <div v-if="expanded" class="card__body">
        <!-- 第 1 层：**纵向阶段轨**。节点独占一行，只回答：到哪一步 / 状态如何 / 这段用了多久。
             标签行右侧放总已用时（收起态它在当前阶段条上，展开态在这里）。 -->
        <section v-if="nodes.length" class="rail" aria-label="执行阶段">
          <div class="rail__label">
            <span>执行阶段</span>
            <span v-if="totalElapsedText">已用时 {{ totalElapsedText }}</span>
          </div>
          <button
            v-for="n in nodes"
            :key="n.key"
            class="stage"
            :class="[`is-${n.status}`, { 'is-sel': selectedNode === n.key }]"
            :title="nodeTitle(n)"
            @click="onNodeClick(n, true)"
          >
            <span class="stage__rail">
              <i class="stage__ico" />
            </span>
            <span class="stage__main">
              <span class="stage__line">
                <b class="stage__name">{{ n.name }}</b>
                <span v-if="n.status !== 'ok' && n.status !== 'awaiting' && n.status !== 'failed'" class="stage__state">{{ nodeStatus(n.status).text }}</span>
                <span v-if="n.status === 'awaiting'" class="node-hint node-hint--confirm">去确认 ›</span>
                <span v-else-if="n.status === 'failed'" class="node-hint node-hint--retry">重试 ↻</span>
              </span>
              <small v-if="nodeCount(n)" class="stage__sub">{{ nodeCount(n) }}</small>
            </span>
            <span class="stage__time">{{ nodeTime(n) || '—' }}</span>
          </button>
        </section>

        <!-- ⚠️ **紧凑局部指示**（统一加载层）：步骤明细是小区域取数，一行小图形，不弹遮罩。 -->
        <div v-if="loadingSteps" class="card__loading kb-state-line kb-state-line--left">
          <KbLoader size="sm" />
          <span>加载中…</span>
        </div>
        <template v-else-if="currentNode">
          <!-- 第 2 层：**单阶段详情（所有节点同一个模板）**。
               不写节点名（轨上选中的那个就是）；说明有才画。头部的「已用时 · N 步」
               2026-11-15 删（用户：「节点右侧已经展示过了，重复」）——用时看轨行、步数看列表。 -->
          <p v-if="currentNode.desc" class="detail__desc" :title="currentNode.desc">
            {{ currentNode.desc }}
          </p>

          <!-- 产物摘要（确认节点等）：数据有就画一行，同一个位置同一个样式 -->
          <p v-if="currentNode.summary" class="detail__sum">{{ currentNode.summary }}</p>

          <!-- 步骤：**所有步骤组合成一张平铺列表**（组只是排序依据，不画组头、不按组折叠）。
               大节点只铺 失败 + 进行中 + 最近 5 条完成；没跑的后续步骤不铺（进度条已经
               回答了"还有多少"，一墙「待跑」就是噪音），展示不完用一行省略号。
               失败原因逐条跟在那条步骤下面。 -->
          <template v-for="s in shownSteps" :key="s.id">
            <div class="st" :class="`is-${s.status}`">
              <span class="st__dot" />
              <span class="st__seq">{{ s.phaseSeq || s.seq }}</span>
              <span class="st__t" :title="s.title">{{ s.title || '(未命名)' }}</span>
              <span class="st__m">
                <template v-if="s.durationMs">{{ Math.round(s.durationMs / 1000) }}s</template>
                <template v-if="s.needConfirm"><em> · 需复核</em></template>
                <template v-if="s.finishReason === 'length'"><em> · 截断</em></template>
              </span>
              <span v-if="s.status !== 'ok'" class="st__s">{{ stepText(s.status) }}</span>
            </div>
            <p v-if="s.status === 'failed' && s.error" class="st__err" :title="s.error">
              {{ s.error }}
            </p>
          </template>
          <!-- 展示不完就用一行省略号（2026-11-13 用户定：不解释、不计数、不铺待跑清单） -->
          <div v-if="moreCount > 0" class="st__more">…</div>
        </template>
      </div>
    </Transition>
  </article>
</template>

<script setup>
/**
 * 任务卡片（**纯展示**：数据从外面传，动作往上派）。
 *
 * ## 形状（收起四行 + 展开区，#1047 clean 版）
 *
 * 1. 类型 + 标题 + **状态**（全卡唯一一处状态）+ 轻量生命周期图标 + 开合箭头
 * 2. 总进度（计数 + 进度条同行；条就是百分比，数字不印第二遍）
 * 3. 收起态：当前阶段一条（右侧总已用时，**收起态唯一的信息行**）；失败原因一行（有才画）
 * 4. 展开区：阶段轨 + **单阶段详情 —— 所有节点走同一个模板**（说明 → 摘要 → 步骤平铺）
 *
 * ## ⚠️ 判据只看「状态 + 节点」，**不看任务类型**
 */
import { computed, ref, watch } from 'vue'
import { taskType, taskStatus, isActive, nodeStatus, elapsedText, formatSeconds } from '../../utils/taskTypes'
import { percentText } from '../../utils/progress'
import KbLoader from './KbLoader.vue'
import KbSvgIcon from './KbSvgIcon.vue'
import KbTooltip from './KbTooltip.vue'

const props = defineProps({
  task: { type: Object, required: true },
  /** 这个任务的步骤 */
  steps: { type: Array, default: () => [] },
  expanded: { type: Boolean, default: false },
  loadingSteps: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle', 'pause', 'resume', 'retry', 'confirm', 'remove'])

const type = computed(() => taskType(props.task?.type))
const state = computed(() => taskStatus(props.task))
const done = computed(() => props.task?.doneCount || 0)
const total = computed(() => props.task?.total || 0)

/**
 * 标题**不复述类型**（2026-11-15 用户：「随堂测验·JAVA程序结构总览 名字显示重了」）：
 * 类型 chip 就在标题左边，标题里的「随堂测验·」「生成主题：」这类前缀剥掉再显示。
 */
const escRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const titleText = computed(() => {
  const raw = (props.task?.title || '').trim()
  const name = type.value?.name
  if (!name) return raw
  const m = raw.match(new RegExp(`^${escRe(name)}\\s*[·：:]\\s*(.+)$`))
  return m ? m[1].trim() : raw
})

const statusText = computed(() => {
  const st = state.value
  if (st.key === 'partial') return `部分完成 · ${st.failed} ${type.value.unit}失败`
  return st.text
})

/** 节点链（**后端下发**；老任务没有这个字段 ⇒ 空链） */
const nodes = computed(() => props.task?.nodes || [])

/** 后端记的错误（只在失败/部分完成态汇总） */
const errorText = computed(() => {
  const k = state.value.key
  if (k !== 'failed' && k !== 'partial') return ''
  return (props.task?.error || '').trim()
})

// ---------- 节点选中 ----------

const selectedNode = ref(null)
const userPicked = ref(false)

/**
 * 默认选中的节点 key：
 * 1. 失败节点；2. 后端 cur nodeKey；3. running；4. awaiting；5. 首节点
 */
const defaultNodeKey = computed(() => {
  const cur = props.task?.nodeKey
  const failed = nodes.value.find((n) => n.status === 'failed')
  if (failed) return failed.key
  if (cur && nodes.value.some((n) => n.key === cur)) return cur
  const running = nodes.value.find((n) => n.status === 'running')
  if (running) return running.key
  const awaiting = nodes.value.find((n) => n.status === 'awaiting')
  if (awaiting) return awaiting.key
  return nodes.value[0]?.key || null
})

watch(
  () => props.task?.id,
  () => {
    selectedNode.value = defaultNodeKey.value
  },
  { immediate: true }
)

watch(
  () => props.task?.nodeKey,
  (v) => {
    if (!userPicked.value) selectedNode.value = v
  }
)

const currentNode = computed(() => nodes.value.find((n) => n.key === selectedNode.value) || null)

const summarySubText = computed(() => {
  const n = summaryNode.value
  if (!n) return ''
  const bits = []
  if (n.status !== 'ok') bits.push(nodeStatus(n.status).text)
  const c = nodeCount(n)
  if (c) bits.push(c)
  return bits.join(' · ')
})

const summaryNode = computed(
  () =>
    currentNode.value ||
    nodes.value.find((n) => n.key === defaultNodeKey.value) ||
    nodes.value[0] ||
    null
)

function selectNode(key) {
  userPicked.value = true
  selectedNode.value = key
  if (!props.expanded) emit('toggle', props.task.id)
}

function onNodeClick(n, isRail) {
  if (!n) return
  if (n.status === 'awaiting') {
    emit('confirm', props.task, n.key)
    return
  }
  if (n.status === 'failed') {
    emit('retry', props.task)
    return
  }
  if (isRail) selectNode(n.key)
  else toggleOpen()
}

function toggleOpen() {
  if (!props.expanded) {
    if (!userPicked.value) selectedNode.value = defaultNodeKey.value
  }
  emit('toggle', props.task.id)
}

const nodeTitle = (n) => {
  const st = nodeStatus(n.status)
  const bits = [n.name]
  if (n.status !== 'ok' && st.text) bits.push(st.text)
  if (n.summary) bits.push(n.summary)
  if (n.elapsedMs > 0) bits.push('已用时 ' + msText(n.elapsedMs))
  return bits.join(' · ')
}

function nodeCount(n) {
  if (n.total > 0) return `${n.done}/${n.total} 步`
  if (!(n.groups || []).length) return '人工节点'
  return n.summary || ''
}

function msText(ms) {
  if (!ms || ms <= 0) return ''
  if (ms < 1000) return '1 秒'
  return formatSeconds(Math.round(ms / 1000))
}

const nodeTime = (n) => msText(n?.elapsedMs)

const totalElapsedText = computed(() => {
  const t = props.task
  if (!t) return ''
  const list = t.nodes || []
  if (list.length) return msText(list.reduce((n, x) => n + (x.elapsedMs || 0), 0))
  return elapsedText(t)
})

const progressLabel = computed(() =>
  total.value > 0 ? `${done.value} / ${total.value} ${type.value.unit || '条'}` : ''
)

const percent = computed(() => percentText(done.value, total.value))

const barState = computed(() => state.value.key)

const nodeSteps = computed(() => {
  const node = currentNode.value
  if (!node) return { all: [], shown: [] }
  const byKey = new Map()
  for (const s of props.steps || []) {
    const k = s.phase || 'write'
    if (!byKey.has(k)) byKey.set(k, [])
    byKey.get(k).push(s)
  }
  const all = (node.groups || []).flatMap((d) => byKey.get(d.key) || [])
  if (all.length <= 30) return { all, shown: all }
  const keep = new Set([
    ...all.filter((s) => s.status === 'failed' || s.status === 'running').map((s) => s.id),
    ...all.filter((s) => s.status === 'ok').slice(-5).map((s) => s.id)
  ])
  return { all, shown: all.filter((s) => keep.has(s.id)) }
})

const shownSteps = computed(() => nodeSteps.value.shown)

const moreCount = computed(() => nodeSteps.value.all.length - nodeSteps.value.shown.length)

const stepText = (s) => nodeStatus(s).text
</script>

<style scoped>
.card {
  padding: var(--sp-12);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-md);
  background: var(--cc-surface);
  transition: border-color 0.15s, background 0.15s;
}
.card + .card {
  margin-top: var(--sp-12);
}
.card:hover {
  border-color: var(--cc-border-strong);
}
.card.is-open {
  background: var(--cc-surface-2);
  border-color: var(--cc-border-strong);
}
.card__head {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  cursor: pointer;
}
.card__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--cc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__type {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: 99px;
  background: var(--cc-surface-2);
  font-size: 11px;
  font-weight: 500;
  color: var(--cc-text-3);
  white-space: nowrap;
}
.card__st {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--cc-text-3);
}
.card__st.is-running {
  color: var(--cc-task-running);
}
.card__st.is-failed {
  color: var(--cc-danger);
}
.card__st.is-partial {
  color: var(--cc-warning, #b7791f);
}
.card__st.is-awaiting {
  color: var(--cc-warning, #b7791f);
}
.card__st.is-canceled,
.card__st.is-canceling {
  color: var(--cc-warning, #b7791f);
}
.card__head-chev {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-right: 1.5px solid var(--cc-text-4);
  border-bottom: 1.5px solid var(--cc-text-4);
  transform: rotate(45deg);
  transition: transform 0.18s ease;
}
.card__head-chev.is-open {
  transform: rotate(225deg);
}
.card__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: card-pulse 1.6s ease-in-out infinite;
}
@keyframes card-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.35;
    transform: scale(0.8);
  }
}

/* ---------- 收起态：当前阶段摘要 ---------- */
.card__current {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--sp-8);
  width: 100%;
  margin-top: var(--sp-10);
  padding: var(--sp-8);
  border: 1px solid var(--cc-task-running-border);
  border-radius: var(--radius-sm);
  background: var(--cc-task-running-soft);
  color: var(--cc-text-1);
  text-align: left;
  cursor: pointer;
}
.card__current.is-ok {
  border-color: var(--cc-border-strong);
  background: var(--cc-success-soft);
}
.card__current.is-failed {
  border-color: var(--cc-border-strong);
  background: var(--cc-danger-soft);
}
.card__current.is-awaiting,
.card__current.is-paused {
  border-color: var(--cc-border-strong);
  background: var(--cc-warning-soft);
}
.card__current-ico {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--cc-task-running);
  border-radius: 50%;
  background: var(--cc-task-running);
  color: var(--cc-task-running-fg);
}
.card__current-ico::after {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.card__current.is-ok .card__current-ico {
  border-color: var(--cc-success);
  background: var(--cc-success);
}
.card__current.is-ok .card__current-ico::after {
  content: "✓";
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
  font-size: 11px;
  line-height: 1;
  font-weight: 700;
}
.card__current.is-failed .card__current-ico {
  border-color: var(--cc-danger);
  background: var(--cc-danger);
}
.card__current.is-failed .card__current-ico::after {
  content: "!";
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
  font-size: 12px;
  line-height: 1;
  font-weight: 700;
}
.card__current.is-awaiting .card__current-ico,
.card__current.is-paused .card__current-ico {
  border-color: var(--cc-warning, #b7791f);
  background: var(--cc-warning, #b7791f);
}
.card__current-main {
  min-width: 0;
}
.card__current-main b {
  display: block;
  overflow: hidden;
  font-size: 12px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__current-main small {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: var(--cc-text-3);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card__current-side {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
}
.card__current-side small {
  color: var(--cc-text-3);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.card__current-go {
  width: 7px;
  height: 7px;
  border-right: 1.5px solid var(--cc-text-3);
  border-bottom: 1.5px solid var(--cc-text-3);
  transform: rotate(-45deg);
}

/* ---------- 展开态：纵向阶段轨 ---------- */
.rail {
  margin-top: var(--sp-10);
}
.rail__label {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--sp-5);
  color: var(--cc-text-3);
  font-size: 10px;
}
.stage {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 51px;
  padding: var(--sp-5) var(--sp-6);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--cc-text-1);
  text-align: left;
  cursor: pointer;
}
.stage:hover {
  background: var(--cc-surface-2);
}
.stage.is-sel {
  border-color: var(--cc-primary-border);
  background: var(--cc-primary-soft);
}
.stage__rail {
  position: relative;
  align-self: stretch;
  display: grid;
  place-items: center;
}
.stage__rail::after {
  content: "";
  position: absolute;
  z-index: 0;
  top: 30px;
  bottom: -8px;
  left: 13px;
  width: 1px;
  background: var(--cc-border-strong);
}
.stage:last-child .stage__rail::after {
  display: none;
}
.stage__ico {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--cc-border-strong);
  border-radius: 50%;
  background: var(--cc-surface);
  color: var(--cc-text-4);
}
.stage__ico::after {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.stage.is-ok .stage__ico {
  border-color: var(--cc-success);
  background: var(--cc-success-soft);
  color: var(--cc-success);
}
.stage.is-ok .stage__ico::after {
  content: "✓";
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
  font-size: 11px;
  line-height: 1;
  font-weight: 700;
}
.stage.is-running .stage__ico {
  border-color: var(--cc-task-running);
  background: var(--cc-task-running-soft);
  color: var(--cc-task-running);
}
.stage.is-running .stage__ico::after {
  animation: card-pulse 1.4s ease-in-out infinite;
}
.stage.is-failed .stage__ico {
  border-color: var(--cc-danger);
  background: var(--cc-danger-soft);
  color: var(--cc-danger);
}
.stage.is-failed .stage__ico::after,
.stage.is-awaiting .stage__ico::after,
.stage.is-paused .stage__ico::after {
  content: "!";
  width: auto;
  height: auto;
  border-radius: 0;
  background: transparent;
  font-size: 12px;
  line-height: 1;
  font-weight: 700;
}
.stage.is-awaiting .stage__ico,
.stage.is-paused .stage__ico {
  border-color: var(--cc-warning, #b7791f);
  background: var(--cc-warning-soft);
  color: var(--cc-warning, #b7791f);
}
.stage__main {
  min-width: 0;
  padding-left: 2px;
}
.stage__line {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  min-width: 0;
}
.stage__name {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage__state {
  flex: 0 0 auto;
  color: var(--cc-text-3);
  font-size: 10px;
}
.stage__sub {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: var(--cc-text-3);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage__time {
  padding-left: var(--sp-6);
  color: var(--cc-text-4);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.stage.is-sel .stage__time {
  color: var(--cc-text-2);
}

/* ---------- 总进度 ---------- */
.card__prog {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-top: var(--sp-9);
}
.card__prog-n {
  flex: 0 0 auto;
  color: var(--cc-text-3);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}
.card__prog .card__bar {
  flex: 1;
  margin-top: 0;
}
.card__time-alone {
  margin-top: var(--sp-6);
  color: var(--cc-text-3);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

/* ---------- 失败原因 ---------- */
.card__err {
  margin: var(--sp-6) 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--cc-danger);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---------- 进度条 ---------- */
.card__bar {
  height: 4px;
  border-radius: 99px;
  background: var(--cc-surface-3, var(--cc-surface-2));
  overflow: hidden;
}
.card__bar i {
  display: block;
  height: 100%;
  background: var(--cc-task-running);
  transition: width 0.3s;
}
.card__bar i.is-succeeded {
  background: var(--cc-success);
}
.card__bar i.is-failed {
  background: var(--cc-danger);
}
.card__bar i.is-awaiting {
  background: var(--cc-warning, #b7791f);
}
.card__bar i.is-paused,
.card__bar i.is-canceled {
  background: var(--cc-warning, #b7791f);
}

/* 卡片右上角轻量生命周期图标 + 节点直达后缀 */
.card__quick {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: none;
}
.card__qbtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--cc-text-3);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, transform 0.1s;
}
.card__qbtn:hover {
  background: var(--cc-surface-3, var(--cc-border));
  color: var(--cc-text-1);
}
.card__qbtn:active {
  transform: scale(0.94);
}
.card__qbtn.is-warn:hover {
  color: var(--cc-warning, #b7791f);
  background: var(--cc-warning-soft, rgba(183, 121, 31, 0.12));
}
.card__qbtn.is-danger:hover {
  color: var(--cc-danger);
  background: var(--cc-danger-soft);
}
.card__qbtn.is-busy {
  cursor: default;
}
.node-hint {
  margin-left: 6px;
  font-size: 11px;
  font-weight: 500;
}
.node-hint--confirm {
  color: var(--cc-warning, #b7791f);
}
.node-hint--retry {
  color: var(--cc-danger);
}

/* ---------- 细节区 ---------- */
.card__body {
  margin-top: var(--sp-10);
  padding-top: var(--sp-9);
  border-top: 1px solid var(--cc-border);
  max-height: min(52vh, 500px);
  overflow-y: auto;
  scrollbar-gutter: stable;
}
.detail__desc {
  margin: var(--sp-10) 0 var(--sp-6);
  overflow: hidden;
  color: var(--cc-text-2);
  font-size: 11px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card__loading,
.card__note {
  margin: var(--sp-4) 0;
  font-size: 12px;
  color: var(--cc-text-4);
}
.detail__sum {
  margin: 0 0 var(--sp-6);
  padding: var(--sp-6) var(--sp-10);
  background: var(--cc-surface-3, var(--cc-surface-2));
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--cc-text-2);
}
.st__more {
  margin: 0 0 var(--sp-4) 26px;
  color: var(--cc-text-4);
  font-size: 12px;
  letter-spacing: 2px;
}
.st {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  height: 22px;
  font-size: 12px;
  color: var(--cc-text-2);
}
.st__dot {
  flex: 0 0 5px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  border: 1px solid var(--cc-text-4);
  opacity: 0.5;
}
.st.is-ok .st__dot {
  background: var(--cc-success);
  border-color: var(--cc-success);
  opacity: 1;
}
.st.is-running .st__dot {
  background: var(--cc-task-running);
  border-color: var(--cc-task-running);
  opacity: 1;
  animation: card-pulse 1.2s ease-in-out infinite;
}
.st.is-failed .st__dot {
  background: var(--cc-danger);
  border-color: var(--cc-danger);
  opacity: 1;
}
.st.is-pending .st__t {
  color: var(--cc-text-4);
}
.st__seq {
  width: 20px;
  flex: 0 0 20px;
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}
.st__t {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.st__m {
  color: var(--cc-text-4);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.st__m em {
  font-style: normal;
  color: var(--cc-danger);
}
.st__s {
  width: 40px;
  text-align: right;
  color: var(--cc-text-4);
  font-size: 11px;
}
.st.is-failed {
  color: var(--cc-danger);
}
.st__err {
  margin: 0 0 var(--sp-4) 26px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--cc-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cardx-enter-active,
.cardx-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}
.cardx-enter-from,
.cardx-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
