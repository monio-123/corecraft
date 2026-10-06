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
      <MilkdownEditor :key="editorKey" v-model="draft" v-model:show-toolbar="toolbar" @submit="save" />
      <div class="quick__actions">
        <!-- 左边两个都是"编辑器/页面"的开关（格式工具栏显不显、要不要进全屏），
             右边是"记下来"这个主操作。space-between 要的是两个子元素，
             所以左边得有个容器把它们圈住。 -->
        <div class="quick__left">
          <!-- 类型：默认就是「笔记」，不点就是零成本。放在动作行最左边——
               它决定"这条记下来是什么"，比右边两个显示开关更像主信息。 -->
          <TopicTypeSelect v-model="draftType" />
          <el-tooltip :content="toolbar ? '切换到简洁模式（隐藏工具栏）' : '切换到完整模式（显示格式工具栏）'" placement="top">
            <button
              class="kb-icon-btn"
              :aria-label="toolbar ? '切换到简洁模式' : '切换到完整模式'"
              @click="toolbar = !toolbar"
            >
              <el-icon><Tools /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip :content="immersive.on ? '退出全屏 (Esc)' : '全屏录入'" placement="top">
            <button
              class="kb-icon-btn"
              :aria-label="immersive.on ? '退出全屏' : '全屏录入'"
              @click="immersive.toggle"
            >
              <el-icon><component :is="immersive.on ? Close : FullScreen" /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <el-button type="primary" size="small" :loading="saving" :disabled="!draft.trim()" @click="save">
          记下来
        </el-button>
      </div>
    </section>

    <!-- 概览 -->
    <section class="stats">
      <div class="stat">
        <div class="stat__num">{{ topicCount }}</div>
        <div class="stat__label">知识点</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ categories.length }}</div>
        <div class="stat__label">目录</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ uncategorizedCount }}</div>
        <div class="stat__label">待归类</div>
      </div>
      <div class="stat">
        <div class="stat__num">{{ todayCount }}</div>
        <div class="stat__label">今日新增</div>
      </div>
    </section>

    <!-- 今日待复习：能力还没做，先把位置和调性占住 -->
    <section class="panel">
      <div class="panel__head">
        <span class="panel__title">今日待复习</span>
        <span class="panel__extra">0 条</span>
      </div>
      <div class="review-empty">
        <el-icon class="review-empty__icon"><Clock /></el-icon>
        <span>复习计划还没开启</span>
      </div>
    </section>

    <!-- 最近记录 -->
    <section class="panel">
      <div class="panel__head">
        <span class="panel__title">最近记录</span>
        <RouterLink to="/app/learn" class="panel__more">全部 →</RouterLink>
      </div>
      <ul v-if="recentTopics.length" class="recent">
        <li
          v-for="t in recentTopics"
          :key="t.id"
          class="recent__item"
          @click="goDetail(t)"
        >
          <span class="recent__title">{{ t.title || excerptOf(t.content, 18) }}</span>
          <span class="recent__cat">{{ getCategoryName(t.categoryId) }}</span>
        </li>
      </ul>
      <p v-else class="recent__empty">还没有记录，从上面记第一条吧</p>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Close, FullScreen, Tools } from '@element-plus/icons-vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import { loadShellData } from './useShellData'
import { useImmersive } from './useImmersive'
import TopicTypeSelect from './TopicTypeSelect.vue'
import {
  ensureTopicsLoaded, ensureCategoriesLoaded, getTopics, getCategories,
  getUncategorizedCount, getCategoryName, createTopic, excerptOf, ensureTopicTypesLoaded
} from './useCorecraftWebStore'

const router = useRouter()
// 全屏录入：整页只剩这一块输入区（见下面的 .is-immersive 规则）。
// 和详情页、AppLayout 共用同一个判据 useImmersive()，状态在 URL 上。
const immersive = useImmersive()
// 工具栏显不显示，状态在本层——「简洁/完整」开关按钮就在下面那个动作行里，
// 按钮和状态得是同一份。传下去用 v-model:show-toolbar。
const toolbar = ref(false)
// 录入时选的类型。存 null = 用默认档（字典里排第一的那条）
const draftType = ref(null)

const draft = ref('')
// 强制重建编辑器来清空输入框：defaultValueCtx 只在创建那一刻读一次 defaultValue，
// 事后把 draft 置空不会回写 doc。重建顺带把 undo 历史也清了，本来也该清。
const editorKey = ref(0)
const saving = ref(false)

const topics = computed(() => getTopics())
const categories = computed(() => getCategories())
const topicCount = computed(() => topics.value.length)
const uncategorizedCount = computed(() => getUncategorizedCount())
const recentTopics = computed(() => topics.value.slice(0, 6))

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
    ElMessage.success('已记下')
  } finally {
    saving.value = false
  }
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
}

onMounted(async () => {
  // 共享 inflight：AppLayout 已经拉过会话时，这里不会再打一次接口。
  // 三个 ensure 收敛在 loadShellData 一处（原来这里和 AppLayout 各写了一遍）。
  // 这里不加 try/catch：壳先挂载，它成功了这一步必然命中缓存、不会失败；
  // 失败的状态由壳的侧边栏那格「加载失败 · 重试」统一承担（见 AppLayout.loadShell）。
  // 只等它把数据拉齐，不取返回值——问候语删了之后这一页已经没有 profile 要用。
  await loadShellData()
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: calc(24px * var(--ui-scale));
}

/* 快捷录入：整块可点，视觉上就是"这里能写"。
   padding / border / 圆角跟随手记页原来那份 .composer 对齐（首页原来是 14px 16px 12px，
   随手记页是 12px 14px 10px），既然录入已经统一收在这一页，就用编辑器那一套的值。
   正文字号 / 行高不在这里设：.ProseMirror 是 Milkdown 渲染出来的，节点上不带本文件的
   data-v，从 scoped 样式里 :deep 过去匹配不可靠，统一由 MilkdownEditorBody 的全局规则负责。 */
.quick {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: calc(12px * var(--ui-scale));
  padding: calc(14px * var(--ui-scale)) calc(17px * var(--ui-scale)) calc(12px * var(--ui-scale));
  transition: border-color 0.15s, box-shadow 0.15s;
}
.quick:focus-within {
  border-color: #a8ceff;
  box-shadow: 0 2px 12px rgba(43, 127, 255, 0.08);
}
/* 全屏开关：24×24 的图标按钮，和右侧「记下来」一起由 space-between 分到两头。
   配色压到 text-4（比「记下来」轻一档），hover 才提上来。 */
.quick__left {
  display: flex;
  align-items: center;
  gap: calc(5px * var(--ui-scale));
}

/* ---------- 沉浸模式（?full=1）----------
   整页只剩这一块输入区，别的（概览 / 待复习 / 最近记录）全部收掉。
   判据类 .is-immersive 由 AppLayout 挂在 .app-content 上，编辑器撑满
   （min-height: 60vh / 解除 80vh 封顶）在 MilkdownEditorBody 的全局块里。 */
.is-immersive .stats,
.is-immersive .panel {
  display: none;
}
.is-immersive .home {
  gap: 0;
}
/* 全屏下没有别的内容作参照，卡片那圈边框和圆角是多余的，直接摊成一张纸 */
.quick.is-big {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
}
.quick.is-big .quick__actions {
  margin-top: auto;
}
.quick__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: calc(10px * var(--ui-scale));
  padding-top: calc(10px * var(--ui-scale));
  border-top: 1px solid #f4f6f8;
}
/* 概览 */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: calc(14px * var(--ui-scale));
}
.stat {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: calc(12px * var(--ui-scale));
  padding: calc(19px * var(--ui-scale)) calc(22px * var(--ui-scale));
}
.stat__num {
  font-size: calc(31px * var(--ui-scale));
  font-weight: 600;
  color: #1f2329;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.stat__label {
  font-size: calc(14px * var(--ui-scale));
  color: #a0a5ad;
  margin-top: calc(4px * var(--ui-scale));
}

/* .panel / .panel__head / .panel__title / .panel__extra / .panel__more 已提到
   kb.css——它们不再只属于这一页（AiSettingsView 也用），继续留在 scoped 里
   就成了同名不同形的两份。 */

.review-empty {
  display: flex;
  align-items: center;
  gap: calc(10px * var(--ui-scale));
  padding: calc(14px * var(--ui-scale)) 0 calc(19px * var(--ui-scale));
  font-size: calc(15px * var(--ui-scale));
  color: #b6bac1;
}
.review-empty__icon {
  font-size: calc(18px * var(--ui-scale));
}

.recent {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recent__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: calc(14px * var(--ui-scale));
  padding: calc(12px * var(--ui-scale)) 0;
  border-bottom: 1px solid #f7f8fa;
  cursor: pointer;
}
.recent__item:last-child {
  border-bottom: none;
}
.recent__item:hover .recent__title {
  color: #2b7fff;
}
.recent__title {
  font-size: calc(17px * var(--ui-scale));
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s;
}
.recent__cat {
  font-size: calc(14px * var(--ui-scale));
  color: #b6bac1;
  flex-shrink: 0;
}
.recent__empty {
  margin: 0;
  padding: calc(14px * var(--ui-scale)) 0 calc(22px * var(--ui-scale));
  font-size: calc(15px * var(--ui-scale));
  color: #b6bac1;
}
</style>
