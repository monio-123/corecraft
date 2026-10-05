<template>
  <!-- 整块都是拖拽源：已归类的也能拖，改目录和归类是同一个动作。
       拖拽走自建的 Pointer Events（useTopicDrag），三端同一套逻辑。 -->
  <article
    class="card"
    :class="{ 'is-dragging': dnd.live && dnd.topicId === topic.id }"
    @pointerdown="onPointerDown"
    @click="onClick"
  >
    <!-- 标题是可选的：随手记存进来的知识点 title 本来就是空的
         （AppLearnView 的 createTopic 传 title: ''），不该为此编一个灰色「未命名」占位置。
         有标题的地方**这里就是输入框**（TopicTitleInput，和详情页同一个组件），
         空着时显示灰色「请输入标题」，点哪都能直接写、失焦即存。
         editable=false 的卡片（详情页底部的"相关知识点"）退回纯展示 h3。 -->
    <div class="card__head">
      <!-- pointerdown.stop：卡片整块是拖拽源（useTopicDrag），不拦住的话点输入框会被拖拽逻辑接管。
           click.stop：卡片点击是"进详情页"，不拦住的话打完字松手就跳走。 -->
      <TopicTitleInput
        v-if="editable"
        :topic-id="topic.id"
        :title="topic.title"
        class="card__title"
        @pointerdown.stop
        @click.stop
      />
      <h3 v-else class="card__title">{{ topic.title }}</h3>
      <el-dropdown v-if="deletable" trigger="click" @command="onCommand" @click.stop>
        <button
          ref="moreBtn"
          type="button"
          class="kb-icon-btn card__more"
          aria-label="更多操作"
          @click.prevent.stop
        >
          <el-icon class="card__more-icon"><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="categorize">归类到目录</el-dropdown-item>
            <el-dropdown-item command="newTab">在新标签页打开</el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <p v-if="excerpt" class="card__excerpt">{{ excerpt }}</p>

    <!-- 底部只剩标签，没有目录。目录不进卡片：目录一多，平铺的下拉列表就没法用了，
         归类和改类统一走"把卡片拖到右侧那棵树上"这一个动作。
         标签样式是全局 .kb-chip，和知识点详情页的标签是同一份规则。 -->
    <div v-if="tags.length" class="card__tags">
      <span v-for="t in tags" :key="t" class="kb-chip">{{ t }}</span>
    </div>
  </article>
</template>

<script setup>
/**
 * 知识点卡片。整块都是拖拽源——已归类的也能拖，改目录和归类是同一个动作。
 * 以前只有未归类的可拖、已归类的靠卡片上的目录 chip 改，chip 撤掉之后就没有第二条路了。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MoreFilled } from '@element-plus/icons-vue'
import { dnd, dndPick, excerptOf } from './useCorecraftWebStore'
import { useTopicDrag } from './useTopicDrag'
import TopicTitleInput from './TopicTitleInput.vue'

const props = defineProps({
  topic: { type: Object, required: true },
  deletable: { type: Boolean, default: true },
  // 标题能不能就地改。详情页底部的"相关知识点"卡片传 false——
  // 在 A 的详情页里改 B 的标题有点越界。
  editable: { type: Boolean, default: true }
})

const emit = defineEmits(['click', 'delete'])

const router = useRouter()
const { onPointerDown } = useTopicDrag(() => props.topic)

// 「在新标签页打开」：一次开一个独立页面，于是"同时看好几个知识点"这件事
// 整个交给浏览器——每个 tab 有自己的 JS 堆，来回切**不会重新加载**，
// 各自的光标、滚动位置、未保存的编辑都原样留着，还白送地址栏 / 后退 / Ctrl+1。
// 自己再造一套页内 tab 反而更贵：页内 N 个面板是 N 个 ProseMirror 实例常驻，
// 还得自己做限流和"关掉时正在编辑怎么办"。
function openInNewTab() {
  // full=1：新标签页一进来就是全屏阅读（壳收起来，见 useImmersive.js）。
  // 一次开一个独立页面，于是"同时看好几个知识点"这件事整个交给浏览器——
  // 每个 tab 有自己的 JS 堆，来回切**不会重新加载**，
  // 各自的光标、滚动位置、未保存的编辑都原样留着，还白送地址栏 / 后退 / Ctrl+1。
  // 自己再造一套页内 tab 反而更贵：页内 N 个面板是 N 个 ProseMirror 实例常驻，
  // 还得自己做限流和"关掉时正在编辑怎么办"。
  const href = router.resolve({
    name: 'AppTopicDetail',
    params: { id: props.topic.id },
    query: { full: '1' }
  }).href
  // noopener：新页面拿不到 window.opener，也就不能反过来改我们这个页面的地址
  window.open(href, '_blank', 'noopener')
}

// 「归类到目录」按下去时记一下按钮自己的位置，浮层从这儿弹出来。
// 用 ref 而不是事件对象：el-dropdown 的 command 回调拿不到触发元素。
const moreBtn = ref(null)

function onCommand(cmd) {
  if (cmd === 'categorize') {
    // 触屏归类入口：打开**同一个**落点浮层（PC 拖拽用的那棵），
    // 只是改成点树上一行即归类，不用把卡片拖进去。
    dndPick(props.topic.id, moreBtn.value?.$el || moreBtn.value)
  } else if (cmd === 'newTab') {
    openInNewTab()
  } else if (cmd === 'delete') {
    emit('delete', props.topic)
  }
}

function onClick() {
  emit('click')
}

const tags = computed(() => (props.topic.tags || []).map((t) => t.name || t))

// 内容摘要：清洗规则收敛到 store 的 excerptOf（删除确认框也要用同一份）
const excerpt = computed(() => excerptOf(props.topic.content))
</script>

<style scoped>
.card {
  position: relative;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 14px 15px 12px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  display: flex;
  flex-direction: column;
  min-height: 118px;
  /* 卡片是拖拽源不是阅读区，禁掉文本选择。
     长按菜单是这个交互最大的敌人：Android Chrome 靠 user-select 弹选择手柄，
     iOS 靠 -webkit-touch-callout 弹系统菜单，两条任缺一条都会先被系统接管。
     touch-action 不在这里设——拖拽逻辑（useTopicDrag）按需接管滚动，
     静态设死会让卡片区域完全不能滚。 */
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}
.card:hover {
  border-color: #cfe0f7;
  box-shadow: 0 3px 14px rgba(31, 35, 41, 0.05);
}
/* 正在被拖的源卡片：压暗，让"已经拿起来了"和"跟着走的影子"分得开 */
.card.is-dragging {
  opacity: 0.4;
}
.card__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
/* .card__title 撑满整行，菜单要钉在卡片右上角而不是"标题右侧、跟着标题行高浮动"。
   .card__head 本身定位，按钮那层 .el-dropdown 相对它绝对定位——
   挂在 button 上无效（el-dropdown 是 EP 生成的 static div，见下面无标题那段）。 */
.card__head {
  position: relative;
}
.card__head > .el-dropdown {
  position: absolute;
  top: 0;
  right: 0;
}
/* 标题：可编辑时是 input、只读时是 h3，两者共用同一套字号字重，看起来才是一个东西。
   min-height 是给 h3 兜底的——input 本身恒为一行高，空标题那边自然就对齐了。 */
.card__title {
  padding-right: 26px;
  flex: 1;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2329;
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

/* 「⋯」菜单按钮。形状 / 配色在全局 .kb-icon-btn，这里只管显隐和竖排点。
   默认：常显。平板 / 手机没有 hover 指针，"hover 才显" 等于永远不显——
   而这个菜单里装的是**触屏上唯一能归类的入口**，藏起来等于触屏功能不存在。
   @media (hover: hover) 是浏览器实测出来的输入方式特征，不是拿屏幕宽度猜——
   笔记本带触屏时它仍为 true，窄窗口不会被误判成平板。
   ⚠️ 但它在国内 Android 浏览器上会撒谎（OPPO 自带 / 夸克在真机触屏上照样报 true），
   所以"手机端视图"不能建立在它上面——见 corecraft-web-context SKILL §5 多端策略。 */
.card__more {
  opacity: 1;
  transition: opacity 0.15s;
}
@media (hover: hover) {
  /* 只有真的能 hover 的设备才藏 */
  .card__more {
    opacity: 0;
  }
  .card:hover .card__more {
    opacity: 1;
  }
}
/* 竖排 ⋮：EP 自带的 MoreFilled 是横排 ⋯，目录行那边（CategoryRowMenu）也是转 90° 用的，
   两处保持一致——同一个"这一行的操作"图标该长得一样。 */
.card__more-icon {
  transform: rotate(90deg);
}
/* 无标题的卡片不需要额外处理：上面 .card__head > .el-dropdown 已经把菜单钉在右上角了。
   （之前的 .card.is-untitled .card__more { position:absolute } 定位错了层——挂在 button 上，
   而 el-dropdown 那层 static div 仍留在 flex flow 里占位，把按钮从"卡片右上角"推到了
   "标题行右端悬在半空"，就是这个问题。） */

.card__excerpt {
  flex: 1;
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: #8a9099;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.card__tags {
  display: flex;
  gap: 4px;
  margin-top: 10px;
  min-width: 0;
  overflow: hidden;
}
</style>
