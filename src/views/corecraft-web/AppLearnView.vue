<template>
  <div class="learn">
    <!-- 这里原来还有一条录入框（MilkdownEditor）。本页的职能是**浏览 + 归类**：
         目录筛选 / 标签筛选 / 搜索 / 卡片网格 / 拖拽归类，录入框只是顶部一条，
         名字叫「随手记」名不副实。录入已统一收到首页，这一页改成纯浏览页，
         侧边菜单同步改名「知识点」。 -->

    <!-- 面包屑：只在层级 > 1 时出现。层级本身不常驻展示，靠拖拽树（放）和 hover 级联（进）。
         和知识点详情页共用 CategoryCrumbs；这里要 replace 不 push，免得每切一次目录
         就往浏览历史里塞一条。 -->
    <CategoryCrumbs
      :category-id="currentCategoryId"
      :min-level="2"
      @pick="setCategoryFilter"
    />

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
              <!-- 整行是 div 不是 button：行尾还要再放一个「⋯」按钮，
                   <button> 套 <button> 是非法 HTML，浏览器会把里层那个提到外层外面，
                   结果布局和点击都乱。拆成"选它的按钮"+"操作按钮"两个兄弟节点。 -->
              <div
                v-for="t in tagList"
                :key="t.name"
                class="tagpanel__item"
                :class="{ 'is-on': tagFilter === t.name }"
              >
                <button class="tagpanel__pick" @click="pickTag(t.name)">
                  <span class="tagpanel__name">{{ t.name }}</span>
                  <span class="tagpanel__count">{{ t.count }}</span>
                </button>
                <TagRowMenu :tag="t" />
              </div>
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
  getTopics, getUncategorizedCount, getCategory, getCategorySubtreeIds,
  getAllTags, deleteTopic, encodeTag, deleteConfirmMessage, UNCATEGORIZED
} from './useCorecraftWebStore'
import CategoryCrumbs from './CategoryCrumbs.vue'
import TopicCard from './TopicCard.vue'
import TagRowMenu from './TagRowMenu.vue'

const route = useRoute()
const router = useRouter()

const tagPanelVisible = ref(false)
const tagFilter = ref(null)
// 标签列表：**直接算，不存快照**。
// 原来是 `ref([])` + refresh() 里 `tagList.value = [...getAllTags()].sort(...)`，那是一次性拷贝——
// store 的 renameTag / confirmDeleteTag 刷的是它自己那份 allTags，本地这份是副本不会跟着变，
// 于是"在标签下拉里改名/删除之后列表不动，必须手动刷新页面"。
// 知识点列表没这个问题是因为它一直是 computed(() => getTopics())，只有标签这一处漏了。
const tagList = computed(() => [...getAllTags()].sort((a, b) => b.count - a.count))
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

function matchTag(t) {
  return tagFilter.value === null || (t.tags || []).some((tag) => encodeTag(tag) === tagFilter.value)
}

/**
 * 选一个目录 = 看**整棵子树**。
 *
 * 为什么不是"只显示直接挂载"：那样进到「集合」只能看到 3 条，
 * 它父目录里另外 20 条就凭空消失了，层级会变成"建得进、看不见"。
 * 子树平铺让页面永远是一排卡片，层级靠侧边栏当前高亮的那一项表达，
 * 不靠额外的子目录区块。（原来这里写的是"靠卡片上的目录 chip"，
 *  那个 chip 早就撤掉了，注释和代码对不上。）
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
  // 标签列表是 computed(tagList)，这里只管把 store 的三份缓存刷一遍就够了
  await Promise.all([ensureTopicsLoaded(true), ensureCategoriesLoaded(true), ensureAllTagsLoaded(true)])
}

function goDetail(t) {
  router.push({ name: 'AppTopicDetail', params: { id: t.id } })
}

async function handleDelete(t) {
  try {
    await ElMessageBox.confirm(deleteConfirmMessage(t), '删除知识点', {
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

// 正在筛的标签被改名或删掉了 → 回到"全部"。
// 兜在这里而不是让 TagRowMenu 回调里清：改标签的入口不止这一个（详情页的标签编辑也能改），
// 在这一处 watch 一次，所有入口都覆盖到。不兜的表现是"筛选还开着但列表空了"，
// 用户会以为知识点被删了。
watch(tagList, () => {
  if (tagFilter.value && !tagList.value.some((t) => t.name === tagFilter.value)) {
    tagFilter.value = null
  }
})

onMounted(refresh)
</script>

<style scoped>
.learn {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 录入（原 .composer 六条规则）：录入框已统一收到首页，这里跟着删。
   连着删掉的还有 `.composer :deep(.el-textarea__inner)`（textarea 早就不存在了）、
   `draft` / `editorKey` / `saving` 三个 state、`save()`、MilkdownEditor 的 import。 */

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
/* 一行 = 行容器（现在带标签的行是 div，因为要再塞一个「⋯」按钮）+ 选它的按钮 + 操作按钮。
   「全部标签」那行不带菜单，还是个 button，所以 cursor/字体这些两处都要留着。 */
.tagpanel__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  text-align: left;
  /* 和侧边栏目录行同一套：行尾「⋯」默认不显形，悬停/聚焦这一行才露出来。
     focus-within 不能省——opacity:0 的按钮照样能被 Tab 选中。 */
  --row-menu-show: 0;
}
.tagpanel__item:hover,
.tagpanel__item:focus-within {
  --row-menu-show: 1;
}
.tagpanel__item:hover {
  background: #f4f6f8;
}
.tagpanel__item.is-on {
  background: #eef5ff;
  color: #2b7fff;
  font-weight: 500;
}
/* 行里的"选它"那一半。名字太长得靠 min-width:0 + ellipsis 让位给右边的数字和 ⋯，
   不设的话 flex 子项的 min-width:auto 会顶着把行撑宽。 */
.tagpanel__pick {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: none;
  background: transparent;
  padding: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.tagpanel__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tagpanel__count {
  font-size: 11px;
  color: #b6bac1;
  flex-shrink: 0;
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

/* ---------- 空状态 ---------- */
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
