<template>
  <div class="learn">
    <!-- 紧凑录入 -->
    <section class="composer">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="记一条…（Ctrl + Enter 保存）"
        @keydown.ctrl.enter="save"
        @keydown.meta.enter="save"
      />
      <div class="composer__actions">
        <span class="composer__hint">随手记，不用想结构，之后再归类</span>
        <el-button type="primary" size="small" :loading="saving" :disabled="!draft.trim()" @click="save">
          记下来
        </el-button>
      </div>
    </section>

    <!-- 面包屑：只在层级 > 1 时出现。层级本身不常驻展示，靠拖拽树（放）和 hover 级联（进）。 -->
    <nav v-if="crumbs.length > 1" class="crumbs">
      <template v-for="(c, i) in crumbs" :key="c.id">
        <span v-if="i" class="crumbs__sep">›</span>
        <button
          class="crumbs__link"
          :class="{ 'is-cur': c.id === currentCategoryId }"
          @click="setCategoryFilter(c.id)"
        >{{ c.name }}</button>
      </template>
    </nav>

    <!-- 筛选：一行，不再两排 chip 糊满屏 -->
    <div class="bar">
      <div class="bar__cats">
        <button class="pill" :class="{ 'is-on': categoryFilter === ALL }" @click="setCategoryFilter(ALL)">
          全部 {{ allTopics.length }}
        </button>
        <button
          class="pill"
          :class="{ 'is-on': categoryFilter === UNCATEGORIZED }"
          @click="setCategoryFilter(UNCATEGORIZED)"
        >待归类 {{ uncategorizedCount }}</button>
      </div>

      <div class="bar__right">
        <el-input
          v-model="keyword"
          placeholder="搜索标题 / 正文"
          size="small"
          clearable
          class="bar__search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <!-- 标签收进下拉：低频操作，不该占首屏 -->
        <el-popover
          v-model:visible="tagPanelVisible"
          :width="260"
          trigger="click"
          placement="bottom-end"
          :teleported="false"
        >
          <template #reference>
            <button class="pill pill--filter" :class="{ 'is-on': tagFilter !== null }">
              标签
              <span v-if="tagFilter" class="pill__on">{{ tagFilter }}</span>
              <el-icon v-else class="pill__caret"><ArrowDown /></el-icon>
            </button>
          </template>
          <div class="tagpanel">
            <button class="tagpanel__item" :class="{ 'is-on': tagFilter === null }" @click="pickTag(null)">
              全部标签
            </button>
            <div v-if="tagList.length" class="tagpanel__list">
              <button
                v-for="t in tagList"
                :key="t.name"
                class="tagpanel__item"
                :class="{ 'is-on': tagFilter === t.name }"
                @click="pickTag(t.name)"
              >
                <span>{{ t.name }}</span>
                <span class="tagpanel__count">{{ t.count }}</span>
              </button>
            </div>
            <p v-else class="tagpanel__empty">还没有标签</p>
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 平铺。这里显示的是**当前目录整棵子树**的知识点（见 matchCategory）。
         卡片上不放目录入口：目录一多就没法在小 chip 里选了，
         归类和改类都是"把卡片拖到右侧浮出的那棵树"这一个动作。 -->
    <div v-if="shown.length" class="grid">
      <TopicCard
        v-for="t in shown"
        :key="t.id"
        :topic="t"
        @click="goDetail(t)"
        @delete="handleDelete"
      />
    </div>
    <div v-else class="empty">
      <p>{{ emptyText }}</p>
      <el-button v-if="isFiltered" size="small" text type="primary" @click="clearFilters">
        清除筛选
      </el-button>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'CorecraftWebAppLearn' })
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElPopover } from 'element-plus'
import { Search, ArrowDown } from '@element-plus/icons-vue'
import {
  ensureTopicsLoaded, ensureCategoriesLoaded, ensureAllTagsLoaded,
  getTopics, getUncategorizedCount, getCategory, getCategoryPath, getCategorySubtreeIds,
  getAllTags, createTopic, deleteTopic, encodeTag, UNCATEGORIZED
} from './useCorecraftWebStore'
import TopicCard from './TopicCard.vue'

const route = useRoute()
const router = useRouter()

const draft = ref('')
const saving = ref(false)
const tagPanelVisible = ref(false)
const tagFilter = ref(null)
const tagList = ref([])
const ALL = 'all'

// 目录筛选的真相源是 URL：侧边栏的目录链接和这里的两个药丸都在写它，本地 ref 只是它的镜像。
// 两个方向的同步缺一不可——
//   只读不写：侧边栏换目录时组件复用不重挂载，筛选不跟着变（URL 变了、列表没反应，只能硬刷新）；
//   只写不读：药丸改了本地值但地址栏还停在 ?category=1，再点同一个侧边栏目录时路由无变化、
//             watch 不触发，界面再也回不去。
function readCategoryQuery(c) {
  if (c === undefined) return ALL
  return c === UNCATEGORIZED ? UNCATEGORIZED : Number(c)
}

const categoryFilter = ref(readCategoryQuery(route.query.category))
const keyword = ref(String(route.query.q || ''))

function setCategoryFilter(key) {
  // 值为 undefined 的 query 会被 vue-router 直接删掉，"全部"就是不带 category 参数
  router.replace({ path: route.path, query: { ...route.query, category: key === ALL ? undefined : key } })
}

const allTopics = computed(() => getTopics())
const uncategorizedCount = computed(() => getUncategorizedCount())

// 目录被删掉后 URL 还指着它时 getCategory 拿不到，当成"全部"，不白屏
const currentCategoryId = computed(() => getCategory(categoryFilter.value)?.id ?? null)
const crumbs = computed(() => (currentCategoryId.value ? getCategoryPath(currentCategoryId.value) : []))

function matchTag(t) {
  return tagFilter.value === null || (t.tags || []).some((tag) => encodeTag(tag) === tagFilter.value)
}

/**
 * 选一个目录 = 看**整棵子树**。
 *
 * 为什么不是"只显示直接挂载"：那样进到「集合」只能看到 3 条，
 * 它父目录里另外 20 条就凭空消失了，层级会变成"建得进、看不见"。
 * 子树平铺让页面永远是一排卡片，表达层级靠卡片上的目录 chip，不靠额外的子目录区块。
 */
const subtreeIds = computed(() =>
  currentCategoryId.value ? getCategorySubtreeIds(currentCategoryId.value) : null
)

function matchCategory(t) {
  if (categoryFilter.value === UNCATEGORIZED) return t.categoryId == null
  if (!subtreeIds.value) return true
  return subtreeIds.value.has(t.categoryId)
}

function matchKeyword(t) {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return true
  return (t.title || '').toLowerCase().includes(q) || (t.content || '').toLowerCase().includes(q)
}

const shown = computed(() => allTopics.value.filter((t) => matchCategory(t) && matchTag(t) && matchKeyword(t)))

const isFiltered = computed(() =>
  categoryFilter.value !== ALL || tagFilter.value !== null || Boolean(keyword.value.trim())
)

const emptyText = computed(() => {
  if (categoryFilter.value === UNCATEGORIZED) return '没有待归类的知识点'
  if (currentCategoryId.value) return '这个目录还是空的 —— 把未归类的知识点拖进来'
  return keyword.value.trim() ? '没搜到' : '还没有知识点'
})

function pickTag(name) {
  tagFilter.value = name
  tagPanelVisible.value = false
}

function clearFilters() {
  setCategoryFilter(ALL)
  tagFilter.value = null
  keyword.value = ''
}

async function refresh() {
  await Promise.all([ensureTopicsLoaded(true), ensureCategoriesLoaded(true), ensureAllTagsLoaded(true)])
  tagList.value = [...getAllTags()].sort((a, b) => b.count - a.count)
}

async function save() {
  const content = draft.value.trim()
  if (!content || saving.value) return
  saving.value = true
  try {
    await createTopic({ title: '', content, tags: [] })
    draft.value = ''
    await refresh()
    ElMessage.success('已记下')
  } finally {
    saving.value = false
  }
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
}

async function handleDelete(t) {
  try {
    await ElMessageBox.confirm(`确定删除「${t.title || '未命名'}」吗？`, '删除知识点', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deleteTopic(t.id)
  ElMessage.success('已删除')
  await refresh()
}

// 侧边栏（AppLayout）也在写这两个 query：目录链接写 category，搜索框回车写 q。
// 只在 onMounted 读一次的话，页面已经挂着时路由变了没人接——URL 变了、界面纹丝不动。
watch(() => route.query.category, (c) => { categoryFilter.value = readCategoryQuery(c) })
// 关键词只做入站同步，不反向写：这里的搜索框是逐字过滤的，每次按键都 replace 路由代价太大。
// 重复提交同一个关键词本来就没区别，不存在"点不动"的副作用。
watch(() => route.query.q, (q) => { keyword.value = String(q || '') })

onMounted(refresh)
</script>

<style scoped>
.learn {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 录入 */
.composer {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 12px 14px 10px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer:focus-within {
  border-color: #a8ceff;
  box-shadow: 0 2px 12px rgba(43, 127, 255, 0.08);
}
.composer :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 14px;
  line-height: 1.7;
  background: transparent;
}
.composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f4f6f8;
}
.composer__hint {
  font-size: 12px;
  color: #b6bac1;
}

/* 筛选条：一行 */
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.bar__cats {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.bar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.bar__search {
  width: 180px;
}

.pill {
  font-size: 12px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 14px;
  background: #f1f2f4;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.pill:hover {
  background: #e8eaed;
  color: #1f2329;
}
.pill.is-on {
  background: #1f2329;
  color: #fff;
}
/* 「待归类」的落点反馈。放在 .is-on 之后：它常常正好是当前筛选的那个，
   同优先级靠后者覆盖，让"正在放这里"压过"已选中"。 */
.pill.is-drop {
  background: #2b7fff;
  color: #fff;
  box-shadow: 0 0 0 3px rgba(43, 127, 255, 0.16);
}
.pill--filter {
  background: transparent;
  border-color: #e2e5e9;
}
.pill__on {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pill__caret {
  font-size: 11px;
}

/* 标签下拉 */
.tagpanel {
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-height: 240px;
  overflow-y: auto;
}
.tagpanel__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  text-align: left;
}
.tagpanel__item:hover {
  background: #f4f6f8;
}
.tagpanel__item.is-on {
  background: #eef5ff;
  color: #2b7fff;
  font-weight: 500;
}
.tagpanel__count {
  font-size: 11px;
  color: #b6bac1;
}
.tagpanel__empty {
  margin: 0;
  padding: 8px;
  font-size: 12px;
  color: #b6bac1;
}

/* 卡片网格：固定列宽 + 卡片等高 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(268px, 1fr));
  gap: 12px;
}
.grid :deep(.topic-card-wrap) {
  margin-bottom: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* ---------- 面包屑（层级 > 1 才出现） ---------- */
.crumbs {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
  font-size: 12px;
}
.crumbs__link {
  border: none;
  background: transparent;
  padding: 2px 5px;
  border-radius: 4px;
  color: #9096a0;
  font-size: 12px;
  cursor: pointer;
}
.crumbs__link:hover {
  color: #1f2329;
}
.crumbs__link.is-cur {
  color: #1f2329;
  font-weight: 600;
  cursor: default;
}
.crumbs__link.is-cur:hover {
  color: #1f2329;
}
.crumbs__sep {
  color: #d0d3d9;
}

.empty {
  padding: 48px 0;
  text-align: center;
}
.empty p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #b6bac1;
}
</style>
