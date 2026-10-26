<template>
  <!--
    AI 调整正文（右侧抽屉）。

    ## 为什么是抽屉，不是弹窗

    这个功能的场景是**对着正文说话**（「新增一节讲 xxx」「在某个知识点里举个例子」）——
    用户得看着正文想指令。弹窗会把正文盖住，抽屉只占右边一条，正文还在视野里。

    ## 为什么左右两栏

    AI 输出的是**改写后的整篇正文**（不是片段）。整篇替换的风险是"它把你写的东西弄丢了"，
    而那种丢失**在结果里看不出来** —— 少了三段，界面不会报错。
    所以左边摆原文、右边摆新文，同一屏里对着看。

    ## 零写入到库里，直到用户点「应用到正文」

    抽屉里的一切（发指令、看结果、对比）都不碰 kp_topic。
    「应用到正文」也只是把新正文交给详情页，由详情页写进编辑器 ——
    **写库是那条自动保存的链**（见 AppTopicDetailView 的 saveContent）。
  -->
  <el-drawer
    v-model="open"
    direction="rtl"
    :size="`${w}px`"
    :with-header="false"
    modal
  >
    <div class="aie">
      <div class="kb-drawer__head">
        <span class="kb-drawer__title">AI 调整正文</span>
        <button type="button" class="kb-icon-btn" aria-label="关闭" @click="close">
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <!-- 指令区。多行输入：要求通常是一句话，但偶尔要补一句限定 -->
      <div class="aie__composer">
        <textarea
          v-model="instruction"
          class="aie__input"
          rows="3"
          placeholder="说清要怎么改，例如「新增一节讲索引失效的场景」"
          @keydown.ctrl.enter="run"
          @keydown.meta.enter="run"
        />
        <button
          type="button"
          class="aie__send"
          :class="{ 'is-stop': running }"
          :disabled="!running && !canRun"
          :aria-label="running ? '停止生成' : '生成'"
          @click="running ? stop() : run()"
        >
          <el-icon>
            <VideoPause v-if="running" />
            <Top v-else />
          </el-icon>
        </button>
      </div>

      <!-- 对比区。左边原文、右边新文。

           **一个滚动容器 + grid 两列**：两边在同一个滚动容器里 ⇒ 天然一起滚，
           不用两套滚动再写同步逻辑（那种写法还得处理"谁在动"和"滚到头了怎么办"）。

           **每行两个格子**：同一处改动并排成一行（左红右绿），行高取两边较大值
           —— 和 git 的 side-by-side 一个形状。

           **渲染沿用编辑器**：预览容器借 .milkdown-editor-body + .ProseMirror 的壳，
           kb-editor.css 那套排版规则（标题层级、段距、列表、表格、代码块…）直接命中
           ⇒ 看到的就是知识点正文渲染后的格式，**零 CSS 重复**。 -->
      <div class="aie__diff milkdown-editor-body" @click.prevent>
        <!-- 列头：粘在滚动容器顶部（不是各自独立粘）⇒ 滚起来还看得见哪边是哪边 -->
        <div class="aie__head-cell">原文</div>
        <div class="aie__head-cell">
          <span>新文</span>
          <!-- 计数是"流式到底通没通"的信号：从 0 跳到全长 = 上游整段缓冲住了才给我们 -->
          <span v-if="running" class="aie__count">已接收 {{ raw.length }} 字</span>
        </div>

        <!-- 渲染模式：拿到 doc 了（编辑器就绪 + 内容能解析） -->
        <template v-if="diff.mode === 'diff'">
          <template v-for="(row, i) in diff.rows" :key="i">
            <div class="aie__cell" :class="cellClass(row, 'left')">
              <div class="ProseMirror" v-html="row.left ? row.left.html : ''"></div>
            </div>
            <div class="aie__cell" :class="cellClass(row, 'right')">
              <div class="ProseMirror" v-html="row.right ? row.right.html : ''"></div>
            </div>
          </template>
        </template>

        <!-- 兜底：编辑器没就绪 ⇒ 拿不到 doc ⇒ 退回纯文本两栏 -->
        <template v-else>
          <div class="aie__cell">
            <pre class="aie__text">{{ original || '（空）' }}</pre>
          </div>
          <div class="aie__cell">
            <pre class="aie__text">{{ preview }}</pre>
          </div>
        </template>
      </div>

      <div class="aie__foot">
        <span class="aie__hint">{{ hint }}</span>
        <button type="button" class="kb-text-btn" @click="close">放弃</button>
        <button type="button" class="aie__apply" :disabled="!canApply" @click="apply">
          应用到正文
        </button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Top, VideoPause } from '@element-plus/icons-vue'
import { ask } from './useAi'
import { parseEditedBody, buildEditMaterial } from './aiEdit'
import { blocksOf, diffRows } from './docDiff'
import { getPrefSpec, getUiPrefs } from '../../utils/uiPrefs'

defineOptions({ name: 'AiEditDrawer' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 当前正文。发给 AI 的原文取的是它 —— 也就是屏幕上那一版，含还没保存的编辑 */
  content: { type: String, default: '' },
  /**
   * markdown → ProseMirror doc。**由详情页从编辑器转发过来**（编辑器在
   * MilkdownProvider 里面，外层拿不到它的 ctx）。
   *
   * 对比区要显示"和正文同一套"的渲染 ⇒ 必须用编辑器自己的解析器，
   * 不能另找一个 markdown 渲染器（那会出现两套渲染）。
   * 没有它（编辑器没就绪）⇒ 退回纯文本两栏。
   */
  docOf: { type: Function, default: null }
})
const emit = defineEmits(['update:modelValue', 'apply'])

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 抽屉宽度。**JS 值不是 CSS 变量**（el-drawer 的 size 会给 var() 拼 px 后缀，
// 变成非法宽度，抽屉直接打不开）—— 与问答抽屉那一项同一件事。
// 单独一项而不是复用问答抽屉的：两边形态不同。问答是一问一答，420px 够；
// 这里左右两栏并排，分两半每栏只剩 210px，根本读不下去 ⇒ 默认给 720。
const EDIT_W_KEY = 'ui.detail.detailEditW'
const EDIT_W = computed(() => getPrefSpec(EDIT_W_KEY))

/** 上限跟着视口走：抽屉盖过七成，正文就没法看了 */
function clampW(v) {
  if (!EDIT_W.value) return Math.round(v)
  const cap = Math.min(EDIT_W.value.max, Math.round(window.innerWidth * 0.7))
  return Math.min(Math.max(Math.round(v), EDIT_W.value.min), cap)
}
/**
 * **每次算的时候现读偏好**，不在 setup 时拍一份：
 * 用户在偏好面板里拖完「编辑宽」再打开抽屉，拿到的是新值，不是打开这个页面那一刻的值。
 */
const w = computed(() => clampW(getUiPrefs()[EDIT_W_KEY] ?? EDIT_W.value?.defaultValue))

// ---------- 这一轮的状态 ----------
const instruction = ref('')
const raw = ref('')        // AI 的原始输出（**逐帧累加**：流式期间界面上要跟着长）
const running = ref(false)
const original = ref('')   // 这次发给 AI 的原文（对比时左边那一栏）
let controller = null

/** 解析出来的正文。解析不出来是 null（见 aiEdit.js：解析不出来就不给应用入口） */
const parsed = computed(() => parseEditedBody(raw.value))
/** 右栏显示的东西：解析得出来就是正文本体，解析不出来就把原话摆出来 */
const preview = computed(() => parsed.value ?? raw.value)

// ---------- 对比（渲染 + git 语义 diff） ----------
/**
 * 节流后的对比输入。
 *
 * **为什么要节流**：解析（remark → ProseMirror）+ 序列化（doc → HTML）都不便宜，
 * 流式期间每来一个 token 两边就都变一次 ⇒ 每个 token 都重算一遍，界面会卡。
 * 250ms 一次：人眼看不出延迟，计算量降到九成以下。
 */
const DIFF_INTERVAL = 250
const diffInput = ref({ left: '', right: '' })
let diffTimer = null
let diffPending = null

function flushDiff() {
  diffTimer = null
  if (!diffPending) return
  diffInput.value = diffPending
  diffPending = null
}
function scheduleDiff() {
  diffPending = { left: original.value, right: preview.value }
  if (diffTimer) return
  diffTimer = setTimeout(flushDiff, DIFF_INTERVAL)
}
watch([original, preview], scheduleDiff, { immediate: true })

/**
 * 这一轮的对比结果。**一次解析，两边共用**（docOf 不便宜，别调两遍）。
 *
 * 拿不到 doc（编辑器没就绪 / 内容不是合法 markdown）⇒ mode='text'，
 * 界面退回纯文本两栏 —— 宁可丑一点，也不能空着让人以为"AI 把正文删了"。
 */
const diff = computed(() => {
  if (typeof props.docOf !== 'function') return { mode: 'text', rows: [] }
  const { left, right } = diffInput.value
  const oldDoc = props.docOf(left)
  const newDoc = props.docOf(right)
  // 左边解析不出来（编辑器没就绪 / 不是合法 markdown）⇒ 整栏退回纯文本
  if (left.trim() && !oldDoc) return { mode: 'text', rows: [] }
  // 右边**有内容却**解析不出来 ⇒ 同样退回纯文本。
  // 不这么判的话右栏会是空的 —— 看着像"AI 什么都没产出"，比丑更糟。
  if (right.trim() && !newDoc) return { mode: 'text', rows: [] }

  const oldBlocks = blocksOf(oldDoc)
  const newBlocks = blocksOf(newDoc)
  // 右边还没内容（还没生成）⇒ **不算"全删了"**：左边整篇按原样摆出来，右边空着
  if (!newBlocks.length && oldBlocks.length) {
    return {
      mode: 'diff',
      rows: oldBlocks.map((b) => ({ type: 'equal', left: b, right: null }))
    }
  }
  return { mode: 'diff', rows: diffRows(oldBlocks, newBlocks) }
})

/**
 * 一个格子的类名。**git 语义**：
 * - `is-del`  左边被删掉的块 → 红
 * - `is-add`  右边新增的块 → 绿
 * - `changed`（同一处改动的两个版本）⇒ 左红右绿
 */
function cellClass(row, side) {
  if (row.type === 'equal') return ''
  if (row.type === 'changed') return side === 'left' ? 'is-del' : 'is-add'
  if (row.type === 'removed') return side === 'left' ? 'is-del' : ''
  if (row.type === 'added') return side === 'right' ? 'is-add' : ''
  return ''
}

const canRun = computed(() => !running.value && instruction.value.trim().length > 0)
/** 只有解析成功才有应用入口 —— 写进库比不写更糟 */
const canApply = computed(() => !running.value && parsed.value !== null)

const hint = computed(() => {
  if (running.value) return '正在生成…'
  if (raw.value && !parsed.value) return 'AI 没按要求的格式输出，下面是它的原话。要用的话自己复制。'
  return ''
})

async function run() {
  if (!canRun.value) return
  const text = instruction.value.trim()
  // 原文在**发请求之前**拍一份：对比看的是这次发给 AI 的那版，
  // 不是"此刻编辑器里是什么"（中间用户可能又改过）。
  original.value = props.content || ''
  raw.value = ''
  running.value = true
  controller = new AbortController()
  try {
    await ask(text, {
      // 要求和原文一起走 material（**不能走 question**）：
      // 后端 questionKey 命中配置时会把用户自己敲的问题整段替换掉。
      material: buildEditMaterial(text, original.value),
      // 完整键 ⇒ 不在 ai.prompt.question. 下面 ⇒ 不会混进问答抽屉的按钮
      questionKey: 'ai.prompt.edit.body',
      signal: controller.signal,
      // 逐帧累加：流式期间右栏要跟着长（`raw` 也是"已接收多少字"的来源）
      onDelta: (t) => { raw.value += t }
    })
  } catch (e) {
    // 用户点「停止」不是错误
    if (e?.name === 'AbortError') return
    ElMessage.error(e?.message || 'AI 调用失败')
  } finally {
    running.value = false
    controller = null
  }
}

function stop() {
  controller?.abort()
}

function apply() {
  if (!canApply.value) return
  emit('apply', parsed.value)
}

function close() {
  open.value = false
}

/**
 * **关掉抽屉 = 这一轮作废**。
 *
 * <p>两件事一起做：① 停掉还在跑的请求（抽屉没了，模型还在烧额度）
 * ② 清掉这一轮的结果（原文、输出、对比区、指令）。
 *
 * <p>⚠️ **换一条知识也走这一条**：详情页在换知识时会关掉抽屉
 * （`editOpen = false`）⇒ "上一条的结果不能带到下一条"这条由它管住。
 * 以前这里**另有一个 `watch(topicId)` 在清同一批状态** —— 同一条规则两处，
 * 漏一处就出现"把 A 的改写应用到 B"。现在只有这一条。
 */
watch(open, (v) => {
  if (v) return
  controller?.abort()
  controller = null
  instruction.value = ''
  raw.value = ''
  original.value = ''
  // 对比区同步清空：不等那 250ms 的节流（不然会闪一下上一条的内容）
  diffPending = null
  if (diffTimer) { clearTimeout(diffTimer); diffTimer = null }
  diffInput.value = { left: '', right: '' }
})
</script>

<style scoped>
/* 和详情页那个问答抽屉同一套骨架：上头一条标题、中间可滚、底下一条操作栏。
   头的样式（标题 + 分隔线）已提到 assets/kb.css 的 .kb-drawer__head —— 
   两个抽屉的头逐字符相同，各写一份就是改一处漏一处。 */
.aie {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 指令区：多行输入 + 右下一个圆钮 */
.aie__composer {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-6);
  flex: none;
  margin-top: var(--sp-10);
  padding: var(--sp-6);
  background: var(--cc-surface-2);
  border: 1px solid transparent;
  border-radius: var(--radius-lg);
  transition: var(--cc-transition);
}
.aie__composer:focus-within {
  background: var(--cc-surface);
  border-color: var(--cc-border);
}
.aie__input {
  flex: 1;
  min-width: 0;
  padding: 0;
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-1);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
}
/* 发送 / 停止 是同一个钮的两种状态，不并排两个按钮 */
.aie__send {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--ctl-sm);
  height: var(--ctl-sm);
  padding: 0;
  font-size: var(--text-base);
  color: var(--cc-surface);
  background: var(--cc-primary);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: var(--cc-transition);
}
.aie__send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ---------- 对比区 ----------
   **一个滚动容器 + grid 两列**：两边天然一起滚（不用两套滚动 + 同步逻辑）。
   每行两个格子 ⇒ 同一处改动并排成一行，行高自动取两边较大值。 */
.aie__diff {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-10);
  align-content: start;
  padding-bottom: var(--sp-10);
}
/* 列头：粘在滚动容器顶部 ⇒ 滚起来还看得见哪边是哪边。
   上边距放在这一格自己身上（不放容器上）：放容器上 sticky 会差一截。 */
.aie__head-cell {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-6);
  padding: var(--sp-10) 0 var(--sp-4);
  background: var(--cc-surface);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.aie__count {
  font-size: var(--text-sm);
  color: var(--cc-text-4);
}
/* 一个格子 = 一块。min-width:0 是必须的：grid 子项默认 min-width:auto，
   一段不换行的长代码能把整栏撑破。 */
.aie__cell {
  min-width: 0;
  padding: 0 var(--sp-6);
  border-left: 3px solid transparent;
}
/* git 语义：删=red、加=green。**只做底色 + 左边一条**，不动文字颜色 ——
   文字颜色一动，长文读起来就花了。 */
.aie__cell.is-del {
  background: color-mix(in srgb, var(--cc-danger) 9%, transparent);
  border-left-color: var(--cc-danger);
}
.aie__cell.is-add {
  background: color-mix(in srgb, var(--cc-success) 9%, transparent);
  border-left-color: var(--cc-success);
}

/* ---------- 预览借的壳 ----------
   `.milkdown-editor-body` + `.ProseMirror` 是**编辑器自己的类**：
   套上它们，kb-editor.css 那套排版规则（标题层级、段距、列表、表格、代码块、
   软换行、链接…）直接命中 ⇒ 看到的就是知识点正文渲染后的格式，**零 CSS 重复**。

   下面几条是"编辑器里有、预览里不能有"的，必须盖掉。 */
.aie__diff .ProseMirror {
  /* 编辑器里 ProseMirror 自己是一个滚动区（min-height 135 / max-height 80vh /
     overflow-y auto）—— 预览里滚的是外层那个容器 ⇒ 这几条必须去掉，
     否则每个格子自己滚，两边就对不齐了。 */
  min-height: 0;
  max-height: none;
  height: auto;
  overflow: visible;
  /* 左右 34px 白边（--kb-gutter）是给折叠箭头留的位置，预览里没有箭头 ⇒ 去掉，
     两栏本来就窄。 */
  padding: 0;
}
/* 标题：编辑器里标题盒子往左探出去装折叠箭头（负边距 + 等量 padding）。
   预览里没有箭头 ⇒ 探出去就探到格子外面了 ⇒ 归零。
   （用 :deep —— v-html 进来的节点没有 scoped 的 data-v 属性。） */
.aie__diff .ProseMirror :deep(h1),
.aie__diff .ProseMirror :deep(h2),
.aie__diff .ProseMirror :deep(h3),
.aie__diff .ProseMirror :deep(h4),
.aie__diff .ProseMirror :deep(h5),
.aie__diff .ProseMirror :deep(h6) {
  margin-left: 0;
  padding-left: 0;
}
/* 图片：编辑器里图片走的是 **nodeView**（`.kb-img` 那层 span），
   而 DOMSerializer 只还原 toDOM 里的裸 `<img>` ⇒ 收边那条规则（max-width）
   在预览里就丢了，宽图会顶破格子。补上编辑器里图片那条（见 kb-editor.css）。 */
.aie__diff .ProseMirror :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-sm);
  vertical-align: bottom;
}

/* 兜底模式的纯文本（编辑器没就绪时才用） */
.aie__text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--cc-text-2);
}

@media (max-width: 900px) {
  .aie__diff {
    grid-template-columns: 1fr;
  }
}

.aie__foot {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  flex: none;
  padding-top: var(--sp-10);
  border-top: 1px solid var(--cc-border);
}
.aie__hint {
  flex: 1;
  min-width: 0;
  font-size: var(--text-sm);
  color: var(--cc-text-4);
}
.aie__apply {
  flex: none;
  padding: var(--sp-4) var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-surface);
  background: var(--cc-primary);
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: var(--cc-transition);
}
.aie__apply:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
