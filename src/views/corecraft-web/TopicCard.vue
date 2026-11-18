<template>
  <!-- ⚠️ **卡片不再能拖**（2026-11-05 用户：「图二是最早做的拖动归类 也直接删掉吧」）：
       原来整块是拖拽源（拖到目录上 = 换个目录归属），那条路连同落点浮层一起撤了。
       ⚠️ **但"换个目录归属"这件事本身还在**（2026-11-06 用户澄清）：
       手动选目录的入口在「⋯」菜单里（ITEMS 的「归类到目录」）——
       删的是**拖动**这条路，不是**归类**这件事。
       现在这张卡：点进去看正文、改标题、手动换目录。 -->
  <article
    class="card"
    :class="{ 'is-selectable': selectable, 'is-selected': selectable && selected }"
    @click="onClick"
  >
    <!-- 标题是可选的：随手记存进来的知识点 title 本来就是空的
         （AppLearnView 的 createTopic 传 title: ''），不该为此编一个灰色「未命名」占位置。
         有标题的地方**这里就是输入框**（TopicTitleInput，和详情页同一个组件），
         空着时显示灰色「请输入标题」，点哪都能直接写、失焦即存。
         editable=false 的卡片（详情页底部的"相关知识点"）退回纯展示 h3。 -->
    <div class="card__head">
      <!-- click.stop：卡片点击是"进详情页"，不拦住的话打完字松手就跳走。
           ⚠️ 原来还有 pointerdown.stop（挡拖拽逻辑接管输入框）—— 卡片不再能拖，那条撤了。 -->
      <TopicTitleInput
        v-if="editable && !selectable"
        :title="topic.title"
        class="card__title"
        @click.stop
        @save="onTitleSave"
      />
      <h3 v-else class="card__title">{{ readTitle }}</h3>
      <!-- ⚠️ **掌握度轻签在右上角**（2026-11-12 方案 C）：档位签和「⋯」菜单**同在右上角**，
           但**不重叠** —— 轻签在菜单**左边**，标题让出轻签那点宽度。
           ⚠️ **没有有效评估时完全不渲染**（组件内部 v-if）⇒ 卡片是干净的空白，
           不显示「未评估」（用户：「默认要不就不展示吧 只有学习过了才展示掌握程度」）。
           ⚠️ **批量模式不画轻签**：那个位置由勾选框接管（见下面 .card__pick），
           两个右上角控件并存会打架。 -->
      <MasteryChip
        v-if="!selectable"
        class="card__master"
        :level="masteryLevel"
      />
      <!-- 下拉壳、竖排 ⋮、hover 前的点击拦截都在 RowMenu 里，和目录行 / 标签行
           同一份。这里只给"它长在卡片右上角"补规则（card__more）。
           ⚠️ **批量模式不画 ⋯**（2026-11-11）：这时候点卡片是"选中"，不是"操作这一条"
           —— 一个 ⋯ 摆在选中态旁边，点进去是弹菜单还是勾选？两件事共用一个卡片不成立。
           它那个位置改由勾选框接管（同一个位置、同一个盒子尺寸，见 .card__pick）。 -->
      <RowMenu
        v-if="deletable && !selectable"
        title="更多操作"
        :items="ITEMS"
        btn-class="card__more"
        @command="onCommand"
        @click.stop
      />
      <!-- 勾选框：**占的就是 ⋯ 那个位置**（右上角、同一个 24px 见的盒子）。
           为什么不摆左上角：左上角是标题的起点，摆上去就得给标题加左内距 ⇒
           一进批量模式整张卡的标题横移一截，看着像"换了张卡"。
           也不带自己的点击处理 —— 点它和点卡片是同一件事（选中/取消），
           冒泡上去由父组件统一判，两处各判一次迟早不一致。 -->
      <span v-if="selectable" class="card__pick" :class="{ 'is-on': selected }" aria-hidden="true">
        <span class="card__pick-box">
          <KbSvgIcon v-if="selected" name="check" :sw="2.6" />
        </span>
      </span>
    </div>

    <!-- 没正文时**不装成正常卡片**：写一句「未写正文」。
         原来 `v-if="excerpt"` 整块不渲染 ⇒ 占位条目只剩个标题，
         跟"加载失败"长得一模一样，用户分不清它到底写没写。 -->
    <p v-if="excerpt" class="card__excerpt">{{ excerpt }}</p>
    <p v-else class="card__excerpt card__excerpt--empty">未写正文</p>

    <!-- ⚠️ **底部那块标签 chip 撤了**（2026-11-03 用户拍板：标签功能从页面上去掉，
         后台逻辑暂时不去）。目录改成**「⋯」菜单里手动选**（见上面 ITEMS 里的
         「归类到目录」）—— 标签再撤掉 ⇒ 卡片底部**什么都不剩**，
         那一行本来就是为标签留的（连着把卡片的 min-height 一起收了，2026-11-06）。 -->
  </article>
</template>

<script setup>
/**
 * 知识点卡片。
 * ⚠️ **它不再是拖拽源**（2026-11-05）：原来整块能拖（拖到目录上 = 换个目录归属），
 * 那套「拖动归类」连同落点浮层一起删了 ⇒ 卡片上只剩"点进去"、"改标题"、"手动换目录"。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { excerptOf, updateTopic } from './useCorecraftWebStore'
import RowMenu from './RowMenu.vue'
import TopicTitleInput from './TopicTitleInput.vue'
import MasteryChip from './MasteryChip.vue'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'

const props = defineProps({
  topic: { type: Object, required: true },
  deletable: { type: Boolean, default: true },
  // 标题能不能就地改。详情页底部的"相关知识点"卡片传 false——
  // 在 A 的详情页里改 B 的标题有点越界。
  editable: { type: Boolean, default: true },
  // **批量模式**（2026-11-11）：整张卡变成"一个可勾选的格子"。
  // 这时候点卡片是选中/取消（由父组件判），不是进详情；⋯ 菜单收起、标题转只读。
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['click', 'delete', 'categorize', 'masteryScope'])

const router = useRouter()

/**
 * 标题保存。列表页没有详情页那个 applyPatch，**直调 store 的 updateTopic** ——
 * 组件自己不再调接口（保存的出口只有这一个）。store 会顺手把列表刷一遍，
 * 卡片拿到的还是新的 title。
 */
async function onTitleSave(title) {
  try {
    await updateTopic(props.topic.id, { title })
  } catch {
    // 失败提示归 axios 拦截器；这里吞掉只是为了不产生 unhandled rejection
  }
}
// 「在新标签页打开」：一次开一个独立页面，于是"同时看好几个知识点"这件事
// 整个交给浏览器——每个 tab 有自己的 JS 堆，来回切**不会重新加载**，
// 各自的光标、滚动位置、未保存的编辑都原样留着，还白送地址栏 / 后退 / Ctrl+1。
// 自己再造一套页内 tab 反而更贵：页内 N 个面板是 N 个 ProseMirror 实例常驻，
// 还得自己做限流和"关掉时正在编辑怎么办"。
function openInNewTab() {
  // full=1：新标签页一进来就是全屏阅读（壳收起来，见 useImmersive.js）。
  const href = router.resolve({
    name: 'AppTopicDetail',
    params: { id: props.topic.id },
    query: { full: '1' }
  }).href
  // noopener：新页面拿不到 window.opener，也就不能反过来改我们这个页面的地址
  window.open(href, '_blank', 'noopener')
}

// 和 CategoryRowMenu 同一个写法：菜单项写死成常量，
// 加一项只动这一个数组，加错 command 名会在 onCommand 里对不上而不是"点了没反应"。
// ⚠️ **「归类到目录」是 2026-11-06 恢复的**（用户：「图二的卡片归类为什么给我删除了
// 我之前让删除的是 拖动卡片归类但是手动选择这个还是要有」）。
// 2026-11-05 删掉的是**拖动归类**（把卡片拖到目录上）—— 手动选目录这条路
// 从来不在那次删除的范围里，删错了 ⇒ 恢复。
// ⚠️ **它自己不做归类**：只发 `categorize` 事件，真正开弹窗的是列表页
// （AppLearnView 那边只有一份弹窗实例，不是每张卡各挂一个）。
//
// ⚠️ **「AI 归类」也只发事件**（2026-11-11 新增）：它复用列表页那份
// AiOrganizeDialog（传这一条进去），不是每张卡各挂一个弹窗。
// **AI 没开时不画这一项** —— 关闭的语义是"这个功能对他不存在"，不是"它坏了"
// （和全站其它 AI 入口一个口径）。
//
// ⚠️ **「掌握度设置」也只发事件**（2026-11-12 新增）：复用列表页那份
// MasteryScopeDialog（单条参与/不参与/跟随类型默认三态）。
// **当前状态走右对齐 meta**（2026-11-13，原来是「掌握度设置（当前：不参与）」——
// 状态塞在括号里又长又丑）：不参与的东西也要能一眼看出"它被关掉了"，
// 不然用户点进去才知道。
const ITEMS = computed(() => {
  const items = [{ command: 'categorize', label: '归类到目录', icon: 'folder' }]
  items.push({
    command: 'masteryScope',
    label: '掌握度设置',
    icon: 'gear',
    meta: props.topic.masteryEnabled ? '参与' : '不参与'
  })
  items.push({ command: 'newTab', label: '在新标签页打开', icon: 'external' })
  items.push({ command: 'delete', label: '删除', icon: 'trash', tone: 'danger', divided: true })
  return items
})

function onCommand(cmd) {
  if (cmd === 'categorize') {
    emit('categorize', props.topic)
  } else if (cmd === 'masteryScope') {
    emit('masteryScope', props.topic)
  } else if (cmd === 'newTab') {
    openInNewTab()
  } else if (cmd === 'delete') {
    emit('delete', props.topic)
  }
}

function onClick() {
  emit('click')
}

// 内容摘要：清洗规则收敛到 store 的 excerptOf（删除确认框也要用同一份）
const excerpt = computed(() => excerptOf(props.topic.content))

/**
 * 掌握度档位（2026-11-12）。
 *
 * <p>⚠️ **判据全在后端**：`masteryEnabled` 是后端解析好的最终值
 * （单条覆盖 &gt; 类型默认值 &gt; 安全兜底），`masteryLevel` 是后端算好的档位词。
 * 前端**只做一件事**：没有有效评估（level 为空）就什么都不画。
 *
 * <p>⚠️ **不参与掌握度的内容（笔记/备忘）连位置都不占** —— 它连档位都不会有。
 */
const masteryLevel = computed(() => {
  if (!props.topic.masteryEnabled) return ''
  return props.topic.masteryLevel || ''
})

/**
 * 只读态（批量模式 / 详情页的"相关知识点"）标题。
 *
 * 随手记存进来的那批 `title` **本来就是空的**（见 AppLearnView 的 createTopic）——
 * 可编辑时靠输入框的 placeholder 顶着，转成 h3 就是**一片空白**，
 * 卡片没法认，更别说"勾哪几条"了。
 * ⇒ 没标题就退回正文开头（和 CategorizeDialog 里「哪一条」同一份规则），
 * **不编「未命名」**：那是个假占位，用户会以为这条真叫这个名字。
 */
const readTitle = computed(() => {
  if (props.editable) return props.topic.title
  return props.topic.title || excerptOf(props.topic.content, 24)
})
</script>

<style scoped>
/* ⚠️ **原来这里有 `min-height: 142px`**（2026-11-06 删）。
   它是给**底部那行标签 chip**留的位置：标签 2026-11-03 撤了，那一行没了，
   142px 却留着 ⇒ 每张卡底部空出 ~40px（用户截图图四圈起来的那块）。
   而卡里的内容高度其实是**固定的**：标题一行（输入框）+ 摘要两行（`-webkit-line-clamp: 2`）
   ⇒ 去掉这行，下面的内容自己就撑满，行内多张卡由 grid 的 stretch 拉齐。 */
.card {
  position: relative;
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: var(--sp-12) var(--sp-13) var(--sp-11);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  display: flex;
  flex-direction: column;
}
.card:hover {
  border-color: var(--cc-primary-border);
  /* §4.3 的阴影两档：0 3px 14px rgb(31 35 41 / 5%) → --cc-shadow-1 */
  box-shadow: var(--cc-shadow-1);
}
/* ⚠️ **这里原来有 `.card.is-cursor`**（命中游标指着的那张：蓝边 + 淡蓝底）——
   2026-11-05 用户：「对于卡片栏来说 这个搜索直接做过滤就行了 不用那个命中多少个的框
   也不用把命中的标蓝」⇒ 卡片栏是**纯过滤**：命中什么只剩什么 ⇒ 满屏都是命中的卡片，
   再标一遍等于没标 ⇒ 这条规则连同 prop 一起删。 */

/* ===== 批量模式（2026-11-11）=====
   选中态沿用全站那一套「主色软底 + 主色描边」（侧栏 `.app-cats__item.is-on`、
   筛选弹层 `.tf__row.is-on` 都是这个语义），只是这里描的是卡片自己的 border
   —— 卡片本来就有 border，再加一条内描边环会和它几乎重合、看着像描了两次。
   ⚠️ 选中态必须压过 :hover（`border-color: --cc-primary-border` 比 --cc-primary 浅）：
   鼠标停在已选卡片上时它不该"变淡"，所以这条写在 :hover 之后。 */
.card.is-selected,
.card.is-selected:hover {
  background: var(--cc-primary-soft);
  border-color: var(--cc-primary);
}
/* 批量模式下整张卡是勾选区：**不显示文本光标**、也不能选词
   （拖一下就选中一片字，和"点一下选中这张卡"打架）。 */
.card.is-selectable {
  cursor: pointer;
  -webkit-user-select: none;
  user-select: none;
}
/* 勾选框：钉在 ⋯ 那个位置（右上角、同一档尺寸），所以进批量模式**什么都不跳**。
   外壳 24×24 只是点击手感，里面那个 16×16 的方块才是图形本身 ——
   和 `.tf__check`（筛选弹层的勾位）、侧栏选中柱是同一套"选中"语言。 */
.card__pick {
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  pointer-events: none;   /* 点击交给卡片本身（同一个动作，别判两次） */
}
.card__pick-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--cc-border-strong);
  border-radius: 4px;
  background: var(--cc-surface);
  color: var(--cc-surface);
  transition: background 0.15s, border-color 0.15s;
}
/* 悬停在卡上 ⇒ 勾选框边框透出主色（"这一格可以点"的暗示），仍不填色。 */
.card.is-selectable:hover .card__pick-box {
  border-color: var(--cc-primary);
}
.card__pick.is-on .card__pick-box,
.card.is-selectable:hover .card__pick.is-on .card__pick-box {
  background: var(--cc-primary);
  border-color: var(--cc-primary);
  color: var(--cc-surface);
}
/* 勾的笔画用 18px 那一档（比文字小、比图标大），16px 盒子里刚好不顶边。 */
.card__pick-box :deep(svg) {
  width: 12px;
  height: 12px;
}
/* ⚠️ **批量模式下标题只占一行**（2026-11-11）。
   可编辑时它是 `<input>`（天生单行），转只读变 `<h3>` 就吃到 `-webkit-line-clamp: 2`
   ⇒ 长标题的卡会长高一截 ⇒ 一进批量模式整个网格的行高都变、用户视线跟着跳。
   截成一行 + 省略号，和输入框那行**同高**（都是 line-height 1.5）。 */
.card.is-selectable .card__title:not(input) {
  -webkit-line-clamp: 1;
  line-clamp: 1;
  text-overflow: ellipsis;
}
/* .card__title 撑满整行，菜单要钉在卡片右上角而不是"标题右侧、跟着标题行高浮动"。
   .card__head 本身定位，按钮那层 .el-dropdown 相对它绝对定位——
   挂在 button 上无效（el-dropdown 是 EP 生成的 static div，见下面无标题那段）。
   同一个选择器不拆成两块：position 和 flex 摆在一处，少一次"这段在管什么"的猜。 */
.card__head {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-10);
  position: relative;
}
.card__head > .el-dropdown {
  position: absolute;
  top: 0;
  right: 0;
}
/* ⚠️ **掌握度轻签**（2026-11-12）：在右上角，**在「⋯」菜单左边**。
   做法：轻签**走正常流**（跟在标题后面），标题是 flex:1 会自动给它让位 ——
   比"标题多让固定 62px"稳：档位词有长有短（待巩固 3 字 / 掌握 2 字），
   写死一个数就会一边挤标题、一边留空。
   ⚠️ **右外边距 = 「⋯」那一档的宽度**：菜单是绝对定位钉在 right:0 的，
   轻签要让开它，否则两者叠在一起。 */
.card__master {
  flex: none;
  margin-right: calc(var(--ctl-sm) + var(--sp-4));
  align-self: flex-start;
}
/* 标题：可编辑时是 input、只读时是 h3，两者共用同一套字号字重，看起来才是一个东西。
   min-height 是给 h3 兜底的——input 本身恒为一行高，空标题那边自然就对齐了。 */
.card__title {
  padding-right: var(--sp-14);
  flex: 1;
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 600;
  line-height: 1.5;
  color: var(--cc-text-1);
  /* 素雅=衬线卡题（--font-display 随主题；本色=正文字体零变化） */
  font-family: var(--font-display);
  min-height: 1.5em;
}
/* 两行截断只对 h3 有效。input 天生单行（超出省略号由 TopicTitleInput 负责），
   给它 display: -webkit-box 会把输入框渲染坏，所以按元素类型分开。 */
.card__title:not(input) {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

/* 「⋯」按钮的显隐。形状 / 旋转 / opacity / transition 都在 RowMenu 里，
   这里**只改 `--row-tools` 那个变量**：改变量而不是再声明一条 opacity，
   两边就不抢同一条规则（抢了就是"谁的规则在后面谁赢"，改样式时静默失效）。
   默认常显（变量没设时 RowMenu 那边 fallback 到 1）：平板 / 手机没有 hover 指针，
   "hover 才显" 等于永远不显——而这个菜单里装的是**触屏上唯一能动这张卡的操作**。
   @media (hover: hover) 是浏览器实测出来的输入方式特征，不是拿屏幕宽度猜——
   笔记本带触屏时它仍为 true，窄窗口不会被误判成平板。
   ⚠️ 但它在国内 Android 浏览器上会撒谎（OPPO 自带 / 夸克在真机触屏上照样报 true），
   所以"手机端视图"不能建立在它上面——见 corecraft-web-context SKILL §5 多端策略。

   规则挂在 **.card** 上而不是按钮上：自定义属性沿 DOM 继承，能穿过子组件的
   scoped 边界；写成 `.card__more` 的话那个按钮在 RowMenu 内部，拿不到本组件的
   data-v，整条规则静默不生效（build 和编译都不会报）。 */
@media (hover: hover) {
  /* 只有真的能 hover 的设备才藏 */
  .card {
    --row-tools: 0;
  }
  .card:hover {
    --row-tools: 1;
  }
}
/* 点击区：壳里是 padding 撑出来的小方块（约 20px），这里撑到 28px 那一档。
   这条**必须**用 :deep()——按钮在 RowMenu 内部，TopicCard 的 scope 到不了它
   （scoped 只把 data-v 打在子组件的根元素上，根是 .el-dropdown 不是 button）。 */
.card__head :deep(.card__more) {
  min-width: var(--ctl-sm);
  min-height: var(--ctl-sm);
  justify-content: center;
}
/* 无标题的卡片不需要额外处理：上面 .card__head > .el-dropdown 已经把菜单钉在右上角了。
   （之前的 .card.is-untitled .card__more { position:absolute } 定位错了层——挂在 button 上，
   而 el-dropdown 那层 static div 仍留在 flex flow 里占位，把按钮从"卡片右上角"推到了
   "标题行右端悬在半空"，就是这个问题。） */

.card__excerpt {
  flex: 1;
  margin: var(--sp-6) 0 0;
  font-size: var(--text-xs);
  line-height: 1.65;
  color: var(--cc-icon);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

/* 没写正文的占位条目：同一行位置，弱一档的颜色 ——
   它不是"坏了"，是"还没写"（所以不用错误色）。 */
.card__excerpt--empty {
  color: var(--cc-text-3);
}
</style>
