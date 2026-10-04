<template>
  <div class="app-shell">
    <!-- 左侧栏：品牌 + 导航 + 目录 -->
    <aside class="app-side">
      <div class="app-side__brand">
        <div class="app-side__logo">Bro</div>
        <div class="app-side__name">知识库</div>
      </div>

      <nav class="app-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.id"
          :to="item.path"
          class="app-nav__item"
          :class="{ 'is-active': isActive(item.path) }"
        >
          <el-icon v-if="getIconComponent(item.icon)" class="app-nav__icon">
            <component :is="getIconComponent(item.icon)" />
          </el-icon>
          <span>{{ item.name }}</span>
        </RouterLink>
      </nav>

      <!-- 目录：常驻侧边栏，只放一级。
           层级不在这里常驻——232px 撑不住缩进，而层级只在两件事上才需要被看见：
           放东西（拖拽浮出完整树）和进深层（hover 弹级联）。两个入口都是临时的。 -->
      <div class="app-side__section">
        <div class="app-side__section-title">
          <span>目录</span>
          <el-tooltip content="新建一级目录" placement="right">
            <button class="app-side__add" @click="createVisible = true">＋</button>
          </el-tooltip>
        </div>
        <div v-if="rootCategories.length" class="app-cats">
          <RouterLink
            v-for="c in rootCategories"
            :key="c.id"
            :to="{ path: '/app/learn', query: { category: c.id } }"
            class="app-cats__item"
            :class="{ 'is-on': isCurrentRoot(c.id) }"
            @mouseenter="onCatEnter(c, $event)"
            @mouseleave="onCatLeave"
          >
            <span class="app-cats__name">{{ c.name }}</span>
            <span class="app-cats__count">{{ categoryCounts[c.id] || 0 }}</span>
            <!-- 右侧这个位置二选一：有下级给 ›（"停一下能进去"），没下级给 ＋
                 （当场建第一个子目录）。不给空目录弹级联——弹出来是个空壳，
                 箭头也是骗人的；但"建第一个子目录"这个能力得留在手边，
                 否则空目录就再也没法长出下级。
                 ＋ 上必须 preventDefault：这一行是 RouterLink（渲染成 <a href>），
                 只 stop 拦得住事件冒泡，拦不住浏览器照着 href 跳走。 -->
            <el-icon v-if="hasKids(c.id)" class="app-cats__more"><ArrowRight /></el-icon>
            <el-icon
              v-else
              class="app-cats__more app-cats__more--add"
              title="新建子目录"
              @click.prevent.stop="openChildCreate(c)"
            >
              <Plus />
            </el-icon>
          </RouterLink>
        </div>
        <p v-else class="app-side__empty">还没有目录</p>
      </div>

      <div class="app-side__spacer"></div>

      <!-- 底部：用户 -->
      <div class="app-side__user">
        <el-avatar :size="28" :src="currentUser.avatar || undefined" class="user-avatar">
          {{ userAvatarFallback }}
        </el-avatar>
        <span class="app-side__username">{{ currentUser.nickname || currentUser.username || '未登录' }}</span>
        <el-tooltip content="退出登录" placement="right">
          <button class="app-side__logout" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </aside>

    <!-- 右侧：顶栏 + 内容 -->
    <div class="app-main">
      <header class="app-top">
        <div class="app-top__crumb">
          {{ currentTitle }}
        </div>
        <div class="app-top__actions">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索知识点…"
            class="app-search"
            clearable
            @keyup.enter="goSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-tooltip v-if="hasAdminShell" content="管理后台" placement="bottom">
            <button class="app-top__icon-btn" @click="goAdmin">
              <el-icon><Setting /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <main class="app-content">
        <RouterView v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.name" />
          </keep-alive>
        </RouterView>
      </main>
    </div>

    <!-- 入口一（放）：拖起未归类卡片时右侧浮出完整目录树当落点，拖完即消失 -->
    <CategoryTree v-if="dnd.live" mode="drop" :items="rootCategories" />

    <!-- 入口二（进）：侧边栏一级目录 hover 停够一会儿，一次铺开它整棵子树。
         不用一层一层再 hover 展开——层级在"要挑一个深层目录"时才需要被完整看见，
         一次给全比让人自己逐层点开省事。 -->
    <CategoryTree
      v-if="cascadeFor != null"
      :key="cascadeFor"
      mode="nav"
      :items="childrenOf(cascadeFor)"
      :anchor-top="cascadeTop"
      @pick="goCategory"
      @add="openChildCreate"
      @enter="onCascadeEnter"
      @close="closeCascade"
    />

    <!-- 新建一级目录：侧边栏就地新建，不跳页 -->
    <CategoryCreateDialog v-model="createVisible" />
    <!-- 新建子目录：从级联菜单里触发，父级就是当前停着的那一级 -->
    <CategoryCreateDialog
      v-model="childCreateVisible"
      :parent-id="childParentId"
      :parent-label="childParentLabel"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { SwitchButton, Search, Setting, ArrowRight, Plus } from '@element-plus/icons-vue'
import { clearAuth } from '../../utils/auth'
import { flattenShellNav, getIconComponent } from '../../utils/menu'
import { bootstrapSession } from '../../utils/session'
import CategoryCreateDialog from '../../views/corecraft-web/CategoryCreateDialog.vue'
import CategoryTree from '../../views/corecraft-web/CategoryTree.vue'
import {
  ensureTopicsLoaded, ensureCategoriesLoaded, getChildCategories, hasChildCategories,
  getCategoryCountMap, dnd, ROOT_CATEGORY
} from '../../views/corecraft-web/useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

// 会话数据放 ref：localStorage 不是响应式的，直接读不会触发重渲染
const session = ref({ menuTree: [], menuPaths: [], profile: {} })
const searchKeyword = ref('')
const createVisible = ref(false)

// hover 级联的触发状态
const cascadeFor = ref(null)
const cascadeTop = ref(0)
let hoverTimer = null
let closeTimer = null

// 建子目录的弹窗参数，由级联菜单里那一级决定
const childCreateVisible = ref(false)
const childParentId = ref(ROOT_CATEGORY)
const childParentLabel = ref('')

const navItems = computed(() =>
  flattenShellNav('app', session.value.menuTree, session.value.menuPaths)
)
// 侧边栏只渲染一级（parentId = 0），层级交给 hover 级联
const rootCategories = computed(() => getChildCategories(ROOT_CATEGORY))
const categoryCounts = computed(() => getCategoryCountMap())

const childrenOf = (id) => getChildCategories(id)
const hasKids = (id) => hasChildCategories(id)
const isCurrentRoot = (id) => Number(route.query.category) === id

// 有管理后台菜单的（超管）才给"管理后台"入口
const hasAdminShell = computed(() => session.value.menuPaths.some((p) => !p.startsWith('/app')))

const currentUser = computed(() => session.value.profile)

const userAvatarFallback = computed(() => {
  const s = currentUser.value.nickname || currentUser.value.username || 'U'
  return String(s).slice(0, 1).toUpperCase()
})

const currentTitle = computed(() => route.meta.title || '知识库')

function isActive(path) {
  if (path === '/app') return route.path === '/app'
  return route.path.startsWith(path)
}

function goSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/app/learn', query: kw ? { q: kw } : {} })
}

function goAdmin() {
  router.push('/')
}

// hover 停 300ms 才弹级联：太短的话鼠标只是路过就闪出一片菜单。
// 拖拽期间一律不弹——拖卡片时鼠标会经过侧边栏，弹级联会和拖拽浮树打架。
// 没有下级的目录不弹：弹出来是个空壳。那里右侧给的是 ＋，建子目录走那条路。
function onCatEnter(c, e) {
  if (dnd.live || !hasKids(c.id)) return
  clearTimeout(hoverTimer)
  clearTimeout(closeTimer)
  const top = e.currentTarget.getBoundingClientRect().top
  hoverTimer = setTimeout(() => {
    cascadeFor.value = c.id
    cascadeTop.value = top
  }, 300)
}

function onCatLeave() {
  scheduleCloseCascade()
}

// 鼠标进到弹出的级联里：取消"马上要关"。侧边栏那一行和菜单之间隔着一道缝，
// 鼠标走过去必然先触发一次 leave，没有这个口子就是"一碰就散"。
function onCascadeEnter() {
  clearTimeout(closeTimer)
}

// 整条级联只有一个开关，要关它的理由全汇到这一处。
// 之前 cascadeFor 只在 goCategory / openChildCreate 里被清，onCatLeave 只清了
// hoverTimer——打开和关闭是两条互不相干的路径，中间没人管，于是弹出来就再也
// 关不掉，只能靠点导航或刷新。
function closeCascade() {
  clearTimeout(hoverTimer)
  clearTimeout(closeTimer)
  cascadeFor.value = null
}

// 离开侧边栏那一行不等于不要了：鼠标常常只是斜着划向菜单。留一点缓冲再关。
function scheduleCloseCascade() {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(closeCascade, 220)
}

function goCategory(c) {
  closeCascade()
  router.push({ path: '/app/learn', query: { category: c.id } })
}

// 级联里某一行上的 ＋：父级就是这一行自己
function openChildCreate(c) {
  closeCascade()
  childParentId.value = c.id
  childParentLabel.value = c.name
  childCreateVisible.value = true
}

async function handleLogout() {
  clearAuth()
  router.push({ name: 'Login' })
}

// 落点全站只有拖拽浮出的那棵树一处（CategoryTree 的 mode="drop"），它直接调 store 的
// dropOver/dropTo。侧边栏、药丸都不再当落点：侧边栏的目录项是导航，「待归类」药丸是纯筛选。
onMounted(async () => {
  // 菜单/资料和业务数据并行拉：菜单决定左侧导航，数据决定目录树
  const [s] = await Promise.all([
    bootstrapSession(),
    ensureTopicsLoaded(),
    ensureCategoriesLoaded()
  ])
  session.value = s
})
</script>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  background: var(--cc-bg);
  overflow: hidden;
}

/* ---------- 左侧栏 ---------- */
.app-side {
  width: 232px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--cc-surface);
  border-right: 1px solid var(--cc-border);
  padding: 20px 12px 12px;
}

.app-side__brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 8px 20px;
}
.app-side__logo {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--cc-logo-gradient);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-side__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: 0.3px;
}

.app-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.app-nav__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 7px;
  color: var(--cc-text-2);
  font-size: 14px;
  text-decoration: none;
  transition: var(--cc-transition);
}
.app-nav__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.app-nav__item.is-active {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
.app-nav__icon {
  font-size: 15px;
}

.app-side__section {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.app-side__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 6px;
  font-size: 12px;
  color: var(--cc-text-3);
  font-weight: 500;
}
.app-side__add {
  border: none;
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  padding: 0 4px;
  border-radius: 4px;
}
.app-side__add:hover {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.app-cats {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
  max-height: 40vh;
}
.app-cats__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  color: var(--cc-text-2);
  font-size: 13px;
  text-decoration: none;
}
.app-cats__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.app-cats__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
/* 名字吃掉全部剩余宽度，数字和图标才贴到同一条右边上。
   不给名字 flex 的话，space-between 会把余量均分给三个子元素——
   "JAVA 7" 的 7 贴着名字、"计算机网络 2" 的 2 却被推到更右，两列对不齐。 */
.app-cats__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 右对齐 + 最小宽度：一位数和两位数右边沿对齐，图标那一列也不会左右跳 */
.app-cats__count {
  font-size: 11px;
  color: var(--cc-text-4);
  flex-shrink: 0;
  min-width: 14px;
  text-align: right;
}
.app-cats__more {
  font-size: 11px;
  color: var(--cc-text-4);
  flex-shrink: 0;
  cursor: pointer;
}
.app-cats__more:hover {
  color: var(--cc-primary);
}
.app-side__empty {
  padding: 4px 10px;
  margin: 0;
  font-size: 12px;
  color: var(--cc-text-4);
}

.app-side__spacer {
  flex: 1;
}

.app-side__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 8px 4px;
  border-top: 1px solid var(--cc-surface-2);
}
.app-side__username {
  flex: 1;
  font-size: 13px;
  color: var(--cc-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.app-side__logout {
  border: none;
  background: transparent;
  color: var(--cc-text-4);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
}
.app-side__logout:hover {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

/* ---------- 右侧 ---------- */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-top {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid var(--cc-border);
}
.app-top__crumb {
  font-size: 15px;
  font-weight: 600;
  color: var(--cc-text-1);
}
.app-top__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.app-search {
  width: 220px;
}
.app-search :deep(.el-input__wrapper) {
  border-radius: 16px;
  box-shadow: none;
  background: var(--cc-surface-2);
}
.app-top__icon-btn {
  border: none;
  background: transparent;
  color: var(--cc-icon);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--cc-radius-sm);
  display: flex;
}
.app-top__icon-btn:hover {
  background: var(--cc-surface-2);
  color: var(--cc-primary);
}

/* 内容区：居中窄栏——这是"产品"的视觉签名，不是"后台" */
.app-content {
  flex: 1;
  overflow-y: auto;
  padding: 28px 24px 64px;
}
.app-content > * {
  max-width: 920px;
  margin: 0 auto;
}
</style>
