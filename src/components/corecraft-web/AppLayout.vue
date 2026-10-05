<template>
  <!-- 收起时侧边栏宽度归零，.app-main 是 flex:1 会自动铺满。
       目录也跟着一起看不见——按约定不额外做目录入口，要切目录就点顶栏这个按钮展开。 -->
  <div class="app-shell" :class="{ 'is-side-collapsed': sideCollapsed }">
    <!-- 左侧栏：品牌 + 导航 + 目录。沉浸模式下整个不渲染（它就是"壳"的一部分）。 -->
    <aside v-if="!immersive.on" class="app-side">
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
            <button class="app-side__add" aria-label="新建一级目录" @click="openCreateRoot">＋</button>
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
            <!-- 行内容和弹层里的行完全一样：名称 · 数字 · ⋯。
                 之前这里是「有下级给 › / 没下级给 ＋」二选一，结果"给一个已经有子目录的
                 一级目录再建一个兄弟"这条路整个没了——有 ＋ 的时候弹层里又没有这一级自己。 -->
            <span class="app-cats__name">{{ c.name }}</span>
            <span class="app-cats__count">{{ categoryCounts[c.id] || 0 }}</span>
            <CategoryRowMenu :category="c" @add="openChildCreate" @rename="openRename" />
          </RouterLink>
        </div>
        <p v-else class="app-side__empty">还没有目录</p>
      </div>

      <div class="app-side__spacer"></div>

      <!-- 底部：用户。启动加载失败时这一格换成「加载失败 + 重试」——
           位置不变、文案如实说、按钮形态不变，只是从"谎称未登录"变成"让你重来一次"。
           为什么不能显示「未登录」：真没登录时 401 拦截器已经 clearAuth + 弹到登录页了，
           能站在这里看见侧边栏就说明 token 是好的（建目录、查目录都调得通，
           那些请求不走 bootstrapSession）。那一格实际含义是"资料没加载出来"。 -->
      <div class="app-side__user">
        <template v-if="loadFailed">
          <span class="app-side__failed">加载失败</span>
          <el-tooltip content="重新加载" placement="top">
            <button class="kb-icon-btn" aria-label="重新加载" @click="loadShell">
              <el-icon><Refresh /></el-icon>
            </button>
          </el-tooltip>
        </template>
        <template v-else>
        <el-avatar :size="28" :src="currentUser.avatar || undefined" class="user-avatar">
          {{ userAvatarFallback }}
        </el-avatar>
        <span class="app-side__username">{{ currentUser.nickname || currentUser.username || '未登录' }}</span>
        <el-tooltip content="退出登录" placement="right">
          <button class="app-side__logout" aria-label="退出登录" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
          </button>
        </el-tooltip>
        </template>
      </div>
    </aside>

    <!-- 右侧：顶栏 + 内容 -->
    <div class="app-main">
      <header v-if="!immersive.on" class="app-top">
        <div class="app-top__left">
          <!-- 纯图标按钮必须自带可访问名：tooltip 只管看，屏幕阅读器读的是 aria-label -->
          <el-tooltip :content="sideToggleLabel" placement="bottom">
            <button class="app-top__icon-btn" :aria-label="sideToggleLabel" @click="toggleSide">
              <el-icon><component :is="sideCollapsed ? Expand : Fold" /></el-icon>
            </button>
          </el-tooltip>
          <div class="app-top__crumb">
            {{ currentTitle }}
          </div>
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
            <button class="app-top__icon-btn" aria-label="管理后台" @click="goAdmin">
              <el-icon><Setting /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <!-- is-immersive 这个类同时是给全页 CSS 的开关标记：
           编辑器撑满（MilkdownEditorBody 的全局块）和首页只留录入区都认它。 -->
      <main class="app-content" :class="{ 'is-immersive': immersive.on }">
        <RouterView v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" :key="route.name" />
          </keep-alive>
        </RouterView>
      </main>
    </div>

    <!-- 入口一（放）：拖起卡片时浮出完整目录树当落点，跟着起拖位置不动、拖完即消失。
         位置在这里算（指针右下，贴到视口边缘时翻到另一侧），样式在 CategoryTree。

         拖拽幽灵也在这里渲染（不挂到 body、不克隆、不依赖任何第三方库）：
         之前交给 SortableJS 的 fallback，它的 _appendGhost() 会给克隆体写**行内**
         width/height（= 源卡片尺寸）并在每次移动时重写，CSS 改不动，要改就得每帧
         跟库对着干。而我们要的幽灵是"一行标题"，跟库的模型本来就对不上。 -->
    <CategoryTree
      v-if="dnd.live"
      mode="drop"
      :items="rootCategories"
      :style="dropTreeStyle"
    />

    <div
      v-if="dnd.live && !dnd.picking && dnd.label"
      class="dnd-ghost"
      :style="{ left: dnd.x + 'px', top: dnd.y + 'px' }"
    >{{ dnd.label }}</div>

    <!-- 触屏点选模式：点浮层外面 = 取消。PC 拖拽没有这一层（松手就自动收了），
         但触屏是"点了⋯弹出来一个浮层"，跟点 popover 的心智一样，
         必须能点外面关掉，否则用户不知道怎么退出去。 -->
    <div v-if="dnd.live && dnd.picking" class="pick-scrim" @click="dndReset" />

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
      @rename="openRename"
      @enter="onCascadeEnter"
      @close="closeCascade"
    />

    <!-- 「给目录起个名」只有一个弹窗：新建一级 / 新建子级 / 改名都是它，
         靠 mode + initialId + initialName 区分。三个弹窗实例就是同一个输入框写三遍。 -->
    <CategoryNameDialog
      v-model="nameDialog.visible"
      :mode="nameDialog.mode"
      :parent-id="nameDialog.parentId"
      :parent-label="nameDialog.parentLabel"
      :initial-name="nameDialog.initialName"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { SwitchButton, Search, Setting, Expand, Fold, Refresh } from '@element-plus/icons-vue'
import { clearAuth } from '../../utils/auth'
import { flattenShellNav, getIconComponent } from '../../utils/menu'
import { hasAdminMenus } from '../../utils/session'
import CategoryNameDialog from '../../views/corecraft-web/CategoryNameDialog.vue'
import CategoryRowMenu from '../../views/corecraft-web/CategoryRowMenu.vue'
import CategoryTree from '../../views/corecraft-web/CategoryTree.vue'
import { loadShellData } from '../../views/corecraft-web/useShellData'
import { useImmersive } from '../../views/corecraft-web/useImmersive'
import {
  getChildCategories, hasChildCategories,
  getCategoryCountMap, dnd, dndReset, ROOT_CATEGORY
} from '../../views/corecraft-web/useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

// 会话数据放 ref：localStorage 不是响应式的，直接读不会触发重渲染
const session = ref({ menuTree: [], menuPaths: [], profile: {} })
const searchKeyword = ref('')

// 侧边栏收起状态。存 localStorage 而不是只放内存：收起是有代价的——目录树跟着一起看不见，
// 一次偶然点错不该让下次打开还是收着的。
// 不写进 utils/auth.js：那是登录态，登出时 clearAuth 会 localStorage.clear() 把它一起抹掉，
// 界面偏好不该跟账号走。
const SIDE_COLLAPSED_KEY = 'app-side-collapsed'
const sideCollapsed = ref(localStorage.getItem(SIDE_COLLAPSED_KEY) === '1')

// 图标按钮没有文字可读，名字要同时喂给 tooltip 和 aria-label，只留这一处来源
const sideToggleLabel = computed(() => (sideCollapsed.value ? '展开侧边栏' : '收起侧边栏'))

function toggleSide() {
  sideCollapsed.value = !sideCollapsed.value
  localStorage.setItem(SIDE_COLLAPSED_KEY, sideCollapsed.value ? '1' : '0')
}

// hover 级联的触发状态
const cascadeFor = ref(null)
const cascadeTop = ref(0)
let hoverTimer = null
let closeTimer = null

// 「给目录起个名」弹窗的全部参数。三个入口（建一级 / 建子级 / 改名）共用这一个状态，
// 拆成三个 ref 就要各自记一份 visible，改一个入口的判断就得同步另外两个。
const nameDialog = reactive({
  visible: false,
  mode: 'create',
  parentId: ROOT_CATEGORY,
  parentLabel: '',
  initialName: ''
})

const navItems = computed(() =>
  flattenShellNav('app', session.value.menuTree, session.value.menuPaths)
)
// 侧边栏只渲染一级（parentId = 0），层级交给 hover 级联
const rootCategories = computed(() => getChildCategories(ROOT_CATEGORY))
const categoryCounts = computed(() => getCategoryCountMap())

const childrenOf = (id) => getChildCategories(id)
const hasKids = (id) => hasChildCategories(id)
const isCurrentRoot = (id) => Number(route.query.category) === id

// 有管理后台菜单的（超管）才给"管理后台"入口。判据与 Layout 的 hasAppShell 共用 hasAdminMenus
// —— 原来这里自己写了一遍 `menuPaths.some(p => !p.startsWith('/app'))`，和 session.js 里的
// hasAdminMenus() 一字不差；Layout 那侧早就调共享函数了，只剩这侧是副本。
const hasAdminShell = computed(() => hasAdminMenus())

const currentUser = computed(() => session.value.profile)

// **启动加载失败 ≠ 没登录。** 真的没登录时 401 拦截器已经 clearAuth + 弹到登录页了，
// 能站在这里看见侧边栏就说明 token 是好的（建目录、查目录都调得通，那些请求不走
// bootstrapSession）。所以这一格显示「未登录」是在撒谎——它实际含义是"资料没加载出来"。
// 失败就如实说"加载失败"并给一个重试，位置不变，不新增 UI 元素。
const loadFailed = ref(false)

async function loadShell() {
  loadFailed.value = false
  try {
    session.value = await loadShellData()
  } catch {
    loadFailed.value = true
  }
}

const userAvatarFallback = computed(() => {
  const s = currentUser.value.nickname || currentUser.value.username || 'U'
  return String(s).slice(0, 1).toUpperCase()
})

const currentTitle = computed(() => route.meta.title || '知识库')

// 沉浸模式：把侧边栏和顶栏收起来，只留内容。判据只有这一个函数，
// AppLayout、详情页、首页都从这儿取（见 useImmersive.js）。
const immersive = useImmersive()

// Esc 退出沉浸模式。挂在 window 上而不是页面上：全屏时壳已经没了，
// 页面上只剩内容，没有一个可以承载键盘事件的固定按钮区。
function onKeydown(e) {
  // immersive 是 reactive 包着的，.on 在脚本里也是布尔值，不要再写 .value
  if (e.key === 'Escape' && immersive.on) immersive.exit()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

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
// 没有下级的目录不弹：弹出来是个空壳。建子目录不靠它——每一行的 ⋯ 菜单里就有，
// 空目录照样能长出下级。
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

// 三个入口都收在这里：建一级（侧边栏标题的 ＋）、建子级（任一行 ⋯ 的"新增子目录"）、
// 改名（任一行 ⋯ 的"重命名"）。都先关掉 hover 弹层，免得弹层停在一棵待改的树上。
function openCreateRoot() {
  closeCascade()
  nameDialog.mode = 'create'
  nameDialog.parentId = ROOT_CATEGORY
  nameDialog.parentLabel = ''
  nameDialog.initialName = ''
  nameDialog.visible = true
}

function openChildCreate(c) {
  closeCascade()
  nameDialog.mode = 'create'
  nameDialog.parentId = c.id
  nameDialog.parentLabel = c.name
  nameDialog.initialName = ''
  nameDialog.visible = true
}

function openRename(c) {
  closeCascade()
  nameDialog.mode = 'rename'
  nameDialog.parentLabel = c.name
  // 后端按 (userId, oldName) 定位，改名的起点必须是当前真名
  nameDialog.initialName = c.name
  nameDialog.visible = true
}

async function handleLogout() {
  clearAuth()
  router.push({ name: 'Login' })
}

// 落点浮层的位置：**锚在起拖那一刻**（dnd.ax / dnd.ay），整个拖拽过程不动。
// 贴到视口右/下边缘就翻到另一侧，免得浮层一半露在屏幕外。
// 树高按 CategoryTree 的 max-height 估。
//
// 为什么不让它跟着指针：浮层一旦跟着飘，想把卡片拖进某一行就得先追着浮层挪，
// 而且指针压在浮层上会挡住 elementFromPoint 的命中（幽灵已经是 pointer-events:none
// 解决了这个问题，浮层没有——它得能接收命中）。固定之后它就是个安静的靶子。
const TREE_W = 232
const TREE_H = 260
const DROP_GAP = 16
const dropTreeStyle = computed(() => {
  const flipX = dnd.ax + DROP_GAP + TREE_W > window.innerWidth
  const flipY = dnd.ay + DROP_GAP + TREE_H > window.innerHeight
  return {
    left: `${flipX ? dnd.ax - DROP_GAP - TREE_W : dnd.ax + DROP_GAP}px`,
    top: `${Math.max(8, flipY ? dnd.ay - TREE_H - DROP_GAP : dnd.ay + DROP_GAP)}px`
  }
})

// 落点全站只有拖拽浮出的那棵树一处（CategoryTree 的 mode="drop"），它只渲染
// data-cat-id 标记，命中由 store 的 hitCategory() 用 elementFromPoint 算。
// 侧边栏、药丸都不再当落点：侧边栏的目录项是导航，「待归类」药丸是纯筛选。
onMounted(async () => {
  // 菜单/资料和业务数据并行拉：菜单决定左侧导航，数据决定目录树。
  // 三个 ensure 收敛在 loadShellData 一处（原来这里和 AppHomeView 各写了一遍）。
  await loadShell()
})
</script>

<style scoped>
/* 拖拽幽灵：跟着指针的一行标题。
   pointer-events: none 是硬要求——它压在指针正下方，命中检测靠 elementFromPoint，
   被它挡住就永远命不中任何目录行。 */
.dnd-ghost {
  position: fixed;
  z-index: 60;
  pointer-events: none;
  transform: translate(-50%, -50%) rotate(2deg);
  max-width: 200px;
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid var(--cc-primary);
  background: var(--cc-surface);
  box-shadow: 0 8px 24px rgb(31 35 41 / 18%);
  font-size: 13px;
  line-height: 1.5;
  color: var(--cc-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.95;
}
/* 触屏点选模式的遮罩：透明，只为接住"点外面取消"。
   z-index 19 —— 刚好在落点树（20）之下，所以树上的行照常可点。 */
.pick-scrim {
  position: fixed;
  inset: 0;
  z-index: 19;
}
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
  /* 收起时宽度归零，必须裁掉内容，否则子元素会溢出到主区上面。
     收起用宽度过渡而不是 v-if：目录树和 hover 级联都有各自的展开/关缓冲，
     节点直接被销毁会让这些状态跟着重来一次。 */
  overflow: hidden;
  transition: width 0.2s, padding 0.2s;
}
.app-shell.is-side-collapsed .app-side {
  width: 0;
  padding-left: 0;
  padding-right: 0;
  border-right-width: 0;
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
  /* 行尾的「⋯」默认不显形，悬停/键盘聚焦这一行时才露出来。
     一排行里全是点会像一堆图标噪点，而且点它的人只有少数几个。
     focus-within 一起带上：opacity:0 的按钮仍然能被 Tab 选中，
     只挂 :hover 的话键盘用户会 Tab 到一个看不见的按钮上。 */
  --row-menu-show: 0;
}
.app-cats__item:hover,
.app-cats__item:focus-within {
  --row-menu-show: 1;
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
/* 启动加载失败时顶掉用户名那一格。文案用警示色而不是灰——"加载失败"是需要
   用户点一下重试的状态，不该和"未设置昵称"那种中性缺省长得一样。 */
.app-side__failed {
  flex: 1;
  font-size: 13px;
  color: var(--cc-danger);
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
.app-top__left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
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

/* 内容区：居中窄栏——这是"产品"的视觉签名，不是"后台"。
   宽度分两档（base.css 的 --cc-content-w / --cc-content-w-reading）。
   1280 只在约 1330px 以上的视口才咬：1440 / 1536 这类笔记本已经铺满，
   1920 收起侧栏时左右留白从 476px 收到 296px。
   max-width 用 :where() 包掉权重，让 AppTopicDetailView 的 .detail 能覆盖成阅读档：
   子组件根节点同时带父组件的 scope id，不包零的话本选择器 (0,3,0) 会压过子页的 (0,2,0)。 */
.app-content {
  flex: 1;
  overflow-y: auto;
  padding: 28px 24px 64px;
}
.app-content > * {
  margin: 0 auto;
}
.app-content > :where(*) {
  max-width: var(--cc-content-w);
}
</style>
