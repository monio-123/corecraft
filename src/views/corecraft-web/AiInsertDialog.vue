<template>
  <!--
    把 AI 的这段回答**用到知识库里**的确认框。

    ## 为什么是弹窗，不是抽屉

    抽屉的语义是"持续对话"（问答抽屉那样），弹窗的语义是"这一下要不要做"。
    这一步是**一次确认**：看完内容，认了就用、不认就回去接着聊 ——
    两件事混在一个抽屉里会互相牵扯（关掉抽屉 = 作废？改完了 = 走了？）。

    ## 三种形态（mode × 这一轮交回来的是什么）

    - **改写**（应用到正文 + 这一轮带【正文开始】/【正文结束】）：
      左「原文」右「改写后」，**左=这一轮发给 AI 时的那版正文，右=AI 交回的整篇新正文**，
      逐块对照（git 的 side-by-side 形状）。确认后**整篇替换**。
      ⇒ 增 / 删 / 改任意组合都表达得了：AI 自己定改什么、怎么改（提示词说了算），
      应用侧只认那两行标记 —— **不解析意图**。
    - **追加**（应用到正文 + 这一轮没有标记）：左「原文（选中段）」右「写入后」，
      新增块标绿。确认后作为独立新段落插在选中段落之后。
    - **新知识点**（应用为新的知识点）：左「出处」右「新知识点」——
      **右边不再是"原文+新增"，而是新知识点自己的正文**（那几段解释搬走了，
      原正文这边只留一条指向它的链接）。同一个内容、两个去处。

    ## 全文 diff 默认滚到第一处差异

    改写是**全文**对照，一篇长文前面大半是没改的部分 ⇒ 停在顶上等于没看见改动。
    所以打开就滚到**第一处有差异的行**（git diff 干的事）。

    ## 右栏那一格是**能改的**（2026-11-05 用户定）

    追加 / 新知识点这两种形态里，右栏那一格不是只读预览，而是一个**真的编辑器**
    （正文同一套）—— AI 写得太啰嗦、太简略，用户当场改完再确认，不必退回抽屉重来一遍。

    ⚠️ **只让"新增"那部分可编辑，原文那部分仍是只读**：写进去的动作是
    "在这段之后另起一段"，原文一个字节都不动。真让用户能删原文，
    一个手滑（全选删除）就把自己的笔记抹了 —— 而这个框的本意是"看一眼再写"。

    ## 框里就能让 AI 再调一版（2026-11-05 用户定）

    右栏底下那条药丸 = 「你生成的太细了，大概解释一下就行」。
    填完点右边的箭头，AI 照着这句话重写一版，**diff 原地刷新** ——
    不用退回抽屉重新问一遍、再点一次「应用到正文」。
    （改写形态下那句话是对**正文**的要求 ⇒ 走同一套「可改写」约定，
    交回的还是一份改写后的整篇正文。）

    ⚠️ **生成期间右栏要看得见在动**（2026-11-05 用户反馈）：
    只让按钮转圈、右边一动不动 = 用户以为卡死了。所以生成期间右栏换成
    **只读的流式预览**（内容一点点长出来）+ 顶部一条进度条；
    失败时**错误原文直接摆出来**（上一版失败只在抽屉里报错，弹窗把它挡住了，
    用户只看到"转圈转完了、内容没变"）。
  -->
  <el-dialog
    v-model="open"
    :title="dialogTitle"
    width="min(clamp(var(--cc-dialog-w-xl), 72vw, 1280px), calc(100vw - 32px))"
    @close="close"
    @opened="scrollToFirstDiff"
  >
    <!-- 标题输入：**只有「应用为新的知识点」才有**（应用到正文不需要标题）。
         预填值从 AI 回答的第一个标题行里提取（见 suggestedTitle），
         用户改过之后就不再被 AI 的新版本覆盖（titleTouched）。 -->
    <div v-if="isNewTopic" class="kb-diff__meta">
      <label class="kb-diff__label">标题</label>
      <input
        v-model="topicTitle"
        class="kb-diff__title-input"
        @input="titleTouched = true"
      />
    </div>

    <!-- **一个滚动容器 + grid 两列**：两边在同一个滚动容器里 ⇒ 天然一起滚，
         不用两套滚动再写同步逻辑（那种写法还得处理"谁在动"和"滚到头了怎么办"）。

         **每行两个格子**：同一处改动并排成一行（左红右绿），行高取两边较大值
         —— 和 git 的 side-by-side 一个形状。

         **渲染沿用编辑器**：预览容器借 .milkdown-editor-body + .ProseMirror 的壳，
         kb-editor.css 那套排版规则（标题层级、段距、列表、表格、代码块…）直接命中
         ⇒ 看到的就是知识点正文渲染后的格式，**零 CSS 重复**。 -->
    <!-- ⚠️ **这里不能有 `@click.prevent`**：那一层 preventDefault 会把点击的默认行为
         也拦掉 —— 可编辑那一格的编辑器里就点不出光标了（contenteditable 定位光标
         走的就是 click 的默认行为）。 -->
    <div class="kb-diff-wrap" :class="{ 'is-single': !hasLeft }">
      <div ref="diffEl" class="kb-diff milkdown-editor-body" :class="{ 'is-single': !hasLeft }">
        <!-- 列头：粘在滚动容器顶部（不是各自独立粘）⇒ 滚起来还看得见哪边是哪边 -->
        <div v-if="hasLeft" class="kb-diff__head">{{ leftHead }}</div>
        <div class="kb-diff__head">{{ rightHead }}</div>

        <!-- 生成中：右栏**整栏**换成一块只读的流式预览。
             ⚠️ **不是每一行都来一份** —— 那样同一段内容会重复好几遍
             （上一版就是每行都渲染一次，一段正文被摊成三四段）。 -->
        <template v-if="adjusting">
          <div v-if="hasLeft" class="kb-diff__cell">
            <div class="ProseMirror" v-html="leftWholeHtml"></div>
          </div>
          <div class="kb-diff__cell">
            <div class="kb-diff__streaming">
              <div class="kb-diff__progress"><i /></div>
              <div class="ProseMirror" v-html="streamHtml"></div>
            </div>
          </div>
        </template>

        <template v-else>
          <template v-for="(row, i) in rows" :key="i">
            <div v-if="hasLeft" class="kb-diff__cell" :class="cellClass(row, 'left')" :data-row="i">
              <div class="ProseMirror" v-html="row.left ? row.left.html : ''"></div>
            </div>
            <div class="kb-diff__cell" :class="cellClass(row, 'right')">
              <!-- 可编辑那一格：**一个真的编辑器**（正文同一套解析器 + 排版），
                   用户改完确认应用的就是改过的那一份。

                   ⚠️ **`:key` 是必须的**：AI 换了一版答案后，光改 v-model 没用 ——
                   **外层改 v-model 不会更新已建的 ProseMirror 视图**（视图只在初始化时
                   读一次 modelValue）⇒ 换 key 重建编辑器，新内容才真的进去了。
                   （原来想用 `ref` + `replaceContent` 换内容，但这个编辑器在 v-for 里，
                   模板 ref 拿到的是**数组**，不是组件实例 ⇒ `replaceContent is not a function`。） -->
              <MilkdownEditor
                v-if="row.editable"
                :key="editorKey"
                v-model="editedMd"
                :show-toolbar="false"
              />
              <div v-else class="ProseMirror" v-html="row.right ? row.right.html : ''"></div>
            </div>
          </template>
        </template>
      </div>

      <!-- 重新生成失败：**错误原文直接摆出来，零前缀**（接口错误不许改写）。
           上一版失败只在抽屉里报错，而抽屉被这个弹窗挡着 ⇒ 用户只看到
           "转圈转完了、内容没变"，根本不知道出了什么事。 -->
      <div v-if="adjustError" class="kb-diff__err">{{ adjustError }}</div>

      <!-- 让 AI 再调一版。**不用退回抽屉重来**：填一句要求，AI 重写一版，diff 原地刷新。
           位置在**右栏正下方**（宽度与右栏一致）—— 这一句是**对右边那一栏的修改**，
          横贯整个弹窗反而像临时塞进来的表单。 -->
      <div class="kb-diff__ask" :class="{ 'is-busy': adjusting }">
        <span class="kb-diff__ask-ico">
          <KbSvgIcon v-if="!adjusting" name="sparkle" />
          <!-- ⚠️ **流式预览保持独立**（2026-11-13 统一加载层）：
               这一段是"AI 正在写"的真实进行中状态，**不是"在加载"**，
               所以不套普通加载图形，只把转圈换成统一图形（主题跟随）。
               ⚠️ 流式内容 / 进度条 / 停止能力一律不动。 -->
          <KbLoader v-else size="sm" />
        </span>
        <input
          v-model="askText"
          class="kb-diff__ask-input"
          aria-label="让 AI 再调整"
          :disabled="adjusting"
          @keyup.enter="adjust"
        />
        <span v-if="adjusting" class="kb-diff__ask-state">生成中</span>
        <button
          v-else
          type="button"
          class="kb-diff__ask-send"
          aria-label="重新生成"
          :disabled="!askText.trim()"
          @click="adjust"
        >
          <el-icon><Top /></el-icon>
        </button>
      </div>
    </div>

    <template #footer>
      <!-- ⚠️ §4.11(h)：原来这里是 size="small"(24px)，而 base.css:445 把
           .el-dialog__footer .el-button 钉成 --ctl-lg(40px) ⇒ 高度被覆盖回 40px，
           **只有"字号压到 12px"这一半生效**。
           ⇒ 删掉 size，字号交回 base.css 那条统一规则。 -->
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="!canWrite" :loading="saving" @click="confirm">
        {{ primaryText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Top } from '@element-plus/icons-vue'
import { blocksOf, blocksOfNodes, diffRows } from './docDiff'
import { parseEditedBody } from './aiEdit'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import KbLoader from '../../components/corecraft-web/KbLoader.vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'

defineOptions({ name: 'AiInsertDialog' })

const open = defineModel({ type: Boolean, default: false })
const props = defineProps({
  /** AI 这一轮的回答（markdown）。要写进正文的就是它 */
  answer: { type: String, default: '' },
  /**
   * **这一轮发给 AI 时用的正文本体**（改写形态的左栏就是它）。
   *
   * ⚠️ **不是"此刻编辑器里是什么"**：中间用户又编辑过的话，AI 的改写是基于旧正文的，
   * 左栏摆旧正文、右栏摆新正文才是真的"它改了什么"（拿新的比 ⇒ 两头都不对）。
   */
  baseMd: { type: String, default: '' },
  /**
   * 写入锚点：`{ blockFrom, blockTo, blockText }`。
   *
   * - blockFrom / blockTo：选中段落在编辑器里的块区间（doc 位置）
   * - blockText：那时候这段的纯文本 —— **用来判"正文是不是已经被改过了"**
   */
  spot: { type: Object, default: null },
  /**
   * 取一段范围里的顶层块。**由详情页从编辑器转发过来**（编辑器在
   * MilkdownProvider 里面，外层拿不到它的 ctx）。
   *
   * 对比区要显示"和正文同一套"的渲染 ⇒ 必须用编辑器自己的解析器，
   * 不能另找一个 markdown 渲染器（那会出现两套渲染）。
   */
  blocksInRange: { type: Function, default: null },
  /** markdown → ProseMirror doc（编辑器自己的解析器） */
  docOf: { type: Function, default: null },
  /** AI 正在按框里那句话重写一版 */
  adjusting: { type: Boolean, default: false },
  /** 生成期间的实时 HTML（详情页节流算好；直接给 HTML 是为了不在这边重复解析） */
  streamHtml: { type: String, default: '' },
  /** 重新生成失败时的错误原文（零前缀直出） */
  adjustError: { type: String, default: '' },
  /** 'body' = 应用到正文；'topic' = 应用为新的知识点 */
  mode: { type: String, default: 'body' },
  /** 正在落库（建新知识点那条链） */
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['confirm', 'adjust'])

/** 「应用为新的知识点」模式 */
const isNewTopic = computed(() => props.mode === 'topic')

/**
 * AI 交回来的**改写后的整篇正文**（带【正文开始】/【正文结束】才有）。
 *
 * **没有标记 ⇒ 这一轮不是改正文**（是解释、比较、提问…）⇒ 走追加那条路。
 * 应用侧不解析 AI 的意图，只有这一条判据。
 */
const rewriteMd = computed(() => parseEditedBody(props.answer))

/** 改写形态：应用到正文 + 这一轮交回的是一份整篇新正文 */
const isRewrite = computed(() => !isNewTopic.value && !!rewriteMd.value)

/**
 * 左边有没有内容。
 *
 * - 应用到正文：一定有（那一步本来就只挂在"选了一段"的那一轮上）
 * - 应用为新的知识点：没有选段就没有"出处" ⇒ 单栏，别留一列空格子
 */
const hasLeft = computed(() => !isNewTopic.value || !!props.spot)

const leftHead = computed(() => (isNewTopic.value ? '出处' : '原文'))
const rightHead = computed(() => (isNewTopic.value ? '新知识点' : (isRewrite.value ? '改写后' : '写入后')))
/**
 * **这个按钮/标题上写什么**（**只有一份** —— 标题和按钮本来是同一句话，
 * 各写一遍的话改文案漏一处 ⇒ 标题写着"应用到正文"、按钮写着"应用为新的知识点"）。
 */
const actionLabel = computed(() => (isNewTopic.value ? '应用为新的知识点' : '应用到正文'))
const dialogTitle = computed(() => actionLabel.value)
const primaryText = computed(() => actionLabel.value)

/**
 * **markdown → 块**（给左右两栏用）。
 *
 * <p>⚠️ **只有这一份**：左栏（发出去的那版）、右栏（AI 交回来的那版）、
 * 新增块三个 computed 原来各写一遍一模一样的四行
 * （判函数 → 调 `docOf(md)` → `blocksOf(doc)`）⇒ 改一边漏一边。
 *
 * @param {string} md markdown 原文（空 = 没有）
 * @returns {Array} 块数组
 */
function blocksOfMarkdown(md) {
  if (typeof props.docOf !== 'function' || !md) return []
  const doc = props.docOf(md)
  return doc ? blocksOf(doc) : []
}

/** 左边：选中那段的原文块 */
const leftBlocks = computed(() => {
  if (typeof props.blocksInRange !== 'function' || !props.spot) return []
  const nodes = props.blocksInRange(props.spot.blockFrom, props.spot.blockTo) || []
  return blocksOfNodes(nodes.map((b) => b.node))
})

/** 改写形态的左栏 = **这一轮发给 AI 时的那版正文**（整篇） */
const baseBlocks = computed(() => blocksOfMarkdown(props.baseMd))

/** 改写形态的右栏 = AI 交回来的整篇新正文 */
const newBlocks = computed(() => blocksOfMarkdown(rewriteMd.value))

/** 新写进去的那几块（追加形态：AI 的回答解析出来的块） */
const addedBlocks = computed(() => blocksOfMarkdown(props.answer))

/**
 * 左右两栏的行。
 *
 * **改写**：左=原文全文、右=改写后全文 ⇒ 逐块对照。改写可能动的是正文里
 * 任意一处（增/删/改任意组合），所以**必须全文对照** —— 只看选中段那一块
 * 会漏掉 AI 顺手改掉的别处。
 *
 * **追加**：右栏 = 原文 + 新增（写入位置是"选中段落之后另起一段"，
 * 所以原来那几块原样保留，新块接在后面）⇒ diff 出来就是
 * 「原文一行不动 + 后面几行绿色新增」。新增的那几行**合成一格**：里面放可编辑器。
 *
 * **新知识点**：右边不是"原文+新增"，而是**新知识点自己的正文**
 * （那几段解释搬走了，原正文这边只留一条指向它的链接）⇒ 左边是出处（中性，
 * 不是"被删掉的"），右边一格可编辑。
 */
const rows = computed(() => {
  if (isRewrite.value) {
    return diffRows(baseBlocks.value, newBlocks.value)
  }
  if (isNewTopic.value) {
    const added = { type: 'added', left: null, right: null, editable: true }
    if (!hasLeft.value) return [added]
    const ctx = leftBlocks.value.map((b) => ({ type: 'ctx', left: b, right: null, editable: false }))
    return [...ctx, added]
  }
  const rs = diffRows(leftBlocks.value, [...leftBlocks.value, ...addedBlocks.value])
  let placed = false
  return rs
    .map((r) => {
      if (r.type !== 'added') return r
      if (placed) return null
      placed = true
      return { type: 'added', left: null, right: null, editable: true }
    })
    .filter(Boolean)
})

/** 生成中左栏那一整块（原文拼成一块，不再逐块切） */
const leftWholeHtml = computed(() => {
  const blocks = isRewrite.value ? baseBlocks.value : leftBlocks.value
  return blocks.map((b) => b.html).join('')
})

/** 编辑器里那份内容（= 要应用的那几段）。用户改的就是它。 */
const editedMd = ref('')
/** 换答案就 +1 ⇒ 编辑器重建（**不能只改 v-model**：视图不看它） */
const editorKey = ref(0)

/**
 * 要写进编辑器 / 建新知识点的那份内容。
 *
 * **带标记的拿标记里面那份**（标记本身不能进正文）—— 这一份同时是
 * 追加形态的"AI 原版"、新知识点形态的正文、改写形态的新正文。
 */
const sourceMd = computed(() => rewriteMd.value ?? props.answer ?? '')

/**
 * 滚动容器。定位"第一处差异"要用它量位置。
 *
 * <p>⚠️ **声明必须在上面的 immediate watcher 之前**：immediate 的回调是同步跑的，
 * 而 `const` 有 TDZ ⇒ 声明在后面会撞 "Cannot access 'diffEl' before initialization"。
 */
const diffEl = ref(null)

/**
 * 内容换了（追问出了新的一版）⇒ **灌进编辑器 + 重新定位一次**。
 *
 * <p>两处都要动：改 `editedMd`（编辑器重建时拿它当初值）+ 换 `editorKey`（逼编辑器重建）。
 * 只改其中一个，界面上的内容就还是旧的。
 *
 * <p>⚠️ **必须 immediate**：这个 watcher 是"编辑器内容"的唯一来源，
 * 不 immediate 的话，组件一挂载时 `props.answer` 已经非空（页面热更新、
 * 或者父组件先把答案备好了才挂这个框）就**永远灌不进去** ⇒ 右栏是个空编辑器，
 * 而主按钮只看 `editedMd`，于是"有内容却写不进去"。
 *
 * <p>⚠️ **只有这一个 watcher**（原来拆成两个：灌内容一个、重新定位一个）：
 * 两个 watcher 靠注册顺序决定谁先跑 ⇒ 顺序一变就出现"定位量的是上一版"，
 * 而界面上看不出哪儿不对。合并成一个 ⇒ 顺序是确定的。
 *
 * <p>⚠️ **`diffEl` 必须声明在本 watcher 之前**：immediate 的回调是**同步**跑的
 * （普通 watcher 的回调是异步的 ⇒ 不受声明顺序影响）⇒ `diffEl` 声明在后面
 * 就会撞 const 的 TDZ（"Cannot access 'diffEl' before initialization"）。
 */
watch(() => props.answer, async () => {
  editedMd.value = sourceMd.value
  editorKey.value += 1
  if (open.value) await scrollToFirstDiff()
}, { immediate: true })

/** 框里那句话：用户对这一版的具体意见 */
const askText = ref('')

/** 新知识点的标题：用户改过就别再被 AI 的新版本盖回去 */
const topicTitle = ref('')
const titleTouched = ref(false)

/**
 * 建议的标题 = AI 回答里的**第一个标题行**；没有标题行就拿正文第一句。
 *
 * AI 解释一个概念时常常先给个标题（「## 补码(two's complement)」）⇒ 直接拿它。
 *
 * ⚠️ **没有标题行也得给一个**（2026-11-05 实测撞出来的）：
 * 相当一部分回答是纯段落、一个标题都没有 —— 空标题会让「应用为新的知识点」的
 * 主按钮**直接置灰**，而按钮置灰又不给理由 ⇒ 用户只看到"点不动"，不知道差什么。
 * 与其留个死路，不如拿首句当起点（标题框就在内容上方，当场就能改）。
 */
const suggestedTitle = computed(() => {
  const md = String(sourceMd.value || '')
  const h = md.match(/^#{1,6}\s+(.+)$/m)
  if (h) return cleanTitle(h[1])
  const first = md.split('\n').map((s) => s.trim()).find(Boolean) || ''
  return cleanTitle(first)
})

/**
 * 把一行整理成标题：去掉 markdown 标记和前导编号，太长就截到最近的标点。
 *
 * 「1. 补码的定义」这种带编号的标题行，编号要去掉 —— 它是那份回答的章节序号，
 * 不是这条知识点的名字。
 */
function cleanTitle(s) {
  let t = String(s || '')
    .replace(/^[>\-*+\s]+/, '')
    .replace(/^\d+(\.\d+)*[.、)\s]+/, '')
    .replace(/[*_`#]/g, '')
    .trim()
  if (t.length > 20) {
    const cut = t.slice(0, 20)
    const at = Math.max(
      cut.lastIndexOf('，'),
      cut.lastIndexOf('。'),
      cut.lastIndexOf('、'),
      cut.lastIndexOf('：')
    )
    t = (at > 6 ? cut.slice(0, at) : cut) + '…'
  }
  return t
}

// 弹窗一开就把标题填上。**immediate**：组件若在弹窗已打开时挂载（热更新），
// 不 immediate 就永远填不上 ⇒ 主按钮一直置灰。
watch(open, (v) => {
  if (!v) return
  topicTitle.value = suggestedTitle.value
  titleTouched.value = false
}, { immediate: true })
watch(suggestedTitle, (v) => {
  // 用户没动过标题 ⇒ 跟着 AI 的新版本走（重新生成后标题可能变了）
  if (!titleTouched.value) topicTitle.value = v
})

/**
 * **当前这一版的内容**（改写形态 = 改写后的整篇；其余 = 编辑器里那份）。
 *
 * <p>⚠️ **只有这一份**：原来这个三元表达式在「能不能写」和「追问时带给 AI 的当前版」
 * 两处各写一遍 ⇒ 改一处漏一处 ⇒ 追问时 AI 拿到的"当前版"和界面上显示的不是同一份。
 */
const currentMd = computed(() => (isRewrite.value ? (rewriteMd.value || '') : (editedMd.value || '')))

/** 解析不出内容 / 被用户清空 ⇒ 没东西可应用 ⇒ 不给确认入口（写进库比不写更糟） */
const canWrite = computed(() => {
  const text = currentMd.value
  if (!text.trim()) return false
  if (isNewTopic.value && !(topicTitle.value || '').trim()) return false
  return true
})

/**
 * 一个格子的类名。**git 语义**：
 * - `is-del`  左边被删掉的块 → 红
 * - `is-add`  右边新增的块 → 绿
 * - `changed`（同一处改动的两个版本）⇒ 左红右绿
 * - `ctx`（「应用为新的知识点」时的出处原文）⇒ 中性，不是"被删掉的"
 */
function cellClass(row, side) {
  if (row.type === 'equal') return ''
  if (row.type === 'changed') return side === 'left' ? 'is-del' : 'is-add'
  if (row.type === 'removed') return side === 'left' ? 'is-del' : ''
  if (row.type === 'added') return side === 'right' ? 'is-add' : ''
  return ''
}

/**
 * 默认滚到**第一处有差异的行**。
 *
 * 改写是全文对照，一篇长文前面大半是没改的部分 ⇒ 停在顶上等于没看见改动。
 * （git diff 干的事：直接落在第一个 hunk 上。）
 *
 * ⚠️ **必须在弹窗"打开之后"才量**：`el-dialog` 关着的时候内容还在 DOM 里
 * 但不可见 ⇒ 每个格子的 `offsetTop` 都是 0 ⇒ 算出来的 scrollTop 也是 0 ⇒
 * 滚不动（症状：全文 diff 打开后停在第一行，用户得自己往下翻）。
 * ⇒ 触发点是弹窗的 `@opened`（动画结束、内容可见了）。
 */
async function scrollToFirstDiff() {
  const box = diffEl.value
  if (!box) return
  const idx = rows.value.findIndex((r) => r.type !== 'equal')
  if (idx < 0) return
  await nextTick()
  const cell = box.querySelector(`[data-row="${idx}"]`)
  if (!cell) return
  const head = box.querySelector('.kb-diff__head')
  // 减去列头的高度：列头是 sticky 的，不减的话第一处差异会藏在它底下
  box.scrollTop = Math.max(0, cell.offsetTop - (head?.offsetHeight || 0))
}

/**
 * 确认应用。
 *
 * **动作由这一轮交回来的东西决定**（父组件那边只管执行）：
 * - 改写 ⇒ `replace`（整篇替换）
 * - 追加 ⇒ `insert`（追加到选中段之后）
 * - 新知识点 ⇒ `topic`（建一条新知识点）
 */
function confirm() {
  if (!canWrite.value) return
  if (isNewTopic.value) {
    emit('confirm', { action: 'topic', content: editedMd.value, title: topicTitle.value })
    return
  }
  if (isRewrite.value) {
    emit('confirm', { action: 'replace', content: rewriteMd.value })
    return
  }
  // **交出去的是编辑器里那份**（用户可能改过），不是 AI 的原版
  emit('confirm', { action: 'insert', content: editedMd.value })
}

/**
 * 让 AI 按框里那句话重写一版。
 *
 * `current` 是**当前这一版的内容**（用户在编辑器里改过的那份）——
 * 历史里存的是 AI 的原版，不带上它等于让 AI 从头再写一遍，
 * 用户刚才的手工修改就白费了。
 *
 * 改写形态下 `current` 是**改写后的整篇正文**（追问是在这一版上继续改）。
 */
function adjust() {
  const text = (askText.value || '').trim()
  if (!text || props.adjusting) return
  const current = currentMd.value
  emit('adjust', { text, current })
}

function close() {
  open.value = false
}
</script>

<style scoped>
/* ---------- 外层：对比区 + 追问条共用一套列宽 ----------
   **追问条要落在右栏正下方**，所以列宽得由外层这一层定：
   外层是 1fr 1fr 的两列，内层的对比区横跨两列（span 1/-1），
   内层自己再按同样的 gap 切成两列 ⇒ 两层的列线**天然对齐**，
   不用写"右边距 = 50% - gap/2"这种算式（改一个 gap 就错位）。 */
.kb-diff-wrap {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-10);
}
/* 单栏（新建知识点且没有选段 ⇒ 没有"出处"可显示） */
.kb-diff-wrap.is-single {
  grid-template-columns: 1fr;
}

/* ---------- 对比区 ----------
   **一个滚动容器 + grid 两列**：两边天然一起滚（不用两套滚动 + 同步逻辑）。
   每行两个格子 ⇒ 同一处改动并排成一行，行高自动取两边较大值。

   **position: relative 是必须的**：定位"第一处有差异的行"靠子元素的 offsetTop，
   而 offsetTop 是相对**最近的定位祖先**算的 —— 容器不定位的话量到的是外层。 */
.kb-diff {
  position: relative;
  grid-column: 1 / -1;
  max-height: 52vh;
  overflow-y: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: var(--sp-10);
  align-content: start;
}
.kb-diff.is-single {
  grid-template-columns: 1fr;
}
/* 列头：粘在滚动容器顶部 ⇒ 滚起来还看得见哪边是哪边。
   上边距放在这一格自己身上（不放容器上）：放容器上 sticky 会差一截。 */
.kb-diff__head {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: baseline;
  /* ⚠️ §4.11(d)：原来还有 `justify-content: space-between` + `gap(--sp-6)`，
     而这一格**只有一个文本子节点** ⇒ 两条都是死属性（space-between 对单子节点无影响）。
     ⇒ 删掉。
     ⚠️ 原来水平 padding 是 **0**，而下面的 .kb-diff__cell 有 `3px border-left + 6px padding`
     ⇒ **列头文字与格子文字起点错开 9px**。⇒ 补 `padding: var(--sp-4) 6px 6px 9px`。 */
  padding: var(--sp-4) 6px 6px 9px;
  background: var(--cc-surface);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
/* 一个格子 = 一块。min-width:0 是必须的：grid 子项默认 min-width:auto，
   一段不换行的长代码能把整栏撑破。 */
.kb-diff__cell {
  min-width: 0;
  padding: 0 var(--sp-6);
  border-left: 3px solid transparent;
}
/* git 语义：删=red、加=green。**只做底色 + 左边一条**，不动文字颜色 ——
   文字颜色一动，长文读起来就花了。 */
.kb-diff__cell.is-del {
  background: color-mix(in srgb, var(--cc-danger) 9%, transparent);
  border-left-color: var(--cc-danger);
}
.kb-diff__cell.is-add {
  background: color-mix(in srgb, var(--cc-success) 9%, transparent);
  border-left-color: var(--cc-success);
}

/* ---------- 生成中：右栏切成只读的流式预览 ---------- */
.kb-diff__streaming {
  position: relative;
}
/* 顶部一条流动的进度条。**流式期间唯一在动的东西** ——
   内容自己在长已经说明"在生成"，这条只是把"右边不是死的"说满。 */
.kb-diff__progress {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  overflow: hidden;
  background: color-mix(in srgb, var(--cc-primary) 14%, transparent);
}
.kb-diff__progress > i {
  display: block;
  width: 40%;
  height: 100%;
  background: var(--cc-primary);
  animation: kb-diff-slide 1.1s ease-in-out infinite;
}
@keyframes kb-diff-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(250%); }
}

/* ---------- 重新生成失败：错误原文直出 ---------- */
.kb-diff__err {
  grid-column: 1 / -1;
  margin-top: var(--sp-8);
  padding: var(--sp-6) var(--sp-8);
  font-size: var(--text-sm);
  /* §4.3(d)：删掉死 fallback。`var(--cc-danger, #f56c6c)` —— token 恒存在，
     fallback 永不生效，**而且它指向的是 EP 的红**（#f56c6c 系）⇒ 同屏两种红。
     ⇒ 只留 var(--cc-danger)。
     ⚠️ 底色 --el-color-danger-light-9 也换掉（§4.3(d) 的 EP 色板全量替换）：
     这一个元素里原来**混用**两种红（cc-danger 字 + el-light-9 底）⇒ 收成 cc 一套。 */
  color: var(--cc-danger);
  background: var(--cc-danger-soft);
  border-radius: var(--radius-sm);
}

/* ---------- 预览借的壳 ----------
   `.milkdown-editor-body` + `.ProseMirror` 是**编辑器自己的类**：
   套上它们，kb-editor.css 那套排版规则（标题层级、段距、列表、表格、代码块、
   软换行、链接…）直接命中 ⇒ 看到的就是知识点正文渲染后的格式，**零 CSS 重复**。

   下面几条是"编辑器里有、预览里不能有"的，必须盖掉。

   ⚠️ **`:deep()` 是必须的**：v-html 进来的节点没有 scoped 的 data-v 属性，
   而 Milkdown 渲染出来的 .ProseMirror 也没有（它不是本组件模板里的元素）——
   不写 :deep，这几条规则**一条都不生效**（编辑器那套 135px 最小高度、
   80vh 内滚、给折叠箭头留的左右白边会原样漏出来）。 */
.kb-diff :deep(.ProseMirror) {
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
   预览里没有箭头 ⇒ 探出去就探到格子外面了 ⇒ 归零。 */
.kb-diff :deep(.ProseMirror h1),
.kb-diff :deep(.ProseMirror h2),
.kb-diff :deep(.ProseMirror h3),
.kb-diff :deep(.ProseMirror h4),
.kb-diff :deep(.ProseMirror h5),
.kb-diff :deep(.ProseMirror h6) {
  margin-left: 0;
  padding-left: 0;
}
/* 图片：编辑器里图片走的是 **nodeView**（`.kb-img` 那层 span），
   而 DOMSerializer 只还原 toDOM 里的裸 `<img>` ⇒ 收边那条规则（max-width）
   在预览里就丢了，宽图会顶破格子。补上编辑器里图片那条（见 kb-editor.css）。 */
.kb-diff :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-sm);
  vertical-align: bottom;
}
/* 可编辑那一格：编辑器本体。它有自己的滚动区（80vh），在两栏对比里要跟着
   外层那个容器一起滚 ⇒ 上面那条 :deep(.ProseMirror) 已经把它盖掉了。 */
.kb-diff__cell :deep(.milkdown-editor-wrapper) {
  padding: var(--sp-4) 0;
}

/* ---------- 新知识点的标题 ---------- */
.kb-diff__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-bottom: var(--sp-8);
}
.kb-diff__label {
  font-size: var(--text-sm);
  color: var(--cc-text-3);
  white-space: nowrap;
}
.kb-diff__title-input {
  flex: 1;
  height: var(--ctl-sm);
  padding: 0 var(--sp-8);
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xs);
  outline: none;
}
.kb-diff__title-input:focus {
  border-color: var(--cc-primary);
}

/* ---------- 让 AI 再调一版 ----------
   **药丸形**，不是"一行表单"：左边一枚图标（说明这是给 AI 的）、
   中间输入、右边圆形发送钮（与正文选区浮层那枚同一套语言）。 */
.kb-diff__ask {
  grid-column: 2;
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  height: var(--ctl-sm);
  margin-top: var(--sp-8);
  padding: 0 var(--sp-3) 0 var(--sp-8);
  background: var(--cc-surface-2);
  border-radius: var(--radius-pill);
}
.kb-diff-wrap.is-single .kb-diff__ask {
  grid-column: 1 / -1;
}
.kb-diff__ask-ico {
  display: inline-flex;
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.kb-diff__ask.is-busy .kb-diff__ask-ico {
  color: var(--cc-primary);
}
.kb-diff__ask-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  background: transparent;
  border: none;
  outline: none;
}
.kb-diff__ask-input:disabled {
  cursor: default;
}
.kb-diff__ask-state {
  font-size: var(--text-xs);
  color: var(--cc-text-3);
  white-space: nowrap;
}
.kb-diff__ask-send {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  /* §4.11(g)：三处发送按钮有两档（这里 24px / .ask__send 28px / .float-send 28px），
     而 kb-editor.css:563 的注释**明言三者"同一档尺寸"** ⇒ 注释与代码打架。
     ⇒ 统一到 --ctl-sm(28)。 */
  width: var(--ctl-sm);
  height: var(--ctl-sm);
  flex: 0 0 auto;
  border: none;
  border-radius: 50%;
  font-size: var(--text-sm);
  color: var(--cc-surface);
  background: var(--cc-primary);
  cursor: pointer;
  transition: var(--cc-transition);
}
.kb-diff__ask-send:disabled {
  color: var(--cc-text-4);
  /* §4.11(g)：禁用底色统一 --cc-border-strong（另两处用的是 #e2e6ec / --cc-surface） */
  background: var(--cc-border-strong);
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .kb-diff,
  .kb-diff-wrap {
    grid-template-columns: 1fr;
  }
  .kb-diff__ask {
    grid-column: 1 / -1;
  }
}
</style>
