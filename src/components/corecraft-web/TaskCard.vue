<template>
  <article class="card" :class="[`is-${state.key}`, { 'is-open': expanded }]">
    <!-- 第 1 行：类型 + 标题 + **状态（全卡唯一一处）** + 开合箭头（整行可点开合）。 -->
    <header class="card__head" @click="toggleOpen">
      <span class="card__type">{{ type.name }}</span>
      <span class="card__title">{{ task.title || '未命名任务' }}</span>
      <span class="card__st" :class="`is-${state.key}`">
        <i v-if="state.key === 'running'" class="card__dot" />
        {{ statusText }}
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
      @click.stop="toggleOpen"
    >
      <i class="card__current-ico" />
      <span class="card__current-main">
        <b>{{ summaryNode.name }}</b>
        <small>
          {{ nodeStatus(summaryNode.status).text }}<template v-if="nodeCount(summaryNode)"> · {{ nodeCount(summaryNode) }}</template>
        </small>
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

    <!-- 第 5 行：**左下一个主动作 + 右下一个次要动作**（2026-11-12 用户定）。
         - 左下：进行中 ⇒「暂停」；暂停了 ⇒「恢复」；有人工节点 ⇒「确认」；跑坏了 ⇒「重试」。
         - 右下：**删除**。**进行中的任务没有删除**（执行线程还在写步骤，删了它会写回来）。
         两边都直接写成文字按钮，不套「···」—— 一项的菜单要多点一次、还多一层浮层
         （用户原话「里面不是只有一个按钮么」）。 -->
    <div class="card__ops" @click.stop>
      <button
        v-if="primaryAction"
        class="card__op card__op--go"
        :class="{ 'is-warn': primaryAction.tone === 'warn', 'kb-busy': primaryAction.busy }"
        :disabled="primaryAction.busy"
        @click="primaryAction.run"
      >
        <KbLoader v-if="primaryAction.busy" size="sm" />
        {{ primaryAction.label }}
      </button>
      <button
        v-if="secondaryAction"
        class="card__act"
        :class="{ 'is-danger': secondaryAction.key === 'remove' }"
        @click="secondaryAction.run"
      >
        {{ secondaryAction.label }}
      </button>
    </div>

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
            @click="selectNode(n.key)"
          >
            <span class="stage__rail">
              <i class="stage__ico" />
            </span>
            <span class="stage__main">
              <span class="stage__line">
                <b class="stage__name">{{ n.name }}</b>
                <span class="stage__state">{{ nodeStatus(n.status).text }}</span>
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
               不写节点名（轨上选中的那个就是）；说明有才画；右侧只放这个阶段自己的量。 -->
          <div class="detail__head">
            <p v-if="currentNode.desc" class="detail__desc" :title="currentNode.desc">
              {{ currentNode.desc }}
            </p>
            <span v-if="detailMetaText" class="detail__meta">{{ detailMetaText }}</span>
          </div>

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
              <span class="st__s">{{ stepText(s.status) }}</span>
            </div>
            <p v-if="s.status === 'failed' && s.error" class="st__err" :title="s.error">
              {{ s.error }}
            </p>
          </template>
          <!-- 展示不完就用一行省略号（2026-11-13 用户定：不解释、不计数、不铺待跑清单） -->
          <div v-if="moreCount > 0" class="st__more">…</div>

          <p v-if="!shownSteps.length && !currentNode.summary" class="card__loading">
            这个节点还没有步骤
          </p>
        </template>
        <p v-else class="card__loading">没有可展开的节点</p>
      </div>
    </Transition>
  </article>
</template>

<script setup>
/**
 * 任务卡片（**纯展示**：数据从外面传，动作往上派）。
 *
 * ## 形状（收起四行 + 展开区 + 操作行，2026-11-13 收敛版）
 *
 * 1. 类型 + 标题 + **状态**（全卡唯一一处状态）+ 开合箭头（整行可点开合）
 * 2. 总进度（计数 + 进度条同行；条就是百分比，数字不印第二遍）
 * 3. 收起态：当前阶段一条（右侧总已用时，**收起态唯一的信息行**）；失败原因一行（有才画）
 * 4. 操作行：一个主操作（左下）+ 删除（右下）
 * 5. 展开区：阶段轨 + **单阶段详情 —— 所有节点走同一个模板**（说明 → 量 → 摘要 → 步骤平铺）
 *
 * ## ⚠️ 判据只看「状态 + 节点」，**不看任务类型**
 *
 * 类型不同只是**节点链不同**（主题三段 / 归类两段 / 出题一段），
 * 而"等人就该点确认、跑坏了就该点重试、跑着就只剩暂停"对所有类型是同一回事。
 * 所以这张卡里**没有任何 `task.type === 'xxx'` 的分支** ——
 * 以前按类型分叉（归类走采纳框、其余走目录弹窗）会让新接类型时到处补 if。
 *
 * - **正在跑** ⇒ 「暂停」（跑着唯一能做的动作）
 * - **暂停了** ⇒ 「恢复」
 * - **等下一个人工节点** ⇒ 「确认」（开那个节点的产物）
 * - **跑坏了 / 部分失败** ⇒ 「重试」（补跑没成的）
 * - **真的完成** ⇒ 没有主操作（没有动作就不该有按钮）
 *
 * ## ⚠️ 次要动作（右下角）：**进行中的任务没有删除**
 *
 * 执行线程还在写步骤，删了它下一步会把步骤写回来 ⇒ 只能先暂停再删。
 */
import { computed, ref, watch } from 'vue'
import { taskType, taskStatus, isActive, nodeStatus, elapsedText, formatSeconds } from '../../utils/taskTypes'
import { percentText } from '../../utils/progress'
import KbLoader from './KbLoader.vue'

const props = defineProps({
  task: { type: Object, required: true },
  /** 这个任务的步骤（只有"进行中"的才会被外面拉，见 GenTaskPanel） */
  steps: { type: Array, default: () => [] },
  expanded: { type: Boolean, default: false },
  loadingSteps: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle', 'pause', 'resume', 'retry', 'confirm', 'remove'])

const type = computed(() => taskType(props.task?.type))
const state = computed(() => taskStatus(props.task))
const done = computed(() => props.task?.doneCount || 0)
const total = computed(() => props.task?.total || 0)

const statusText = computed(() => {
  const st = state.value
  if (st.key === 'partial') return `部分完成 · ${st.failed} ${type.value.unit}失败`
  return st.text
})

/** 节点链（**后端下发**；老任务没有这个字段 ⇒ 空链） */
const nodes = computed(() => props.task?.nodes || [])

/**
 * 后端记的错误（"4 条失败：知识点ID不能为空!" 这种）。
 *
 * <p>⚠️ **只在"需要人处理"的状态下显示**：跑着的时候 error 是上一次的残留，
 * 已完成的时候更没必要占一行。
 *
 * <p>⚠️ **已暂停不显示**（2026-11-12 用户实测）：暂停是**用户自己按的**，
 * 卡片上却挂一条红字「服务重启，任务中断（点重试接着跑）」—— 既把锅甩给服务，
 * 给的按钮又是「恢复」不是「重试」，文案和动作对不上。
 * 而且暂停卡的主操作就是「恢复」，点下去 `resume()` 会把 error 清空；
 * 具体哪一条失败，展开细节区里逐条写着（`s.status === 'failed' && s.error`）。
 */
const errorText = computed(() => {
  const k = state.value.key
  if (k !== 'failed' && k !== 'partial') return ''
  return (props.task?.error || '').trim()
})

// ---------- 节点选中 ----------

/**
 * **当前展开的节点**。
 *
 * <p>默认选「当前节点」（后端给的 `nodeKey`）—— 没有它就选第一个有内容的。
 * 用户点了别的节点就切过去（**互斥**：一次只显示一个节点）。
 */
const selectedNode = ref(null)

/** 用户手动点过节点吗（点过就别再被自动切换顶掉） */
const userPicked = ref(false)

/**
 * 默认选中的节点 key。
 *
 * <p>顺序 = **用户最想第一眼看到的那一个**：
 * <ol>
 *   <li>后端给的当前节点（`nodeKey`）</li>
 *   <li>**失败节点** —— 任务跑坏了，第一眼就该看到坏在哪（不然默认展开「确认」这种人工节点，
 *       界面上一行错误都没有，用户只能干瞪眼）</li>
 *   <li>进行中 / 等人 / 第一个</li>
 * </ol>
 *
 * ⚠️ **必须声明在下面的 `watch` 之前**：`watch(..., { immediate: true })` 的回调
 * 是**同步**执行的（不是等微任务），而它在回调里读 `defaultNodeKey.value`
 * ⇒ 声明顺序反了就是 **TDZ 报错**，症状是**整张卡片渲染不出来**（任务列表一片空白）。
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

/** 任务换了（列表刷新换了一行）⇒ 选中的节点要重新对齐，否则会停在别的任务的节点上 */
watch(
  () => props.task?.id,
  () => {
    selectedNode.value = defaultNodeKey.value
  },
  { immediate: true }
)

/** 任务本身的 stage 变了（跑到下一段了）⇒ 选中项跟着走，除非用户手动选过 */
watch(
  () => props.task?.nodeKey,
  (v) => {
    if (!userPicked.value) selectedNode.value = v
  }
)

const currentNode = computed(() => nodes.value.find((n) => n.key === selectedNode.value) || null)

/**
 * **收起态显示哪一段**。
 *
 * <p>优先用户手动选中的那一段；否则选默认当前段。收起态不铺完整链，
 * 只回答"现在主要在哪一步"。
 */
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

function toggleOpen() {
  if (!props.expanded) {
    // 展开时把选中项对齐到"当前节点"（用户没手动选过的话）
    if (!userPicked.value) selectedNode.value = defaultNodeKey.value
  }
  emit('toggle', props.task.id)
}

const nodeTitle = (n) => {
  const st = nodeStatus(n.status)
  // ⚠️ **tooltip 不再塞 desc**：描述属于阶段详情，混进节点会让一行里同时出现
  // "节点名 / 状态 / 数量 / 用时 / 任务说明"，哪个都看不清（2026-11-13 用户指出）。
  const bits = [n.name, st.text]
  if (n.summary) bits.push(n.summary)
  if (n.elapsedMs > 0) bits.push('已用时 ' + msText(n.elapsedMs))
  return bits.join(' · ')
}

/** 节点行里的第二层信息：数量优先，人工节点说清楚"没有步骤"。 */
function nodeCount(n) {
  if (n.total > 0) return `${n.done}/${n.total} 步`
  if (!(n.groups || []).length) return '人工节点'
  return n.summary || ''
}

// ---------- 每段各自的已用时 ----------

/**
 * 毫秒 → 人类读法（短版，给节点链用）：`48 秒` / `3 分 12 秒` / `2 小时 36 分`。
 * 格式收在 `taskTypes.js` 的 `formatSeconds`（与收起态同一份）；这里只补
 * 节点链自己的规矩：**不足 10 秒不显示**（「0 秒」是噪音）。
 */
function msText(ms) {
  if (!ms || ms < 10000) return ''
  return formatSeconds(Math.round(ms / 1000))
}

/** 节点上显示的已用时（模型节点才有值；人工节点 elapsedMs 恒 0） */
const nodeTime = (n) => msText(n?.elapsedMs)

// ---------- 时间（收起态唯一的信息行） ----------

/**
 * 总已用时（**永远带「已」**，用户原话「是的话就加一个已字」）。
 *
 * <p>⚠️ **未完成态一律取各节点已用时之和，不碰墙钟**（2026-11-14 用户实测：
 * 刚跑 2 分钟的任务显示「已用时 8 小时 2 分」——部署端序列化的任务是
 * **不带时区的 naive 时间**，容器时区（UTC）与浏览器（+8）的差会整个打进
 * `now − start`；而节点 elapsedMs 是后端按**步骤时长**聚合的，与时钟无关，
 * 暂停 / 待确认自动冻结，进行中最多落后一个在跑的步骤）。
 * **所有状态** ⇒ 各节点已用时之和（2026-11-14 用户定）：机器口径，与节点行同一份
 * 数据，"加不上"的差额消失。墙钟差会把等人确认 / 重试退避都算进去，不再采用。
 * 老任务没有节点链（nodes 为空）⇒ 回落起止差。
 * 「预计还要」2026-11-13 起整个下线（`etaMinutes`/`etaText` 已删）。
 */
const totalElapsedText = computed(() => {
  const t = props.task
  if (!t) return ''
  const nodes = t.nodes || []
  if (nodes.length) return msText(nodes.reduce((n, x) => n + (x.elapsedMs || 0), 0))
  return elapsedText(t)
})

// ---------- 第 4 行：进度条 ----------

/** 卡片顶部永远显示**整任务**进度，不随选中节点变化（设计稿：总进度属于卡片，不属于阶段）。 */
const progressLabel = computed(() =>
  total.value > 0 ? `${done.value} / ${total.value} ${type.value.unit || '条'}` : ''
)

const percent = computed(() => percentText(done.value, total.value))

/** 进度条颜色跟随**任务**状态。以前跟随选中节点，收起后会显示旧节点的颜色，与总进度对不上。 */
const barState = computed(() => state.value.key)

// ---------- 第 5 行：一个主操作 + 更多菜单 ----------

/**
 * **主操作**（最多一个，判据只有「状态 + 节点」）。
 *
 * <ol>
 *   <li>正在跑 ⇒ 没有（结果还不存在，给按钮也没用）</li>
 *   <li>有人工节点等着 ⇒ 「确认」→ 交给外面打开那个节点的产物</li>
 *   <li>失败 / 部分完成 / 已暂停 ⇒ 「重试」</li>
 *   <li>其余（真跑完了）⇒ 没有</li>
 * </ol>
 *
 * <p>⚠️ **文案只说动作**：「确认」就是确认，「重试」就是重试。
 * 以前一律叫「继续」—— 用户不知道"继续什么"。
 */
const primaryAction = computed(() => {
  const t = props.task
  // ⚠️ **顺序有讲究**（2026-11-12 用户定）：
  // ① 进行中 ⇒「暂停」（当前唯一能做的动作）
  if (isActive(t)) {
    // ⚠️ **「正在暂停」不是"没有动作"，是"动作已经发出去了"**（2026-11-12 用户实测）：
    // 原来这里返回 null ⇒ 整行按钮凭空消失，卡片下方一片空白，
    // 用户看到的是"我点了暂停，然后就什么都没有了"。
    // ⇒ 改成一枚**忙碌态**按钮（统一加载层的第四种形态）：图形 + 「正在暂停…」，
    // 既说明"我知道你点了"，也说清"还在收尾、别急着再点"。
    if (t.status === 'canceling') {
      return { key: 'canceling', label: '正在暂停…', busy: true, tone: 'warn', run: () => {} }
    }
    // ⚠️ **暂停用警示色**（2026-11-12 用户定）：主色是"往前推进"的意思，
    // 而暂停是**中断**，两者同色会让"点暂停"看着像"点继续"。
    return { key: 'pause', label: '暂停', tone: 'warn', run: () => emit('pause', t) }
  }
  // ② 暂停了 ⇒「恢复」（用户原话「暂停后这个按钮就变成恢复」）
  if (t.status === 'canceled') {
    return { key: 'resume', label: '恢复', run: () => emit('resume', t) }
  }
  // ③ 有人工节点等着 ⇒ 主操作 = 那个节点自己的名字（2026-11-14）：
  // 归类/目录的人工节点叫「确认」，出题的叫「作答」——按钮跟着节点走，不写死。
  const awaiting = nodes.value.find((n) => n.status === 'awaiting')
  if (awaiting) {
    return { key: 'confirm', label: awaiting.name || '确认', run: () => emit('confirm', t, awaiting.key) }
  }
  // ④ 跑坏了 / 部分失败 ⇒「重试」
  const k = state.value.key
  if (k === 'failed' || k === 'partial') {
    return { key: 'retry', label: '重试', run: () => emit('retry', t) }
  }
  return null
})

/**
 * **次要动作**（永远只有一个，直接摆成文字按钮，靠右下角）。
 *
 * <ol>
 *   <li><b>进行中 ⇒ 没有</b>（执行线程还在写步骤，删了它下一步会把步骤写回来）</li>
 *   <li>其余 ⇒ 「删除」</li>
 * </ol>
 *
 * <p>⚠️ **不设「放弃产物」** —— 删掉任务本身就是放弃，不需要用户理解两个词的差别，
 * 也不该按任务类型各配一套。
 */
const secondaryAction = computed(() => {
  const t = props.task
  // ⚠️ **进行中的任务没有删除**（2026-11-12 用户要求「进行中的任务不能删除」）：
  // 执行线程还在写步骤，删了它下一步会把步骤写回来 ⇒ 只能先暂停再删。
  if (isActive(t)) return null
  return { key: 'remove', label: '删除', run: () => emit('remove', t) }
})

// ---------- 细节区 ----------

/**
 * 当前节点的步骤：**全量 + 平铺展示的子集**（一个 computed 出两份，分组逻辑只写一遍）。
 *
 * <p>⚠️ **只用后端下发的组顺序**（`node.groups`）—— 前端不自己排 phase；
 * 组在这里只是**排序依据**，不是布局输入（2026-11-13 用户定，见 task-protocol skill）。
 *
 * <p>**大节点的展示规则**（>30 步，正文几百条那种）：失败的全留（重试前要看原因）
 * + 进行中 + 最近 5 条完成；**没跑的后续步骤不铺** —— 进度条已经回答了"还有多少"，
 * 一墙「待跑」就是噪音（2026-11-13 用户圈图「眼花缭乱」），展示不完用一行省略号。
 * ≤30 步的（目录十几条）全列，看着一条条变绿。
 */
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

/** 详情头右侧：这个阶段自己的量（已用时 · N 步，有哪样写哪样） */
const detailMetaText = computed(() => {
  const n = currentNode.value
  if (!n) return ''
  const parts = []
  const t = nodeTime(n)
  if (t) parts.push(`已用时 ${t}`)
  if (n.total > 0) parts.push(`${n.total} 步`)
  return parts.join(' · ')
})

/** 有多少步没展示出来（**只用来决定画不画省略号**，不解释不计数）。
 *  ⚠️ 旧实现读 `g.failed`——组对象上没有这个字段，`total - undefined = NaN`
 *  ⇒ `NaN > 0` 恒 false，那句提示从来没显示过；随平铺重写一并修掉。 */
const moreCount = computed(() => nodeSteps.value.all.length - nodeSteps.value.shown.length)

/**
 * 步骤状态文案。
 *
 * <p>⚠️ **不自己写一张表**：步骤和节点是同一套口径（`NODE_STATUS`），
 * 这里原来手写了一份 `{pending:'待跑', ok:'完成', skipped:'跳过', …}` ——
 * 同一个文件上面 `nodeTitle()` 用的是 `nodeStatus()`，于是同一张卡片里
 * 节点说「已跳过」、底下的步骤说「跳过」，改一处忘另一处。
 */
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
/* 展开的那张：底色压一档 + 描边加深，让"我看的是这一张"一眼可见 */
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
  /* 类型做成 chip：以前是"小灰字 + 大黑标题"两种字号挤一行（用户划线指出）——
     chip 有自己的形状，跟标题字号就不打架了。 */
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
  color: var(--cc-primary);
}
.card__st.is-failed {
  color: var(--cc-danger);
}
.card__st.is-partial {
  color: var(--cc-warning, #b7791f);
}
/* 「待确认」不是进行中也不是失败：用提示色，别用绿色（绿=完事了） */
.card__st.is-awaiting {
  color: var(--cc-warning, #b7791f);
}
/* 暂停是**人按的**，用琥珀（2026-11-12 用户原话「暂停应该用黄色吧」）。
   灰色会看着像"这事结束了"，红色又像出错 —— 两者都不对。
   「正在暂停」同色：它和「已暂停」是同一件事的前后两拍，不该换一种颜色。 */
.card__st.is-canceled,
.card__st.is-canceling {
  color: var(--cc-warning, #b7791f);
}
/* 开合箭头：整个头部可点开合（2026-11-13 起「展开详情」按钮退役，箭头就是提示） */
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
/* 进行中的呼吸点：静止的状态文字看不出"还活着" */
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
  border: 1px solid var(--cc-primary-border);
  border-radius: var(--radius-sm);
  background: var(--cc-primary-soft);
  color: var(--cc-text-1);
  text-align: left;
  cursor: pointer;
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
  border: 1px solid var(--cc-primary);
  border-radius: 50%;
  background: var(--cc-primary);
  color: var(--cc-surface);
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
/* 轨道线只表达先后关系，不参与节点信息；最后一个节点不往下延长 */
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
  border-color: var(--cc-primary);
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
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

/* ---------- 总进度（计数 + 条同行；条本身就是百分比，数字不印第二遍） ---------- */
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
/* 老任务（无节点链）时时间单独一行 */
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
  /* 最多两行：再长就不是"扫一眼"了，完整文案在 title 里 */
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
  background: var(--cc-primary);
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
/* 暂停：**一律琥珀**（节点级 `paused` 和任务级 `canceled` 同一件事，
   给两种颜色只会让人以为是两种状态） */
.card__bar i.is-paused,
.card__bar i.is-canceled {
  background: var(--cc-warning, #b7791f);
}

/* ---------- 操作行 ---------- */
.card__ops {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin-top: var(--sp-9);
}
.card__op {
  padding: 2px 0;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--cc-primary);
  cursor: pointer;
}
.card__op:hover {
  text-decoration: underline;
}
/* 忙碌态（正在暂停…）**不是禁用态**：它表示"已经开始做了、还没做完"，
   所以不压淡、只去掉可点的手感和 hover 下划线。 */
.card__op:disabled {
  cursor: default;
}
.card__op:disabled:hover {
  text-decoration: none;
}
.card__op--go {
  text-decoration: none;
}
/* 「暂停」用警示色（用户原话「暂停应该用黄色吧」）：主色的语义是"往前推进"，
   暂停是中断，同色会让这两个动作看起来是一回事。 */
.card__op--go.is-warn {
  color: var(--cc-warning, #b7791f);
}
/* 次要动作：**一个字面按钮，靠右下角**（`margin-left:auto` 推到行尾）。
   不套「···」—— 里面永远只有一项，多一层浮层只是多一次点击。
   常态压得很淡（`--cc-text-4`），hover 才提亮；「删除」hover 变红（不可逆）。 */
.card__act {
  margin-left: auto;
  border: none;
  background: transparent;
  padding: 2px 0;
  border-radius: var(--radius-sm);
  font-size: 12px;
  /* `--cc-text-4` 太淡，看着像禁用（实测在浅底上几乎读不出来）⇒ 用辅助色那档 */
  color: var(--cc-text-3);
  cursor: pointer;
}
.card__act:hover {
  color: var(--cc-text-2);
}
.card__act.is-danger:hover {
  color: var(--cc-danger);
}

/* ---------- 细节区 ---------- */
.card__body {
  margin-top: var(--sp-10);
  padding-top: var(--sp-9);
  border-top: 1px solid var(--cc-border);
  max-height: min(52vh, 500px);
  overflow-y: auto;
}
.detail__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-8);
  margin: var(--sp-10) 0 var(--sp-6);
}
.detail__desc {
  flex: 1;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--cc-text-2);
  font-size: 11px;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.detail__meta {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}
.card__loading,
.card__note {
  margin: var(--sp-4) 0;
  font-size: 12px;
  color: var(--cc-text-4);
}
/* 产物摘要（确认节点等）：数据有就画这一行 —— 所有节点同一个位置同一个样式 */
.detail__sum {
  margin: 0 0 var(--sp-6);
  padding: var(--sp-6) var(--sp-10);
  background: var(--cc-surface-3, var(--cc-surface-2));
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--cc-text-2);
}
/* 展示不完的省略号行：缩进对齐步骤标题，纯提示不可点 */
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
  background: var(--cc-primary);
  border-color: var(--cc-primary);
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
/* 失败原因：贴在那条步骤下面，缩进对齐标题（不是另一套列表） */
.st__err {
  margin: 0 0 var(--sp-4) 26px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--cc-danger);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 展开动效：淡入 + 4px 上移（140ms，与 ⋯ 菜单同档） */
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
