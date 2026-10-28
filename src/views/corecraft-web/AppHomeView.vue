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
      <MilkdownEditor :key="editorKey" v-model="draft" :show-toolbar="toolbar" @submit="save" />
      <div class="quick__actions">
        <!-- 左边两个都是"编辑器/页面"的开关（格式工具栏显不显、要不要进全屏），
             右边是"记下来"这个主操作。space-between 要的是两个子元素，
             所以左边得有个容器把它们圈住。 -->
        <div class="quick__left">
          <!-- 类型：默认就是「笔记」，不点就是零成本。放在动作行最左边——
               它决定"这条记下来是什么"，比右边两个显示开关更像主信息。 -->
          <TopicTypeSelect v-model="draftType" />
          <ViewToggles
            v-model:show-toolbar="toolbar"
            :full="immersive.on"
            placement="top"
            full-label="全屏录入"
            @toggle-full="immersive.toggle"
          />
        </div>
        <el-button type="primary" size="small" :loading="saving" :disabled="!draft.trim()" @click="save">
          记下来
        </el-button>
      </div>
    </section>

    <!-- 概览。四张里**只有前两张是入口**，用 RouterLink（不是 div onclick）：
         语义上就是链接，右键复制链接 / 中键新标签页 / 读屏器念得出来，全是白送的。
         后两张是纯数字，去处本来就在同一个壳里：
         「目录」= 侧边栏那棵树（只显示一级，这里给的是全量统计）；
         「今日新增」是全局的一个数，不指向本屏任何一块（原先指向的「最近记录」面板已删，
         见 #123）。用户明确说四张卡先不动、入口收敛的事后面单独议。
         "能不能点"只靠 hover 的边框变化 + 手型表达，**不加静态装饰**——
         四个数字并排摆着，给不动的两张也画个箭头只会把真正能点的那两张淹掉。 -->
    <section class="stats">
      <RouterLink class="stat stat--link" :to="{ path: '/app/learn' }">
        <div class="stat__num">{{ topicCount }}</div>
        <div class="stat__label">知识点</div>
      </RouterLink>
      <div class="stat">
        <div class="stat__num">{{ categories.length }}</div>
        <div class="stat__label">目录</div>
      </div>
      <RouterLink
        class="stat stat--link"
        :to="{ path: '/app/learn', query: { category: UNCATEGORIZED } }"
      >
        <div class="stat__num">{{ uncategorizedCount }}</div>
        <div class="stat__label">待归类</div>
      </RouterLink>
      <div class="stat">
        <div class="stat__num">{{ todayCount }}</div>
        <div class="stat__label">今日新增</div>
      </div>
    </section>

    <!-- 今日待复习：**只留入口**（2026-10-22 撤旧自测题时留下的）。
         复习内容、抽题规则、每日复习的单题流都随旧那套一起撤了（题和记录都没了），
         这个入口现在指向**知识点列表** —— 新的考试在那儿选范围（`/exam?ids=…`）。
         等新的复习能力做出来，再把它改成直达。 -->
    <section class="panel">
      <div class="panel__head">
        <span class="panel__title">今日待复习</span>
      </div>
      <RouterLink class="review-empty review-empty--go" :to="{ path: '/app/learn' }">
        <el-icon class="review-empty__icon"><Clock /></el-icon>
        <span>开始复习</span>
      </RouterLink>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import ViewToggles from '../../components/corecraft-web/ViewToggles.vue'
import { loadShellData } from './useShellData'
import { useImmersive } from './useImmersive'
import { keywordFromRoute } from '../../utils/routeQuery'
import TopicTypeSelect from './TopicTypeSelect.vue'
import {
  getTopicIndex, getCategories,
  getUncategorizedCount, createTopic,
  UNCATEGORIZED
} from './useCorecraftWebStore'

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

// 结构索引（全集，不含正文）—— 首页只要**条数**和"今天新增了几条"，
// 一条正文都不需要（正文那 90 万字符是列表接口慢的真正原因）。
const topics = computed(() => getTopicIndex())
const categories = computed(() => getCategories())
const topicCount = computed(() => topics.value.length)
const uncategorizedCount = computed(() => getUncategorizedCount())

// 今日新增。原来恒为 0 的原因是**日期格式对不上**：
//   `new Date().toDateString()` 返回的是英文的 "Tue Oct 06 2026"，切前 10 位得到
//   "Tue Oct 06 "，而 store 里 createdAt（后端 createTime 归一化过来的，yyyy-MM-dd HH:mm:ss）
//   切前 10 位是 "2026-10-06" —— 两者永远不相等。
//   注意字段名没错，`normalizeTopic` 已经把 createTime 映射成 createdAt 了。
//
// 日期键**本地拼**而不是 toISOString()：后者是 UTC，东八区凌晨 0~8 点会把当天
// 算成前一天，"今日新增"会在刚过完零点时莫名其妙归零。
function dateKey(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const todayCount = computed(
  () => topics.value.filter((t) => String(t.createdAt || '').slice(0, 10) === dateKey(new Date())).length
)



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
  gap: var(--home-gap, var(--sp-14));
  min-height: 100%;
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
  min-height: 0;
  display: flex;
  flex-direction: column;
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
  transition: border-color 0.15s, box-shadow 0.15s;
}
/* 快捷录入：整块可点，视觉上就是"这里能写"。
   padding / border / 圆角跟随手记页原来那份 .composer 对齐（首页原来是 14px 16px 12px，
   随手记页是 12px 14px 10px），既然录入已经统一收在这一页，就用编辑器那一套的值。
   正文字号 / 行高不在这里设：.ProseMirror 是 Milkdown 渲染出来的，节点上不带本文件的
   data-v，从 scoped 样式里 :deep 过去匹配不可靠，统一由 MilkdownEditorBody 的全局规则负责。 */
.quick:focus-within {
  border-color: #a8ceff;
  box-shadow: 0 2px 12px rgba(43, 127, 255, 0.08);
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
   .stats / .panel 是 display:none，**display:none 的元素不参与 flex 布局**，
   .home 只剩 .quick 一个孩子，gap 根本没有两个相邻项可隔，是条死规则。 */
.is-immersive .stats,
.is-immersive .panel {
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
/* 概览。**不参与高度分配**（`flex: 0 0 auto` = 保持自己的高度）：
   用户明确要求"第二三行不要改变本身的框的大小"——它们被拉高时框内的字会偏上/偏下，
   数字和标签不在一条基线上。与其去修那个对齐，不如让它们根本不参与分配；
   录入区让出来的高度由 .home::after 那条尾巴接住（见上面那段）。 */
.stats {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-11);
}
.stat {
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  padding: var(--sp-13) var(--sp-14);
}
/* 可点的那两张。`text-decoration: none` 得自己写：全局 reset 只清了 margin/padding，
   <a> 的下划线还在（侧边栏 .app-cats__item 当年也是单独补的这条）。
   边框/阴影的变化和知识点卡片 .card:hover 用同一组色，一套视觉语言。 */
.stat--link {
  cursor: pointer;
  text-decoration: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.stat--link:hover {
  border-color: var(--cc-primary-border);
  box-shadow: 0 3px 14px rgba(31, 35, 41, 0.05);
}
.stat__num {
  font-size: var(--text-stat);
  font-weight: 600;
  color: var(--cc-text-1);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.stat__label {
  font-size: var(--text-md);
  color: var(--cc-text-3);
  margin-top: var(--sp-4);
}

/* .panel / .panel__head / .panel__title / .panel__extra 已提到
   assets/kb.css——它们不再只属于这一页（AiSettingsView 也用），继续留在 scoped 里
   就成了同名不同形的两份。（原来清单里的 `.panel__more` 已删：没有任何模板渲染它。）

   这里补的是 `flex: 0 0 auto`（理由同上面的 .stats：这一页不参与高度分配）。
   `.panel` 是共享类，在 scoped 里给它加布局属性会顺带波及别的页面，
   所以**只在这页的组合里加**，不碰 kb.css 里的本体。 */
.home > .panel {
  flex: 0 0 auto;
}

.review-empty {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  padding: var(--sp-11) 0 var(--sp-13);
  font-size: var(--text-base);
  color: var(--cc-text-4);
}
.review-empty__icon {
  font-size: var(--text-2xl);
}
/* 有题可复习时同一块变入口：把 <button> 的原生外观去掉、铺满整行、文字靠左。
   `font-family: inherit` 不能省——按钮默认不继承字体族，只写 font-size 的话
   「开始复习」会和旁边的图标对不上。声明在 .review-empty 之后，靠源序覆盖它。 */
.review-empty--go {
  width: 100%;
  font-family: inherit;
  color: var(--cc-text-3);
  text-align: left;
  text-decoration: none;
  background: none;
  border: none;
  cursor: pointer;
}
.review-empty--go:hover {
  color: var(--cc-primary);
}
</style>
