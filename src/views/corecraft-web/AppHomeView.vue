<template>
  <div class="home">
    <!-- 快捷录入：最高频动作，放最显眼。
         原先是 <el-input type="textarea">，纯文本框——从语雀这类富文本源框选复制，
         浏览器往 textarea 里只放 text/plain，标题/列表/代码块在【存库之前】就全塌成了
         纯文字。这和 #51 是同一个 bug：那次只修了随手记页，首页这处漏了，
         直到两边被放在一起看才发现它们是两份不一样还都不全的实现。
         现在录入统一收在这一页（随手记页去掉输入框，改成纯浏览 + 归类）。
         :key 不是可选的：defaultValueCtx 只在编辑器创建那一刻读一次，事后把 draft 置空
         不会回写 doc，不重建的话保存之后框里还留着原文案，像保存失败。 -->
    <section class="quick" :class="{ 'is-big': immersive.on }">
      <MilkdownEditor :key="editorKey" ref="editorRef" v-model="draft" :show-toolbar="toolbar" @submit="save">
        <!-- **顶栏右端集群**（2026-11-16 用户：「是顶栏的右上角啊」）：
             文件动作在左、类型在最右，住在**编辑器顶栏（色带）的右端**。
             ⚠️ 实情仍在编辑器里：点「导入」调 editorRef.pickImportFile()
             （它弹系统文件选择器、读文件、插到光标处）。 -->
        <template #toolbar-end>
          <button class="filebtn" type="button" @click="pickImport">
            <KbSvgIcon name="upload" />
            导入
          </button>
          <TopicTypeSelect v-model="draftType" />
        </template>
      </MilkdownEditor>
      <div class="quick__actions">
        <!-- 左边是"编辑器/页面"的开关（格式工具栏显不显、要不要进全屏），
             右边是"记下来"这个主操作。space-between 要的是两个子元素，
             所以左边得有个容器把它们圈住。
             ⚠️ **类型选择器搬走了**（到顶栏右端集群，见上），这一行只剩开关。 -->
        <div class="quick__left">
          <ViewToggles
            v-model:show-toolbar="toolbar"
            :full="immersive.on"
            placement="top"
            full-label="全屏录入"
            @toggle-full="immersive.toggle"
          />
        </div>
        <el-button class="save-btn" type="primary" size="small" :loading="saving" :disabled="!draft.trim()" @click="save">
          记下来
        </el-button>
      </div>
    </section>

    <!-- 概览。**三张卡全是入口**（RouterLink，不是 div onclick）：
         语义上就是链接，右键复制链接 / 中键新标签页 / 读屏器念得出来，全是白送的。

         ⚠️ **原来有四张**（知识点 / 目录 / 待归类 / 今日新增），「目录」那张 2026-11-03 删掉
         （用户原话：「首页的那个目录栏不要了 只保留知识点 待归类 今日新增」）。
         目录不是一个该从首页进的去处 —— 侧边栏那棵树就在那儿。

         ⚠️ **三张卡的落地口径各不相同**（"同一屏三件事"的关键）：
         | 卡 | 落到哪 | 时间筛选 | 目录范围 |
         |---|---|---|---|
         | 知识点 | `/app/learn` | **不限** | 全部 |
         | 今日新增 | `/app/learn?range=today` | **今天** | 全部 |
         | 待归类 | `/app/learn?category=UNCATEGORIZED` | **不限** | 只看待归类那批 |
         「待归类」是**首页唯一的入口**（列表页那个药丸已经删掉，见 AppLearnView）。 -->
    <section class="stats">
      <RouterLink
        v-for="c in statCards"
        :key="c.label"
        class="stat stat--link"
        :to="c.to"
      >
        <div class="stat__num" :class="{ 'is-busy': c.busy }">{{ c.num }}</div>
        <div class="stat__label">{{ c.label }}</div>
      </RouterLink>
    </section>

    <!-- 最后一行：**复习** + **考试记录** 两块**并排**（#895，2026-11-12）。
         ⚠️ **它们曾经各占一整行** ——「考试记录」是 2026-11-11 后加的，加进来就多出一整行
         ⇒ 首页总高被撑破 ⇒ 录入框（`.quick` 是唯一 flex-grow 的块）被压掉一排高度，
         用户报「首页的布局被上次的任务动了（加了一个考试导致输入框变小了）」。

         ⇒ 口径：**录入框的高度 = 加「考试记录」之前的高度**（也就是"现在的长度 + 去掉考试栏目
         那一行的长度"），而**不靠手写一个 px 值** —— `.quick` 是 `flex: 999 1 0`、
         其余块 `flex: 0 0 auto`（不参与高度分配）⇒ 下面少一行，录入框就自动多长那一份。
         实测（CDP 量 rect，1440×900）：改前 quick 374.7 / 面板 116.2+116.2 两行
         ⇒ 改后 quick 510.9（= 374.7 + 116.2 + 20 gap）、两块并排各占半宽。

         **为什么并排而不是把「考试记录」删掉/挪走**：两块都是"入口面板"、
         内容都只有一行链接，视觉重量相当 ⇒ 分左右两半正好是用户拍板的形状，
         也让「首页刚好铺满一屏」这个前提（AppLayout 那条 `.app-content:has(.home)`
         注释里写着）继续成立。 -->
    <section class="home__row">
      <!-- 今日待复习：**只留入口**（2026-10-22 撤旧自测题时留下的）。
           复习内容、抽题规则、每日复习的单题流都随旧那套一起撤了（题和记录都没了），
           这个入口现在指向**知识点列表** —— 新的考试在那儿选范围（`/exam?ids=…`）。
           等新的复习能力做出来，再把它改成直达。
           ⚠️ §4.7(d)：标题原来叫「今日待复习」，但这一格里**没有任何数量/日期**
           （`.panel__extra` 是空的，面板里只有一条「开始复习」链接）⇒ "今日"这个词
           让用户每次都以为会有一个数拿不出来。
           ⇒ 标题压成「复习」（不承诺一个拿不出的数）。等复习能力做出来再把数补进
           `.panel__extra`（kb.css 已给它留了位）。 -->
      <section class="panel">
        <div class="panel__head">
          <span class="panel__title">复习</span>
        </div>
        <RouterLink class="panel-link" :to="{ path: '/app/learn' }">
          <KbSvgIcon class="panel-link__icon" name="clock" />
          <span>开始复习</span>
        </RouterLink>
      </section>

      <!-- 考试记录：历史卷子的出口（设计 §8.3）——分数、题数、时间在这一页翻，
           回看/重做进的是考试页（新标签）。掌握度的证据就是从这些考试里来的，
           这个入口让用户能顺着"我考过什么"看到掌握度的来路。 -->
      <section class="panel">
        <div class="panel__head">
          <span class="panel__title">考试记录</span>
        </div>
        <RouterLink class="panel-link" :to="{ path: '/app/exams' }">
          <KbSvgIcon class="panel-link__icon" name="note" />
          <span>查看考试记录</span>
        </RouterLink>
      </section>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, defineAsyncComponent, h } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import ViewToggles from '../../components/corecraft-web/ViewToggles.vue'
import { loadShellData } from './useShellData'
import { useImmersive } from './useImmersive'
import { keywordFromRoute } from '../../utils/routeQuery'
import { rangeBounds, withinRange } from '../../utils/timeRange'
import TopicTypeSelect from './TopicTypeSelect.vue'
import {
  getTopicIndex,
  getUncategorizedCount, createTopic,
  UNCATEGORIZED
} from './useCorecraftWebStore'

// 首页这块录入框是**首屏唯一用到编辑器**的地方，而编辑器连着 Milkdown /
// ProseMirror / remark 一整套 505KB（gzip 157KB）。原来是静态 import ⇒ 它挂在
// AppHomeView 的依赖链上，首页第一帧必须等它下完、解析完才画得出来。
// 改成异步组件：先画一个同高的空占位，chunk 单独下、单独解析，好了再换上
// ⇒ 侧边栏、统计卡、录入框外框都不再被它挡住。
// ⚠️ 占位复用 `.milkdown-editor-wrapper` 这个全局类（kb-editor.css 那条 flex 链），
// 不另写一份高度规则 —— 编辑器换上时录入框高度一像素都不跳。
const MilkdownEditor = defineAsyncComponent({
  loader: () => import('../../components/MilkdownEditor.vue'),
  loadingComponent: { render: () => h('div', { class: 'milkdown-editor-wrapper' }) },
  delay: 0
})

const route = useRoute()

// 全屏录入：整页只剩这一块输入区（见下面的 .is-immersive 规则）。
// 和详情页、AppLayout 共用同一个判据 useImmersive()，状态在 URL 上。
const immersive = useImmersive()

// 工具栏显不显示，状态在本层——「简洁/完整」开关按钮就在下面那个动作行里，
// 按钮和状态得是同一份。传下去用 :show-toolbar（**单向**：编辑器自己不持有这个状态，
// 开关按钮也不在编辑器里）。
const toolbar = ref(false)
// 录入时选的类型。存 null = 用默认档（字典里排第一的那条）
const draftType = ref(null)

const draft = ref('')
// 强制重建编辑器来清空输入框：defaultValueCtx 只在创建那一刻读一次 defaultValue，
// 事后把 draft 置空不会回写 doc。重建顺带把 undo 历史也清了，本来也该清。
const editorKey = ref(0)
const saving = ref(false)

/**
 * 录入框的编辑器实例（右上角「导入」按钮要调它）。
 * ⚠️ 异步组件 + `:key="editorKey"`（换知识点时重建）⇒ ref 指向的是那个异步组件的
 * 包装实例，方法靠 `defineExpose` 转发（MilkdownEditor.vue 里转给 Body）。
 */
const editorRef = ref(null)
/** 「导入」：弹系统文件选择器（选 .md）——实情在编辑器里（读文件、解析、插到光标处） */
function pickImport() {
  editorRef.value?.pickImportFile()
}

// 结构索引（全集，不含正文）—— 首页只要**条数**和"今天新增了几条"，
// 一条正文都不需要（正文那 90 万字符是列表接口慢的真正原因）。
const topics = computed(() => getTopicIndex())
const topicCount = computed(() => topics.value.length)
const uncategorizedCount = computed(() => getUncategorizedCount())

// 今日新增。**口径与列表页的「今天」完全同一份**（utils/timeRange.js）：
// 以前这里自己拼日期键（`new Date().toDateString()` 切前 10 位 ⇒ "Tue Oct 06 "，
// 跟库里 "2026-10-06" 永远不相等 ⇒ 恒为 0），现在是**同一对起止**，
// 首页这张卡上的数 = 列表页「今天」那一屏的条数。
//
// ⚠️ **日期键本地拼**而不是 toISOString()：后者是 UTC，东八区凌晨 0~8 点会把当天
// 算成前一天，"今日新增"会在刚过完零点时莫名其妙归零。
//
// ⚠️ **过了零点不会自己变**：结构索引是模块级缓存（不跨天刷新）⇒ 这个数跟着一起旧，
// F5 才更新 —— 与全站其它数据的口径一致，不在这里另开一条刷新路径。
const todayCount = computed(() => {
  const bounds = rangeBounds('today')
  return topics.value.filter((t) => withinRange(t.createdAt, bounds)).length
})

/**
 * 概览那三张卡（**数据驱动**，模板一块 `v-for` 画完）。
 *
 * <p>⚠️ **原来三张卡是三段一模一样的 DOM**（`RouterLink.stat.stat--link` →
 * `div.stat__num` → `div.stat__label`），只有落地地址 / 数字 / 文案不同
 * ⇒ 加第四张卡要复制一整段，改样式要改三处。
 *
 * <p>⇒ 三张卡的口径（落地地址、时间筛选、目录范围）就是上面注释里那张表 ——
 * 现在那张表**直接变成这份数组**，注释与代码不会再各说各话。
 */
const statCards = computed(() => [
  { label: '知识点', num: topicCount.value, to: { path: '/app/learn' }, busy: false },
  {
    label: '待归类',
    num: uncategorizedCount.value,
    to: { path: '/app/learn', query: { category: UNCATEGORIZED } },
    busy: uncategorizedCount.value > 0
  },
  {
    label: '今日新增',
    num: todayCount.value,
    to: { path: '/app/learn', query: { range: 'today' } },
    busy: false
  }
])



async function save() {
  const content = draft.value.trim()
  if (!content || saving.value) return
  saving.value = true
  try {
    await createTopic({ title: '', content, tags: [], typeId: draftType.value })
    draft.value = ''
    editorKey.value++
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // 共享 inflight：AppLayout 已经拉过会话时，这里不会再打一次接口。
  // 三个 ensure 收敛在 loadShellData 一处（原来这里和 AppLayout 各写了一遍）。
  // 这里不加 try/catch：壳先挂载，它成功了这一步必然命中缓存、不会失败；
  // 失败的状态由壳的侧边栏那格「加载失败 · 重试」统一承担（见 AppLayout.loadShell）。
  // 只等它把数据拉齐，不取返回值——问候语删了之后这一页已经没有 profile 要用。
  await loadShellData(keywordFromRoute(route))
})
</script>

<style scoped>
/* ---------- 三排 + 一条弹性尾巴 ----------
   录入框吃掉可分配的高度，概览和待复习两块保持自己的高度、落在**和左右一样的留白**处。
   高度链：.app-shell(100%) → .app-main(flex:1) → .app-content(flex:1, overflow-y:auto)
   → .home(min-height:100%)。**必须用 min-height 而不是 height**：用 height 会把这一页
   锁死在容器高度上，编辑器内容一长就被压扁而不是把容器撑出滚动条。
   「100%」能生效是因为 .app-content 是 flex 项、已经有解析后的确定高度（见它的 flex:1），
   不是因为谁显式写了 height——链路上每一环都得是 flex 项，缺一环百分比就退化成 auto。

   ⚠️ **这里就是 100%，不要再加任何补偿**（#124 / #125 连续返工两次就在这）：
   底部那截留白由 AppLayout 的 `.app-content:has(.home)` 直接把 padding-bottom
   收成 --sp-15 来解决，**拥有 padding 的元素自己改**。
   子页面在这里"补"是补不掉的：`100%` 量的是内容盒，往上补的元素会扎进 padding 区，
   而 padding 不吸收溢出 → 容器照样算出超出部分 → **凭空多一条滚动条**。
   补 padding 只能在拥有它的那个元素上做，别在子页面里算差值。 */
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
  /* 壳已收滚（AppLayout 的 :has(.home)）⇒ 这里的高度被钉死为一屏：
     flex:1 吃满内容区，矮窗出现赤字时**下方的栏先让位**（它们 shrink:1），
     录入区有 .quick 上那条 220px 的底。内容再多也是编辑器内滚，页面不滚。
     ⚠️ **width:100% 不能省**（2026-11-15 用户截图「何意味」）：壳收滚后
     .app-content 是 flex 列，`margin: 0 auto` 的 auto 边距会**优先于 stretch**
     把子项按内容收缩 ⇒ 整个工作面缩成 fit-content 的一条窄卡居中
     （与 .detail #839 那个窄卡同一个坑，FEATURES §2.7 的注释早就写着）；
     width:100% 给出确定宽度，auto 边距才回到"只管居中"的本职。 */
  width: 100%;
  flex: 1;
  min-height: 0;
  /* 整页一条工作面（2026-11-09）：录入框 / 统计卡 / 复习面板三块同宽、右边缘对齐，
     宽度只由这一个 max-width 管。此前 .quick 单独收 1080，而 .home 被 AppLayout 的
     `:has(.home, .learn, .detail)` 那条解除上限（max-width:none）⇒ 统计卡/复习面板
     随 .home 铺满全宽、录入框卡在 1080 ⇒ 视口一宽右边缘就错开
     （实测 1500 差 60px / 1920 差 480px / 2560 差 1120px，窗口最大化即用户报的
     "全屏下输入框右侧一大片空白"）。
     1160 = 工作面 1072 + 左右内距 88，就是 A2 拍板的 1080 那条线。

     ⚠️ **2026-11-11 改：居中**（推翻 2026-11-06 A2 的「左对齐不居中」）。
     A2 那条的理由是「居中封顶会让两侧对称空白一路增长」——**但左对齐的代价更大**：
     左侧永远贴着侧栏（实测 0px），右侧空白随窗口线性涨（1920 → 496px / 2560 → 1136px）
     ⇒ **左右留白比例必然随缩放变化**（实测 1440 时 0.36 : 1、1920 时 11.3 : 1、2560 时 25.8 : 1），
     正是用户报的「左右边比例在不同缩放程度不一样，全屏下右边空一大片」。
     居中之后左右留白**恒等** ⇒ 缩放时同比变化、比例恒定。
     ⇒ **对称空白不是缺陷，是版心居中的正常结果**；真正难看的是「一边贴边、一边空一半」。
     与列表页 .learn（也是居中，1920 时 x=508 / 左右各 252）口径一致 —— 两页同属一个壳，
     规则必须一样，不能各写一份。 */
  max-width: 1160px;
  margin: 0 auto;
  /* 左右内距走 --home-pad-x（偏好面板「首页 → 左右」）。
     ⚠️ **这个变量原来零消费者**（面板里那个滑块拖了没反应，AppLayout 的注释还写着
     「左右留白交给偏好面板（--home-pad-x）」—— 注释在撒谎，规则实际不存在）。
     2026-11-11 把它接上：**滑块终于活了**，且默认 44 = 原写死值 ⇒ 视觉零变化。
     ⚠️ **左右必须相等**，否则版心就不再居中（外边距不对称时居中偏移）⇒ 这条只给一个值，
     不分左右。 */
  padding: 34px var(--home-pad-x, 44px) 34px;
}
/* 底部的「弹性尾巴」：`.home::after` 是一个**零内容**的 flex 项，专门吃录入区让出来的
   那段高度（--home-tail-grow）。它不是装饰，是这个滑块能用的前提。

   **为什么非有它不可**：flex-grow 只能在"参与分配的元素之间"分。录入区调小时，
   如果下面两排也跟着长，省下的高度就被它们分走；可下面两排一旦按用户要求
   **固定自身高度**（不再 grow），就等于退出了分配 —— 剩下唯一还在 grow 的录入区会
   **恒定地拿走全部剩余高度**，滑块立刻变成一根废的。尾巴没有内容、没有 min-content
   地板，比例才永远绑得住。

   这不是推理，是量出来的（634 宽窄视口，量 ProseMirror 的 rect，不是目测卡片边框）：
   **100% → 232px、65% → 232px、40% → 119px**。65% 以上纹丝不动，**不是比例算错**，
   是"没有可分配的对手方"——下面两排早已卡在各自内容高度的地板上，录入区没东西可抢。

   `margin-top` 抵掉一个 gap：尾巴排在 .panel 之后，gap 会算它一份。默认档 tail=0
   时它必须不占位，否则底部凭空多出一条 --home-gap 的空白，"以当前为默认"就不成立。 */
.home::after {
  content: '';
  flex: var(--home-tail-grow, 0) 1 0;
  margin-top: calc(-1 * var(--home-gap, var(--sp-14)));
}
/* 录入框占可分配高度的几成：--home-quick-grow / --home-tail-grow 由 uiPrefs.js 写进
   <html> 行内（百分比 → 一对 flex-grow 比值，见那边 quickGrow）。没存过偏好时两个都不写，
   这里的 999 / 0 顶上 = 全部高度给录入框、尾巴 0 高，**默认即现状**。 */
.quick {
  flex: var(--home-quick-grow, 999) 1 0;
  /* 输入框的**高度下限**（2026-11-15 用户：「输入框不能被挤压，下方的栏先被挤压
     然后才是输入框」）：矮窗时压缩顺序 = 统计卡/复习面板先（它们 flex-shrink:1），
     录入区最后——原来它 basis 0 又不设底，矮窗直接塌成一行。 */
  min-height: 220px;
  /* 右上角集群（导入 + 类型）浮在卡片角上 ⇒ 需要定位上下文 */
  position: relative;
  display: flex;
  flex-direction: column;
  /* 宽度不在这儿设：整页一条工作面由 .home 的 max-width 管（2026-11-09 收敛）。
     这里曾有一条 `max-width: 1080px`（A2 拍板），但它**单独**收口的后果是
     录入框 1080、统计卡/复习面板铺满全宽，右边缘错开（见上面 .home 的注释）——
     同一个宽度两条规则各管一半，早晚打起来 ⇒ 收敛成 .home 一条。 */
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  /* 顶部内距 = 工具条下沿到正文的呼吸位（编辑器读这个变量，
     见 kb-editor.css 的 .md-toolbar）—— 两种模式下正文第一行的位置一致。
     左右内距和详情页的正文框**写死同一个值**（--sp-12 = 16px）：
     两个页面的框共用一个内距，文字离框边的距离才两边一样
     （之前这个写死 --sp-12(16px)、详情卡片 24px，
     同一个白边值叠在两个不同底子上 ⇒ 两页间距对不上）。 */
  --kb-frame-pad-y: var(--sp-11);
  padding: var(--kb-frame-pad-y) var(--sp-12) var(--sp-10);
  /* 聚焦装裱线需要定位上下文（::before 贴顶边） */
  position: relative;
  /* 量子签名：录入卡走靛→青渐变描边（token 层只管颜色，这层是页面的形状签名） */
  transition: border-color 0.15s, box-shadow 0.15s, background var(--t-slow, 300ms);
}
[data-theme="liangzi"] .quick {
  border-color: transparent;
  background:
    linear-gradient(var(--cc-surface), var(--cc-surface)) padding-box,
    linear-gradient(135deg, rgba(129, 140, 248, 0.55), rgba(45, 212, 191, 0.28) 60%, rgba(129, 140, 248, 0.4)) border-box;
}
[data-theme="liangzi"] .quick:hover,
[data-theme="liangzi"] .quick:focus-within { border-color: transparent; }
/* 素雅签名：录入区内极淡点阵（写作纸感） */
[data-theme="suya"] .quick {
  background-image: radial-gradient(circle, rgba(51, 46, 34, 0.09) 1px, transparent 1.5px);
  background-size: 22px 22px;
  background-position: 6px 10px;
}
/* 聚焦时顶边浮出一条主题色装裱线（token：--cc-quick-line，见 themes.css） */
.quick::before {
  content: '';
  position: absolute;
  top: -1px;
  left: 14px;
  right: 14px;
  height: 2px;
  border-radius: 0 0 2px 2px;
  background: var(--cc-quick-line);
  opacity: 0;
  transform: scaleX(0.55);
  transition: opacity 0.2s var(--ease, ease), transform 0.28s var(--ease, ease);
}
.quick:focus-within::before { opacity: 1; transform: scaleX(1); }
/* 快捷录入：整块可点，视觉上就是"这里能写"。
   padding / border / 圆角跟随手记页原来那份 .composer 对齐（首页原来是 14px 16px 12px，
   随手记页是 12px 14px 10px），既然录入已经统一收在这一页，就用编辑器那一套的值。
   正文字号 / 行高不在这里设：.ProseMirror 是 Milkdown 渲染出来的，节点上不带本文件的
   data-v，从 scoped 样式里 :deep 过去匹配不可靠，统一由 MilkdownEditorBody 的全局规则负责。 */
.quick:focus-within {
  /* §4.3 的硬编色映射表：
     #a8ceff → --el-color-primary-light-7(#bfd9ff)
     rgba(43,127,255,.08) → --el-color-primary-light-9(#eaf2ff) */
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 2px 12px var(--el-color-primary-light-9);
}
/* 全屏开关：24×24 的图标按钮，和右侧「记下来」一起由 space-between 分到两头。
   配色压到 text-4（比「记下来」轻一档），hover 才提上来。 */
.quick__left {
  display: flex;
  align-items: center;
  gap: var(--sp-5);
}

/* ---------- 沉浸模式（?full=1）----------
   整页只剩这一块输入区，别的（概览 / 待复习）全部收掉。
   判据类 .is-immersive 由 AppLayout 挂在 .app-content 上，编辑器撑满
   （min-height: 60vh / 解除 80vh 封顶）在 MilkdownEditorBody 的全局块里。

   原来这里还有一条 `.is-immersive .home { gap: 0 }`，已删：沉浸模式下
   .stats / .home__row 是 display:none，**display:none 的元素不参与 flex 布局**，
   .home 只剩 .quick 一个孩子，gap 根本没有两个相邻项可隔，是条死规则。

   ⚠️ **`.home__row` 必须一起收**（#895 加的）：两块面板被收进它之后，
   只藏 `.panel` 的话 **`.home__row` 自己还在** ⇒ 它是个空容器，
   但它是 `.home` 的 flex 项 ⇒ 仍占一行高度 + 一份 gap ⇒ 沉浸模式多出一条空白。
   ⇒ 收容器本身，不收它的孩子（容器没了，孩子跟着走）。 */
.is-immersive .stats,
.is-immersive .home__row {
  display: none;
}
/* 全屏下没有别的内容作参照，卡片那圈边框和圆角是多余的，直接摊成一张纸。
   flex / flex-direction 不在这里重复——.quick 已经是 `flex:1` + column 了
   （#123 给首页做三排等分时加的），这里只改"看起来像不像卡片"。 */
.quick.is-big {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
}
.quick__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-10);
  padding-top: var(--sp-10);
  border-top: 1px solid var(--cc-surface-2);
}
/* ⚠️ 右上角集群（导入 + 类型）现在住在**顶栏色带右端**（`toolbar-end` 插槽），
   不再需要浮层定位（2026-11-16 用户：「是顶栏的右上角啊」）。 */
.quick {
  position: relative;
}
/* 概览。**不参与高度分配**（`flex: 0 0 auto` = 保持自己的高度）：
   用户明确要求"第二三行不要改变本身的框的大小"——它们被拉高时框内的字会偏上/偏下，
   数字和标签不在一条基线上。与其去修那个对齐，不如让它们根本不参与分配；
   录入区让出来的高度由 .home::after 那条尾巴接住（见上面那段）。
   ⚠️ **三列**（2026-11-03：「目录」那张卡删掉 ⇒ 四列变三列）。 */
.stats {
  /* 2026-11-15：0 0 auto ⇒ 矮窗时纹丝不动、录入区被压没。改成**先让位**
     （用户定的压缩顺序：下方的栏先被挤压、然后才是输入框）。shrink 到内容以下
     就裁掉（overflow:hidden），录入区有 220px 的底在 .quick 上。 */
  flex: 0 1 auto;
  min-height: 0;
  overflow: hidden;
  display: grid;
  /* 三列随工作面三等分。P0-4 要解决的"4K 上每张 730–1161px 宽的空招牌"由
     .home 的整页收口解决（1072 工作面 ⇒ 每张 ~346px，与当初想收的 360 同档），
     这里不需要再各自封顶。 */
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.stat {
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: 22px 24px 20px;
}
/* 可点的那两张。`text-decoration: none` 得自己写：全局 reset 只清了 margin/padding，
   <a> 的下划线还在（侧边栏 .app-cats__item 当年也是单独补的这条）。
   边框/阴影的变化和知识点卡片 .card:hover 用同一组色，一套视觉语言。 */
.stat--link {
  cursor: pointer;
  text-decoration: none;
  /* transform 供主题签名 hover 浮起 1px 用（本色/素雅，2026-11-14） */
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.18s var(--ease, ease);
}
.stat--link:hover {
  border-color: var(--cc-primary-border);
  /* §4.3 的阴影两档：0 3px 14px rgb(31 35 41 / 5%) → --cc-shadow-1 */
  box-shadow: var(--cc-shadow-1);
}
.stat__num {
  font-size: var(--text-stat);
  font-weight: 600;
  color: var(--cc-text-1);
  line-height: 1.05;
  font-variant-numeric: tabular-nums;
  /* 素雅=衬线数字 / 量子=等宽数字（token 随主题切换，本色=正文字体） */
  font-family: var(--font-display);
  transition: color var(--t-fast, 140ms) var(--ease, ease);
}
/* 待归类 > 0：琥珀是「这是待办」的信息色（--cc-warning 随主题） */
.stat__num.is-busy { color: var(--cc-warning); }
.stat--link:hover .stat__num:not(.is-busy) { color: var(--cc-primary); }
.stat__label {
  font-size: var(--text-md);
  color: var(--cc-text-3);
  margin-top: 6px;
  transition: color var(--t-fast, 140ms) var(--ease, ease);
}
.stat--link:hover .stat__label { color: var(--cc-text-2); }
/* 素雅数字 36px（demo 档） */
[data-theme="suya"] .stat__num { font-size: 36px; }
/* 记下来按钮对齐 demo（渐变/圆角/高度；disabled 走 EP 浅色态自动变浅） */
.quick__actions :deep(.save-btn) {
  height: 32px;
  padding: 0 17px;
  border-radius: 10px;
  font-weight: 500;
  background: var(--cc-btn-bg);
  border: 1px solid var(--cc-btn-bd);
  box-shadow: var(--cc-btn-shadow);
}
.quick__actions :deep(.save-btn:not(.is-disabled):hover) {
  background: var(--cc-btn-bg);
  filter: brightness(1.07);
  /* --cc-primary-ring：原引用 --accent-ring 从未定义 ⇒ 这条光环一直失效，2026-11-13 修复 */
  box-shadow: 0 0 0 3px var(--cc-primary-ring);
}

/* .panel / .panel__head / .panel__title / .panel__extra 已提到
   assets/kb.css——它们不再只属于这一页（AiSettingsView 也用），继续留在 scoped 里
   就成了同名不同形的两份。（原来清单里的 `.panel__more` 已删：没有任何模板渲染它。）

   ⚠️ **这里原来还有一条 `.home > .panel { flex: 0 0 auto }`**（补"本页不参与高度分配"）
   —— #895 把两块面板收进 `.home__row` 之后**它已经不匹配任何元素**（面板不再是
   `.home` 的直接孩子）⇒ **删掉，别留一条死规则**：留着的话下一个人以为
   ".home 的直接孩子里的 .panel"规则还在，加面板回来时不会发现自己少了 flex 声明。
   现在"不参与高度分配"这条由 `.home__row` 自己承担（它才是 .home 的直接孩子）。

   `.panel` 是共享类，在 scoped 里给它加布局属性会顺带波及别的页面，
   所以**只在这页的组合里加**，不碰 kb.css 里的本体。 */

/* 最后一行：两块入口面板并排（#895，2026-11-12）。
   ⚠️ **原来两个 .panel 各占一整行**（它们是 .home 的直接孩子，各一个 flex 项）⇒
   「考试记录」加进来就多出一整行 ⇒ 首页总高被撑破 ⇒ 录入框被压掉一排。
   ⇒ 把它们收进一个两列容器，**少一行 = 录入框多长一份**（.quick 是唯一 flex-grow 的块）。

   **为什么用 grid 而不是 flex**：两块面板内容都是"标题 + 一条链接"、
   高度天然一样（各自 116.2px，实测）⇒ grid 的两列等高不用写额外规则；
   flex 要等高就得给 `align-items: stretch`（默认就是，但内容少的一侧会被拉空）。
   grid 还能让两列**等宽**（各半），flex 得写 flex:1 两遍。

   **间距 = 上下那套 gap**（20px，见 .home 的 gap）⇒ 同一行控件只准一种间距
   （横向 gap 与纵向 gap 同值，两种都是"相邻块之间"的距离）。 */
.home__row {
  /* 同 .stats：矮窗时先于录入区让位（2026-11-15），shrink 到内容以下就裁掉 */
  flex: 0 1 auto;
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
/* 两块面板在这行里**不参与高度分配**（理由同 .stats：用户明确要求"第二三行不要改变
   本身的框的大小"）⇒ 高度由内容定，录入框让出来的高度由 .home::after 那条尾巴接住。 */
.home__row > .panel {
  min-width: 0;
}
/* 窄屏：两块并排会把每块压到读着挤的宽度（链接文字 + 图标一行放不下就换行）。
   900px 以下**退回上下两行** —— 那一档录入框本来就没多少高度，让出的一行还给用户。 */
@media (max-width: 900px) {
  .home__row {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 面板里的入口行（复习 / 考试记录两个面板共用一条样式）。
   ⚠️ **名字原来叫 `.review-empty`**（2026-11-06 两个类合并成一个时留下的）——
   考试记录面板一来它就不再只属于"复习"，名字跟着中性化（重复 2 处即收敛：
   两个面板同一行样式，名字却只说其中一个 = 下一个改样式的人找不到它）。
   ⚠️ **text-decoration 不能删**：<a> 确实需要（全局 reset 不清它，AppLayout:840-841 有同样说明）。 */
.panel-link {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-11) var(--sp-14) var(--sp-13);
  font-size: var(--text-base);
  color: var(--cc-text-3);
  width: 100%;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}
.panel-link__icon {
  font-size: var(--text-2xl);
}
.panel-link:hover {
  color: var(--cc-primary);
}
/* 图标拨一下再回位 —— 小彩蛋级微交互（keyframes 在 themes.css，名字 clock-tick 是
   当初给复习面板的时钟指针做的；现在两个面板的图标都吃它，就是一个通用的 hover 小动效） */
.panel-link:hover .panel-link__icon { animation: clock-tick 0.5s var(--ease, ease); }
/* 量子签名：统计卡与复习面板走渐变描边。2026-11-14 升级为**流动描边**——
   锥形渐变的角度被 @property --qspin（themes.css 注册）驱动，7s 缓转一圈；
   不支持 @property 的浏览器动画不跑，静态 conic 照常渲染（不劣化）。 */
[data-theme="liangzi"] .stat,
[data-theme="liangzi"] .panel {
  border-color: transparent;
  position: relative; /* HUD 角标 ::after 的定位上下文 */
  background:
    linear-gradient(var(--cc-surface), var(--cc-surface)) padding-box,
    conic-gradient(from var(--qspin), rgba(129, 140, 248, 0.6), rgba(45, 212, 191, 0.25), rgba(129, 140, 248, 0.15), rgba(129, 140, 248, 0.6)) border-box;
  animation: liangzi-spin 7s linear infinite;
}
@keyframes liangzi-spin { to { --qspin: 360deg; } }
[data-theme="liangzi"] .stat--link:hover,
[data-theme="liangzi"] .panel:hover { border-color: transparent; }
[data-theme="liangzi"] .stat--link:hover {
  box-shadow: 0 0 0 1px rgba(129, 140, 248, 0.35), 0 8px 30px rgba(99, 91, 232, 0.2);
}
/* HUD 角标（2026-11-14 提案④）：hover 时四角浮出示波器式定位角——
   8 段 L 形用一张 ::after 的 8 层背景画（不用加 DOM）；::after 与素雅墨晕（themes.css）互斥不冲突。 */
[data-theme="liangzi"] .stat::after {
  content: '';
  position: absolute;
  inset: 5px;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.2s var(--ease, ease);
  background:
    linear-gradient(var(--cc-primary), var(--cc-primary)) top left / 10px 1.5px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) top left / 1.5px 10px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) top right / 10px 1.5px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) top right / 1.5px 10px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) bottom left / 10px 1.5px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) bottom left / 1.5px 10px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) bottom right / 10px 1.5px,
    linear-gradient(var(--cc-primary), var(--cc-primary)) bottom right / 1.5px 10px;
  background-repeat: no-repeat;
}
[data-theme="liangzi"] .stat--link:hover::after { opacity: 0.75; }
/* 量子数字微光（稿 theme-bak L652）2026-11-14 升级为呼吸：缓慢明灭（reduced-motion 回落到静态值） */
[data-theme="liangzi"] .stat__num {
  text-shadow: 0 0 12px rgba(129, 140, 248, 0.3);
  animation: liangzi-num-glow 3.2s ease-in-out infinite;
}
@keyframes liangzi-num-glow {
  0%, 100% { text-shadow: 0 0 10px rgba(129, 140, 248, 0.22); }
  50% { text-shadow: 0 0 16px rgba(129, 140, 248, 0.42); }
}

/* 曜夜首页签名（稿 theme-bak L626-640，2026-11-13 上线）：金→暗渐变描边 + hover 外发光 + 数字微光。
   ⚠️ 必须写在组件里（量子签名同款位置），不能放 themes.css——
   .stat/.quick 的底色与焦点阴影由本文件 scoped 声明，比 themes.css 后加载、同特异性 ⇒ 写那边会被压掉。 */
[data-theme="yaoye"] .stat,
[data-theme="yaoye"] .panel {
  border-color: transparent;
  background:
    linear-gradient(var(--cc-surface), var(--cc-surface)) padding-box,
    linear-gradient(135deg, rgba(232, 176, 89, 0.4), rgba(232, 176, 89, 0.08) 55%, rgba(232, 176, 89, 0.28)) border-box;
}
[data-theme="yaoye"] .stat--link:hover {
  box-shadow: 0 0 0 1px rgba(232, 176, 89, 0.3), 0 10px 34px rgba(232, 176, 89, 0.14);
  /* 金边流光（2026-11-14 提案②）：hover 时描边金光沿卡边巡一圈（@property --yspin，themes.css 注册） */
  background:
    linear-gradient(var(--cc-surface), var(--cc-surface)) padding-box,
    conic-gradient(from var(--yspin), rgba(232, 176, 89, 0.85), rgba(232, 176, 89, 0.12) 25%, rgba(232, 176, 89, 0.3) 50%, rgba(232, 176, 89, 0.1) 75%, rgba(232, 176, 89, 0.85)) border-box;
  animation: yaoye-spin 2.8s linear infinite;
}
@keyframes yaoye-spin { to { --yspin: 360deg; } }
/* 数字微光升级为呼吸（reduced-motion 回落静态值） */
[data-theme="yaoye"] .stat__num {
  text-shadow: 0 0 14px rgba(232, 176, 89, 0.25);
  animation: yaoye-num-glow 3.6s ease-in-out infinite;
}
@keyframes yaoye-num-glow {
  0%, 100% { text-shadow: 0 0 10px rgba(232, 176, 89, 0.18); }
  50% { text-shadow: 0 0 18px rgba(232, 176, 89, 0.4); }
}
/* 录入卡聚焦换成金环 + 金晕（盖掉上面 .quick:focus-within 默认那套浅色焦点阴影） */
[data-theme="yaoye"] .quick:focus-within {
  border-color: transparent;
  box-shadow: 0 0 0 3px var(--cc-primary-ring), 0 0 24px rgba(232, 176, 89, 0.1);
}

/* ================= hover 浮起 1px（2026-11-14 提案：本色③ / 素雅③） =================
 * transition 已在 .stat--link 基段（transform 0.18s）；素雅同时有 themes.css 的墨晕，两者叠加。 */
[data-theme="bense"] .stat--link:hover,
[data-theme="suya"] .stat--link:hover {
  transform: translateY(-1px);
}
[data-theme="bense"] .stat--link:hover {
  box-shadow: 0 3px 14px rgb(31 35 41 / 8%);
}
/* 量子按钮扫光（2026-11-14 提案⑤）：hover 时高光沿按钮平移一次（background-position，只动合成层） */
[data-theme="liangzi"] .quick__actions :deep(.save-btn) {
  background: linear-gradient(110deg, #655cf5 0%, #4f46e5 38%, #818cf8 50%, #4f46e5 62%, #4f46e5 100%);
  background-size: 220% 100%;
  background-position: 0% 0;
  transition: background-position 0.6s var(--ease, ease);
}
[data-theme="liangzi"] .quick__actions :deep(.save-btn:not(.is-disabled):hover) {
  background: linear-gradient(110deg, #655cf5 0%, #4f46e5 38%, #818cf8 50%, #4f46e5 62%, #4f46e5 100%);
  background-size: 220% 100%;
  background-position: 100% 0;
  filter: none;
}
</style>
