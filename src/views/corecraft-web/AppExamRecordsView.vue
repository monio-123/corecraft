<template>
  <div class="records">
    <!-- 筛选条：类型 / 目录 / 时间（设计 §8.3「考试记录：可按类型/目录/时间查看历史考试」）。
         ⚠️ 状态**只有本页这几个 ref 一份**（不镜像到 URL）：这是一个"翻记录"的浏览页，
         不是链接分享面（可分享的是回看链接 `/exam?attempt=…`，那是另一条路）——
         两处可写就有一处对不上，这里不给自己造第二份。 -->
    <div class="rec-filters">
      <el-select v-model="kindFilter" class="rec-filter" placeholder="全部类型" clearable>
        <el-option v-for="k in kindOptions" :key="k.value" :label="k.label" :value="k.value" />
      </el-select>
      <!-- 目录筛选**复用 CategoryTreeSelect**（目录树的唯一入口）：
           选中一个目录 = 看整棵子树（getCategorySubtreeIds，和列表页/考试范围同一份规则）。
           clearable 给筛选用：清空 = 不限目录。 -->
      <CategoryTreeSelect
        v-model="categoryFilter"
        class="rec-filter rec-filter--cat"
        :categories="allCategories"
        placeholder="全部目录"
        clearable
      />
      <el-select v-model="timeRange" class="rec-filter">
        <el-option v-for="r in TIME_RANGES" :key="r.kind" :label="r.label" :value="r.kind" />
      </el-select>
    </div>

    <!-- 加载中：整块换成**同构行骨架**（不画旧内容——"没变"和"正在变"长得一样，
         见 AppLearnView）。⚠️ 前 160ms 保留旧内容，快请求不闪（2026-11-13 统一加载层）。 -->
    <KbLoading :loading="loading">
      <template #loading>
        <div class="rec-list">
          <KbSkeletonRow v-for="i in 5" :key="i" />
        </div>
      </template>

      <!-- 出错：只摆状态。错误详情由 request.js 弹（全站唯一提示出口），不重复弹一遍 -->
      <div v-if="loadFailed" class="kb-center">
        <p class="kb-error">加载失败</p>
        <el-button @click="refresh">重试</el-button>
      </div>

      <div v-else-if="rows.length" class="rec-list">
        <div v-for="r in rows" :key="r.id" class="rec">
          <div class="rec__body">
            <div class="rec__title">{{ titleOf(r) }}</div>
            <div class="rec__meta">
              <span class="rec__chip">{{ kindLabel(r.kind) }}</span>
              <span v-if="catText(r)" class="rec__cat">{{ catText(r) }}</span>
              <span class="rec__num">{{ r.questionCount }} 题</span>
              <span class="rec__time">{{ timeText(r) }}</span>
            </div>
          </div>
          <!-- 分数：**没有数不写 0**（设计 §12.4「隐含零分路径」在显示层的同一条线）——
               未作答 / 作答中 / 未出分 各说各的状态，出了分才显示分数 -->
          <div class="rec__score" :class="{ 'is-scored': r.lastScore != null }">{{ scoreText(r) }}</div>
          <!-- ⚠️ **进入中 = 按钮忙碌态**（2026-11-13）：图形 + 「进入中」，
               按钮宽度锁住（.rec__open 那一档），不跳宽。
               ⚠️ 文案变长（"查看"→"进入中…"）会撑宽按钮 ⇒ 用 min-width 锁。 -->
          <button type="button" class="rec__open" :disabled="busyId === r.id" @click="openRow(r)">
            <span v-if="busyId === r.id" class="kb-busy">
              <KbLoader size="sm" />
              <span>进入中</span>
            </span>
            <span v-else>{{ openLabel(r) }}</span>
          </button>
          <!-- 从历史里删掉（软删：记录页 / 锚 / 回看不再出现；已产出的证据不动） -->
          <button
            type="button"
            class="rec__del"
            :disabled="busyId === r.id || deletingId === r.id"
            @click="removeRow(r)"
          >删除</button>
        </div>
      </div>

      <!-- 空：筛选生效时的话和"真没有"不是一句（别让界面说假话） -->
      <p v-else class="kb-center kb-center--bare">{{ emptyText }}</p>
    </KbLoading>

    <!-- 分页：只有超过一页才画（一页装得下时画个"1"是噪音）。条件加 !loading 同 AppLearnView -->
    <div v-if="!loading && total > pageSize" class="pager">
      <el-pagination
        layout="prev, pager, next"
        background
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 考试记录（设计 §8.3 / §11.3 的出口）：**历史上考过的卷子都能翻出来**。
 *
 * <p>数据走 `GET /kp/exam/papers`（摘要列表，不拖全卷）：类型 / 目录 / 时间筛选、
 * 分数、题数、时间；点「查看」进逐题回看（题干、你的作答、判定、讲解）——
 * 回看复用考试页的 URL 契约 `/exam?attempt=…&name=…`（AppExamView 的 attempt 分支
 * 自己恢复现场：交过卷的看结果，没交卷的接着答，**同一个入口不用分辨**）。
 *
 * <p>⚠️ **无作答的卷先建一次作答再进**：考试页认 `attempt` 恢复现场，空手进去
 * 会掉进"没有指定要考的知识"（它是按 `ids` 出卷的页面）。建作答很快（只是建记录）。
 *
 * <p>⚠️ **摘要里没有「用时」**（KpPaperSummaryResp 只有交卷/出卷时间）——
 * 设计 §8.3 写了"看用时"，那是等 attempt 摘要带时长后的增强，这里不造假数：
 * 时间栏显示的是**交卷时间（或还没交卷时的出卷时间）**，标注了是哪个。
 */
defineOptions({ name: 'CorecraftWebAppExamRecords' })
import { ref, computed, watch, onMounted, onActivated } from 'vue'
import { ElMessage } from 'element-plus'
import { getPapers, createAttempt, deletePaper } from '../../api/exam'
import { KINDS, kindLabel, openExamAttemptTab } from './exam'
import { fmtScore } from './examParse'
import { confirmAction } from '../../utils/confirm'
import { TIME_RANGES, rangeBounds } from '../../utils/timeRange'
import KbLoading from '../../components/corecraft-web/KbLoading.vue'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import KbSkeletonRow from '../../components/corecraft-web/KbSkeletonRow.vue'
import {
  ensureCategoriesLoaded,
  getCategories,
  getCategorySubtreeIds,
  categoryNameChain
} from './useCorecraftWebStore'
import CategoryTreeSelect from './CategoryTreeSelect.vue'

/** 一页几条（服务端分页，摘要行很轻） */
const pageSize = 20

// ==================== 筛选（每项只有这一个可写的地方） ====================

/** 类型：null = 全部。选项就是 KINDS 那张表（唯一一份卷子类型名） */
const kindFilter = ref(null)
/** 目录：null = 全部。选中 = 含整棵子树（请求参数里展开成 categoryIds） */
const categoryFilter = ref(null)
/** 时间档：TIME_RANGES 的 kind，'all' = 不限 */
const timeRange = ref('all')

const kindOptions = computed(() => Object.entries(KINDS).map(([value, label]) => ({ value, label })))
const allCategories = computed(() => getCategories())

/** 有没有筛选生效（空态文案要分辨"真没有"和"没筛出"） */
const hasFilter = computed(() => !!kindFilter.value || !!categoryFilter.value || timeRange.value !== 'all')
const emptyText = computed(() => (hasFilter.value ? '没有符合条件的考试记录' : '还没有考试记录'))

// ==================== 列表 ====================

const rows = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const loadFailed = ref(false)
/** 正在建作答的那一行（按钮禁用防重复提交） */
const busyId = ref(null)

/** 组请求参数。**目录展开成子树**（和列表页/考试范围同一份规则），时间起止由 timeRange 算 */
function buildParams() {
  const params = { page: page.value, size: pageSize }
  if (kindFilter.value) params.kind = kindFilter.value
  if (categoryFilter.value) {
    params.categoryIds = [...getCategorySubtreeIds(categoryFilter.value)].join(',')
  }
  const bounds = rangeBounds(timeRange.value)
  if (bounds) {
    params.createdFrom = bounds.from
    params.createdTo = bounds.to
  }
  return params
}

/** 在飞的那一次（序号大的才算数——竞态守卫，晚发的先回也不能盖掉新条件的结果） */
let seq = 0

async function refresh() {
  const mine = ++seq
  loading.value = true
  loadFailed.value = false
  try {
    const pageData = (await getPapers(buildParams()))?.data || {}
    if (mine !== seq) return
    rows.value = pageData.records || []
    total.value = Number(pageData.total) || 0
  } catch {
    // request 层已统一报错（全站唯一提示出口），这里只摆状态
    if (mine === seq) loadFailed.value = true
  } finally {
    if (mine === seq) loading.value = false
  }
}

// 筛选变了 → 回第 1 页重拉（翻到第 5 页再换条件，第 5 页多半是空的）
watch([kindFilter, categoryFilter, timeRange], () => {
  page.value = 1
  refresh()
})

function onPageChange(p) {
  page.value = p
  refresh()
}

/**
 * ⚠️ **第一次激活不重拉**（onMounted 的 refresh 已经在拉了）⇒ activatedOnce 区分。
 * 之后每次切回来都重拉：离开这段时间可能又考了一场（和 AppLearnView 同一个口径）。
 */
let activatedOnce = false
onMounted(() => {
  // 目录名（行里的路径、筛选树）要先有；失败不拖累列表（角标式降级：名字显示不出来而已）
  ensureCategoriesLoaded().catch(() => {})
  refresh()
})
onActivated(() => {
  if (activatedOnce) refresh()
  activatedOnce = true
})

// ==================== 行的显示 ====================

function titleOf(r) {
  return String(r.title || '').trim() || kindLabel(r.kind)
}

/** 目录路径（「数据库 / Mybatis」）；没有目录的卷（随堂测验）不显示这一截 */
function catText(r) {
  return r.categoryId ? categoryNameChain(r.categoryId).join(' / ') : ''
}

/**
 * 时间：**交卷时间优先**（那是"这场考试发生在什么时候"），没交卷的报出卷时间。
 * 两个时间都标了是哪个 —— 只给一串数字，用户分不清是出卷还是交卷。
 */
function timeText(r) {
  const t = r.lastSubmittedAt || r.createTime
  return `${r.lastSubmittedAt ? '交卷' : '出卷'} ${String(t || '').slice(0, 16)}`
}

/**
 * 分数栏。**没有数不写 0**（0 是"考了 0 分"的值，和"还没出分"是两件事）：
 * 未作答 → 作答中 → 未出分 → 分数，按作答的真实阶段报。
 */
function scoreText(r) {
  if (r.lastAttemptId == null) return '未作答'
  if (r.lastSubmittedAt == null) return '作答中'
  if (r.lastScore == null) return '未出分'
  return `${fmtScore(r.lastScore)} / ${fmtScore(r.lastMaxScore)} 分`
}

/** 按钮跟着阶段走：没作答过的进去就开答，交过卷的进去回看 */
function openLabel(r) {
  if (r.lastAttemptId == null) return '去作答'
  if (r.lastSubmittedAt == null) return '继续作答'
  return '查看'
}

// ==================== 行的进入 ====================

/**
 * 进入这场考试。**回看 / 续答是同一个 URL**（答题器认 attempt 的状态）；
 * 还没作答过的先建一次作答（考试页恢复现场认的是 attempt，空手进不去）。
 */
async function openRow(r) {
  if (busyId.value) return
  if (r.lastAttemptId) {
    openExamAttemptTab(r.lastAttemptId, titleOf(r))
    return
  }
  busyId.value = r.id
  try {
    const attemptId = (await createAttempt(r.id, {}))?.data
    if (attemptId) openExamAttemptTab(attemptId, titleOf(r))
  } catch {
    // request 层已统一报错；按钮恢复原状即可
  } finally {
    busyId.value = null
  }
}

/** 删除位（防双击） */
const deletingId = ref(null)

/**
 * 从历史里删掉一份卷（软删：记录页 / 锚 / 回看都不再出现；已产出的证据不动）。
 * 失败不清确认框状态——留着让用户直接重试。
 */
async function removeRow(r) {
  if (deletingId.value) return
  if (!(await confirmAction('删除试卷'))) return
  deletingId.value = r.id
  try {
    await deletePaper(r.id)
    ElMessage.success('已删除')
    // 删的是当前页最后一条且不在第一页 ⇒ 退回上一页，别停在空页
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    else await refresh()
  } catch {
    // request 层已统一报错
  } finally {
    deletingId.value = null
  }
}
</script>

<style scoped>
/* 版心：1160 + 居中，和首页/列表页同一档（同壳各页版心一致，#859）。
   ⚠️ 封顶 + 居中是配套的：只封顶不居中 = 左贴边右留白（#862）。 */
.records {
  width: 100%;
  max-width: 1160px;
  margin: 0 auto;
}

/* 筛选条一行 */
.rec-filters {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-12);
}
.rec-filter {
  width: 148px;
}
.rec-filter--cat {
  width: 232px;
}

/* 行卡片：和统计卡同一族形（surface + 描边 + 大圆角） */
.rec {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: var(--sp-11) var(--sp-14);
}
.rec + .rec {
  margin-top: var(--sp-8);
}
.rec__body {
  flex: 1;
  min-width: 0;
}
.rec__title {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--cc-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin-top: var(--sp-4);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
  overflow: hidden;
  white-space: nowrap;
}
.rec__chip {
  flex: none;
  padding: 1px var(--sp-5);
  border-radius: var(--radius-pill);
  background: var(--cc-surface-2);
  font-size: var(--text-xs);
  color: var(--cc-text-3);
}
.rec__cat {
  overflow: hidden;
  text-overflow: ellipsis;
}
.rec__num,
.rec__time {
  flex: none;
  font-variant-numeric: tabular-nums;
}

/* 分数栏：出了分才提亮（中性不染色，语义色只给"有成绩"这一个语义） */
.rec__score {
  flex: none;
  min-width: 110px;
  text-align: right;
  font-size: var(--text-md);
  color: var(--cc-text-3);
  font-variant-numeric: tabular-nums;
}
.rec__score.is-scored {
  color: var(--cc-text-1);
  font-weight: 600;
}

/* 进入按钮：和筛选条同族描边 chip（一行工具条只有一种控件长相，# 2026-11-09 口径） */
.rec__open {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 var(--sp-8);
  border: 1px solid var(--cc-border-strong);
  border-radius: 7px;
  background: var(--cc-surface);
  color: var(--cc-text-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  white-space: nowrap;
  /* ⚠️ **宽度锁死**（2026-11-13）：忙碌态文案从「查看」变成「进入中」，
     不锁宽的话按钮会横向一跳（列表整行都在动）。 */
  min-width: 78px;
  transition: color var(--t-fast, 140ms) var(--ease, ease), border-color var(--t-fast, 140ms) var(--ease, ease);
}
.rec__open:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary-border);
}
.rec__open:disabled {
  color: var(--cc-text-4);
  border-color: var(--cc-border);
  cursor: default;
}
/* 删除：安静的危险色文字钮（描边家族之外唯一染色的动作），与「查看」同高 */
.rec__del {
  flex: none;
  height: 28px;
  padding: 0 var(--sp-6);
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--cc-danger);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  white-space: nowrap;
}
.rec__del:hover {
  background: var(--cc-danger-soft, rgba(192, 85, 77, 0.08));
}
.rec__del:disabled {
  color: var(--cc-text-4);
  cursor: default;
}
</style>
