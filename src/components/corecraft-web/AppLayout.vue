<template>
  <!-- 收起时侧边栏宽度归零，.app-main 是 flex:1 会自动铺满。
       目录也跟着一起看不见——按约定不额外做目录入口，要切目录就点顶栏这个按钮展开。 -->
  <div class="app-shell" :class="{ 'is-side-collapsed': sideCollapsed }">
    <!-- 左侧栏：品牌 + 导航 + 目录。沉浸模式下整个不渲染（它就是"壳"的一部分）。

         `@click="closeCascade"` 挂**整条侧边栏**而不是目录行：#111 之后遮罩只盖主区
         （`left: var(--w-side)`，因为侧边栏还得留着当拖拽落点区），"点侧边栏也收树"
         这件事就得自己管。挂容器而不是行，是因为点**行之间的空白**同样该收——
         按行挂只覆盖得到目录名那几行，空白处点了没反应。
         目录行的点击靠冒泡到这里，本来就不用再挂一遍。

         行上两个控件各自 `stop`，不会误触发：
         - `›` 展开/收起的是它自己（#108：展开和进入是两件事）
         - `⋯` 只开菜单、不关树（#108：用户可能只是看了一眼菜单又缩回去） -->
    <aside v-if="!immersive.on" class="app-side" @click="closeCascade">
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
           层级不在这里常驻——264px 撑不住缩进，而层级只在两件事上才需要被看见：
           放东西（拖拽浮出完整树）和进深层（点 › 展开整棵子树）。

           这一块有两个落点角色：
           - **每一行**（data-cat-id）：拖到那一行 = 挪到那个目录下
           - **整块的空白处**（data-cat-root）：拖上去 = 挪成一级目录
           两者靠 hitCategory 的判断顺序分开（先找行、再退到块）。 -->
      <div
        class="app-side__section"
        data-cat-root
        :class="{ 'is-drop': dnd.over === ROOT_CATEGORY && dnd.overOk, 'is-bad': dnd.live && dnd.over === ROOT_CATEGORY && !dnd.overOk }"
      >
        <div class="app-side__section-title">
          <span>目录</span>
          <!-- 一个入口：**生成知识包**（主题 → 目录骨架 → 逐目录写知识点）。
               生成的目录自成一体、放在第一层；挂到哪、怎么挪，用户自己在侧边栏拖。
               为什么叫"生成"不叫"导入"：这套体系还没长出来的时候，"生成"才是
               用户真正缺的那件事（"导入"的前提是先有东西可导）。 -->
          <el-tooltip content="生成知识包" placement="right">
            <button class="app-side__add" aria-label="生成知识包" @click="openTreeDialog">
              <el-icon><MagicStick /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="新建一级目录" placement="right">
            <button class="app-side__add" aria-label="新建一级目录" @click="openCreate(ROOT_CATEGORY, '')">＋</button>
          </el-tooltip>
        </div>
        <div v-if="rootCategories.length" class="app-cats">
          <RouterLink
            v-for="c in rootCategories"
            :key="c.id"
            :to="categoryLink(c.id)"
            class="app-cats__item"
            :class="{
              'is-on': isCurrentRoot(c.id),
              'is-drop': dnd.over === c.id && dnd.overOk,
              'is-bad': dnd.live && dnd.over === c.id && !dnd.overOk
            }"
            :data-cat-id="c.id"
            :data-cat-name="c.name"
            :draggable="false"
            @pointerdown="onCatPointerDown"
          >
            <span class="app-cats__name">{{ c.name }}</span>
            <span class="app-cats__count">{{ categoryCounts[c.id] || 0 }}</span>
            <CategoryRowMenu :category="c" @add="(c) => openCreate(c.id, c.name)" @rename="openRename" @points="openPointsDialog" />
            <!-- 展开子树的入口。**没有下级就不给箭头**：弹出来是个空壳，
                 建子目录靠这一行自己的「⋯」就够了。
                 `@click.stop.prevent`：外层是 RouterLink，不拦住的话点箭头会顺带
                 导航进这个目录——展开和进入是两件事，箭头只干前者。 -->
            <button
              v-if="hasKids(c.id)"
              type="button"
              class="app-cats__expand"
              :aria-label="`展开 ${c.name} 的下级目录`"
              :aria-expanded="cascadeFor === c.id"
              @click.stop.prevent="toggleCascade(c, $event)"
            ><el-icon><ArrowRight /></el-icon></button>
          </RouterLink>
        </div>
        <!-- ⚠️ **「没有目录」这句话只在真的加载完之后才成立**。
             加载中就说它，是句假话（用户明明有目录），看着像"目录被清空了"。
             所以分「加载中」「加载失败」「真的没有」三种情况。 -->
        <p v-else-if="categoriesState === 'loading'" class="app-side__empty">加载中…</p>
        <p v-else-if="categoriesState === 'failed'" class="app-side__empty">加载失败</p>
        <p v-else class="app-side__empty">没有目录</p>
      </div>

      <div class="app-side__spacer"></div>

      <!-- 底部：用户。**会话拉失败时这一格换成「加载失败 + 重试」**——
           位置不变、文案如实说、按钮形态不变，只是从"谎称未登录"变成"让你重来一次"。
           为什么不能显示「未登录」：真没登录时 401 拦截器已经 clearAuth + 弹到登录页了，
           能站在这里看见侧边栏就说明 token 是好的（建目录、查目录都调得通，
           那些请求不走 bootstrapSession）。那一格实际含义是"资料没加载出来"。
           判断依据是**共享的** sessionState（两个壳同一份，见 utils/session.js）。 -->
      <div class="app-side__user">
        <template v-if="sessionState === 'failed' || shellDataFailed">
          <span class="app-side__failed">加载失败</span>
          <el-tooltip content="重新加载" placement="top">
            <button class="kb-icon-btn" aria-label="重新加载" @click="loadShell">
              <el-icon><Refresh /></el-icon>
            </button>
          </el-tooltip>
        </template>
        <!-- 用户菜单：**两个壳共用这一份**（见 components/UserMenu.vue）。
             菜单项和管理后台一样：编辑个人信息 / 修改密码 / 退出登录。
             「AI 能力」不在这里 —— 它在右边那个齿轮里（见下面）。

             **退出登录不用监听**：确认框、清登录态、提示、跳登录页都在
             UserMenu 里做完了（两个壳共用那一份）；这个壳退出时没有要额外清的
             ——数据归 store 的 onAuthChange、AI 配置归 useAi.js 的 onAuthChange。

             它**不装在 v-if / v-else 里**：加载失败时那一条是"重试"不是"用户"，
             但侧边栏照样在、宽度和密度照样该能调——**没有理由让设置跟着数据一起消失。** -->
        <UserMenu
          class="app-side__user-menu"
          :user="currentUser"
        />

        <!-- 设置（一个入口，两项）。**不塞进顶栏**：它管的是侧边栏和首页这两块，
             放在侧边栏底栏才是"哪儿的问题回哪儿改"。admin 壳压根没有侧边栏，没有对应物。
             **放在用户菜单之后**：`.app-side__user-menu` 是 `flex: 1`，撑满剩下的
             宽度，齿轮自然落到最右边——放它前面就会被顶到左边去。

             ①「界面设置」：侧边栏 / 首页 / 知识点 / 详情四组**合并到这一个面板**，顶部一条切换栏换组（用户要求）。
                之前首页那组挂在首页自己的左下角，形态一样、组件同一个，两处入口却是两处：
                同一个 `PrefsPanel` 摆两个位置，用户得知道"哪条调哪个"。合并后只有一个入口。
             ②「AI 能力」：配置用哪些模型 API（地址 / 模型 / Key）。原来它在用户菜单里 ——
                那是"关于我"的位置，放一个系统级配置不合适；它是设置，该在设置里。

             ⚠️ **齿轮就是偏好面板自己的触发器**（面板里那个齿轮按钮）。
             曾经试过把齿轮改成 el-dropdown、面板用虚拟触发器挂在它上面 ——
             **同一个元素既是下拉的触发器、又是浮层的触发器，点一下两个都响应**：
             面板自己冒出来（还没点「界面设置」），而且虚拟定位没解析、跑到左上角去了。
             所以齿轮保持"一点就弹偏好面板"，AI 能力作为面板顶部的一个入口。 -->
        <PrefsPanel :groups="['side', 'home', 'learn', 'detail']" label="界面设置">
          <template #extra>
            <button type="button" class="prefs__link" @click="goAiSettings">
              <span>AI 能力</span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </template>
        </PrefsPanel>
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

    <!-- 入口一（放）：拖起**知识点卡片**时浮出完整目录树当落点，跟着起拖位置不动、拖完即消失。
         位置在这里算（指针右下，贴到视口边缘时翻到另一侧），样式在 CategoryTree。

         **拖目录时不弹这个浮层**（catId != null 那半边）：那时用户正按着 nav 树里的一行，
         要放的地方就在那棵树和侧边栏目录区上。再弹一棵过来，同一个指针下有两个"树"在抢
         落点，指针压在上面那棵上时 elementFromPoint 命中的还是它——看着没反应。

         拖拽幽灵也在这里渲染（不挂到 body、不克隆、不依赖任何第三方库）：
         之前交给 SortableJS 的 fallback，它的 _appendGhost() 会给克隆体写**行内**
         width/height（= 源卡片尺寸）并在每次移动时重写，CSS 改不动，要改就得每帧
         跟库对着干。而我们要的幽灵是"一行标题"，跟库的模型本来就对不上。 -->
    <CategoryTree
      v-if="dnd.live && dnd.catId == null"
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
    <div v-if="dnd.live && dnd.picking" class="scrim pick-scrim" @click="dndReset" />

    <!-- 入口二（进）：点一级目录行尾的 › 展开它整棵子树。
         不用一层一层再点开——层级在"要挑一个深层目录"时才需要被完整看见，
         一次给全比让人自己逐层点开省事。
         遮罩 z 39 压在弹层（40）之下、**只盖主区不盖侧边栏**（见 .cascade-scrim）：
         点主区 = 关；侧边栏那边由 `<aside class="app-side">` 自己的
         @click="closeCascade" 管，它同时还得留着当拖拽落点区，不能被遮罩吃掉。 -->
    <div v-if="cascadeFor != null" class="scrim cascade-scrim" @click="closeCascade" />
    <CategoryTree
      v-if="cascadeFor != null"
      :key="cascadeFor"
      mode="nav"
      :items="childrenOf(cascadeFor)"
      :anchor-top="cascadeTop"
      @pick="goCategory"
      @pick-topic="goTopic"
      @add="(c) => openCreate(c.id, c.name)"
      @rename="openRename"
      @points="openPointsDialog"
    />

    <!-- 「给目录起个名」只有一个弹窗：新建一级 / 新建子级 / 改名都是它，
         靠 mode + initialId + initialName 区分。三个弹窗实例就是同一个输入框写三遍。 -->
    <CategoryNameDialog
      v-model="nameDialog.visible"
      :mode="nameDialog.mode"
      :parent-id="nameDialog.parentId"
      :parent-label="nameDialog.parentLabel"
      :initial-name="nameDialog.initialName"
    :initial-id="nameDialog.initialId"
    />

    <!-- 知识包生成：主题 → 目录骨架 → 逐目录写知识点。
         也可以从某个目录的「⋯」进"生成知识点"（目录现成，只写内容）。 -->
    <CategoryTreeDialog
      v-model="treeDialog.visible"
      :mode="treeDialog.mode"
      :category="treeDialog.category"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, RouterLink, RouterView } from 'vue-router'
import { Search, Setting, Expand, Fold, Refresh, ArrowRight, MagicStick } from '@element-plus/icons-vue'
import { categoryLink, topicLink } from '../../utils/categoryNav'
import { flattenShellNav, getIconComponent } from '../../utils/menu'
import { hasAdminMenus, bootstrapSession, sessionState, sessionProfile } from '../../utils/session'
import { applyUiPrefs, clearUiPrefs } from '../../utils/uiPrefs'
import UserMenu from '../UserMenu.vue'
import PrefsPanel from './PrefsPanel.vue'
import CategoryNameDialog from '../../views/corecraft-web/CategoryNameDialog.vue'
import CategoryTreeDialog from '../../views/corecraft-web/CategoryTreeDialog.vue'
import CategoryRowMenu from '../../views/corecraft-web/CategoryRowMenu.vue'
import CategoryTree from '../../views/corecraft-web/CategoryTree.vue'
import { useTopicDrag } from '../../views/corecraft-web/useTopicDrag'
import { loadShellData } from '../../views/corecraft-web/useShellData'
import { useImmersive } from '../../views/corecraft-web/useImmersive'
import {
  getChildCategories, hasChildCategories, categoriesState,
  getCategoryCountMap, dnd, dndReset, ROOT_CATEGORY
} from '../../views/corecraft-web/useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

// 会话数据放 ref：localStorage 不是响应式的，直接读不会触发重渲染
const session = ref({ menuTree: [], menuPaths: [], profile: {} })
// 初值取自 URL：带着 ?q= 进来的（刷新 / 分享链接）框里就有那个词，见下面那段注释。
const searchKeyword = ref(String(route.query.q || ''))

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

// 目录树弹层的开合状态。展开靠点行尾的 ›（见 toggleCascade），关靠点遮罩 / Esc 之外的
// 那几条业务路径（进目录 / 新建子级 / 改名都会顺手关掉）。
const cascadeFor = ref(null)
const cascadeTop = ref(0)

/**
 * 目录生成弹窗。
 * `mode = 'pack'` 生成一整套（主题 → 骨架 → 知识点）；`mode = 'points'` 给某个目录写知识点。
 */
const treeDialog = reactive({ visible: false, mode: 'pack', category: null })

/**
 * 侧边栏每个一级目录**也是拖拽源**：把它拖到弹层里的某一行 = 挪到那个目录下，
 * 拖到目录区空白处 = 挪成一级。
 *
 * **模板里那三个 data-* / draggable="false" / @pointerdown 都缺一不可**：
 * - 行上挂 `data-cat-id` / `data-cat-name` 是为了共用一个 handler 就能认出是哪一行
 * - **`draggable="false"` 是关键**：这些行是 `RouterLink`（`<a href>`），**`<a>` 默认
 *   `draggable=true`**，一按就启动浏览器原生拖拽——拖起来是个带 URL 的灰色 tooltip，
 *   pointerdown 收不到、我们这套完全不触发。2026-10-07 实机就撞了这个：
 *   "脱不进去"，实际从头到尾是浏览器在拖链接。
 */
const { onPointerDown: onCatPointerDown } = useTopicDrag((e) => {
  const id = Number(e.currentTarget?.dataset?.catId)
  if (!id) return null
  return { what: { catId: id }, label: e.currentTarget.dataset.catName || '' }
})

// 「给目录起个名」弹窗的全部参数。三个入口（建一级 / 建子级 / 改名）共用这一个状态，
// 拆成三个 ref 就要各自记一份 visible，改一个入口的判断就得同步另外两个。
const nameDialog = reactive({
  visible: false,
  mode: 'create',
  parentId: ROOT_CATEGORY,
  parentLabel: '',
  initialName: '',
  // 改名时被改的是哪个目录。后端按 id 定位：名字是可变字段，
  // 允许同名之后按名字根本认不出要改哪一个（initialName 只用来回显）。
  initialId: null
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

const currentUser = sessionProfile

/**
 * 业务数据（知识点 / 目录 / 类型 / AI 配置）拉失败。
 *
 * **和会话的失败分开记**：会话失败 → 用户名都不知道（sessionState 管）；
 * 业务数据失败 → 用户名好好地在那儿，只是侧边栏的目录树是空的。
 * 合成一个状态的话，业务数据一失败连用户名都跟着说"加载失败"——那是把好的说成坏的。
 */
const shellDataFailed = ref(false)

async function loadShell() {
  shellDataFailed.value = false
  // 会话（导航 / 用户名）**先到先显示** —— 它是壳的**第一帧**。
  //
  // 原来是从 loadShellData() 拿的，而那个函数把会话和知识点/目录/类型/AI 配置
  // 绑在**同一个 Promise.all** 上，于是"导航和用户名要等最慢的那个业务接口"：
  // 登录进来底下写着「未登录」、侧边栏空着好几秒。
  //
  // 而**数据其实早就在手**了 —— 路由守卫 await 过 bootstrapSession，这里命中
  // 快照，不会再打接口。守卫等的是"能不能进这个路由"，壳等的是"业务数据齐了没"，
  // 两件事被绑成一件，壳就得等最慢的那个。
  //
  // 失败不用这里自己记会话那份：**sessionState**（utils/session.js，两个壳共用）已经记了 ——
  // 侧边栏那一格就是看它决定显示「加载失败 + 重试」还是用户菜单。
  try {
    session.value = await bootstrapSession()
  } catch {
    return
  }
  // 业务数据在后面拉，不挡壳。store 的三个缓存都是 ref、组件是 computed，到位就自己刷新。
  try {
    await loadShellData()
  } catch {
    // 提示归 request.js（唯一提示出口），这里不重复弹；
    // 目录区自己有「加载失败」状态（见 store 的 categoriesState）。
    shellDataFailed.value = true
  }
}

const currentTitle = computed(() => route.meta.title || '知识库')

// 沉浸模式：把侧边栏和顶栏收起来，只留内容。判据只有这一个函数，
// AppLayout、详情页、首页都从这儿取（见 useImmersive.js）。
const immersive = useImmersive()

// 搜索框跟着 URL 走（单向：URL → 框）。少了这条，**直接打开一个带 `?q=` 的分享链接**
// 会看到列表已经筛过了、搜索框却空空如也 —— 又一处"两边各存一份"。
//
// ⚠️ **只在初始化取一次，不做持续回读**：边打边搜每敲一个字 replace 一次 URL，
// 持续回读就会在用户正敲下一个字的时候把刚敲的抹掉（实测：打 "K8s" 打完框是空的）。
// 写方只有这个框一个，URL 上的 `q` 除了它没人改，所以"取一次"就够了。
// immediate 必须开：**首屏**这一次才是最要紧的那次（带着 ?q= 进来的），
// watch 默认只在"之后变了"才跑，刷新页面它一次都不会跑。
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

// 搜索：写 `?q=`，**保留已有的其它 query**（`category` 等）。
// 原来是 `push({ query: { q: kw } })` —— 只带 q，别的全丢：在「某个目录」下搜一句，
// 跳过去目录筛选就没了，等于"搜索顺手把当前范围清了"。
function goSearch() {
  goSearchWith(searchKeyword.value)
}

// 边打边搜：**只在知识点列表页**生效。
// ⚠️ 详情页（/app/topic/:id）正开着编辑器，输入框每敲一个字就把人拽到列表页，
// 编辑位置会被冲掉 —— 那一页仍然回车才跳。这不是"功能没做全"，是那一页的输入框另有用途。
// 用 replace 而不是 push：逐字搜索每敲一下塞一条浏览历史，回头一次退回去要按几十次。
watch(searchKeyword, (kw) => {
  if (route.path !== '/app/learn') return
  goSearchWith(kw, true)
})

function goSearchWith(raw, silent = false) {
  const kw = String(raw || '').trim()
  const query = { ...route.query, q: kw || undefined }
  const same = String(route.query.q || '') === kw
  if (same) return
  if (silent) router.replace({ path: '/app/learn', query })
  else router.push({ path: '/app/learn', query })
}

function goAdmin() {
  router.push('/')
}

// hover 停 300ms 才弹级联的整套定时器（hoverTimer / closeTimer / onCatEnter /
// onCatLeave / onCascadeEnter / scheduleCloseCascade）在 2026-10-07 全部删掉了：
// 展开改成**点 › 打开、点遮罩关闭**，鼠标移开不再收走弹层。下面只留开合本身。
//
// 没有下级的目录不给箭头（模板里 `v-if="hasKids(c.id)"`），所以这里不用再判一次。
function toggleCascade(c, e) {
  if (cascadeFor.value === c.id) {
    closeCascade()
    return
  }
  cascadeFor.value = c.id
  cascadeTop.value = e.currentTarget.getBoundingClientRect().top
}

// 整条级联只有一个开关，要关它的理由全汇到这一处。
// 之前 cascadeFor 只在 goCategory / openCreate 里被清，onCatLeave 只清了
// hoverTimer——打开和关闭是两条互不相干的路径，中间没人管，于是弹出来就再也
// 关不掉，只能靠点导航或刷新。
function closeCascade() {
  cascadeFor.value = null
}

function goCategory(c) {
  closeCascade()
  router.push(categoryLink(c.id))
}

/**
 * 点树上的知识点叶子 ⇒ 进它的详情页。
 *
 * <p>和 `goCategory` 一样先把弹层关掉：留着弹层的话，新页面上面还挂着一棵
 * 旧树，看着像没跳过去。
 */
function goTopic(t) {
  closeCascade()
  router.push(topicLink(t.id))
}

/**
 * 新建目录。**一级和子级是同一个动作**，只是父目录不同 ——
 * 原来 openCreateRoot / openChildCreate 两个函数体逐行相同，只差父目录。
 *
 * <p>新建的三个入口都收在这一带：建一级（侧边栏标题的 ＋）、建子级（任一行 ⋯ 的
 * "新增子目录"）、改名（任一行 ⋯ 的"重命名"）。都先关掉 hover 弹层，
 * 免得弹层停在一棵待改的树上。
 *
 * @param {number} parentId 父目录 id（一级用 ROOT_CATEGORY）
 * @param {string} parentLabel 父目录名（一级是空串；给对话框显示用）
 */
function openCreate(parentId, parentLabel) {
  closeCascade()
  nameDialog.mode = 'create'
  nameDialog.parentId = parentId
  nameDialog.parentLabel = parentLabel
  nameDialog.initialName = ''
  nameDialog.initialId = null
  nameDialog.visible = true
}

function openRename(c) {
  closeCascade()
  nameDialog.mode = 'rename'
  nameDialog.parentLabel = c.name
  // initialName 只用来回显输入框、判"没改就别提交"；真正发给后端的是 id
  nameDialog.initialName = c.name
  nameDialog.initialId = c.id
  nameDialog.visible = true
}

/**
 * 打开知识包生成弹窗（主题 → 骨架 → 逐目录写知识点）。
 *
 * <p>生成的目录**自成一体、放在第一层**：挂到哪、怎么挪层级，用户自己在侧边栏拖。
 */
function openTreeDialog() {
  closeCascade()
  treeDialog.mode = 'pack'
  treeDialog.category = null
  treeDialog.visible = true
}

/**
 * 给某个**现成的目录**写知识点（目录行「⋯」→「生成知识点」）。
 *
 * <p>材料里会带上这个目录现有的子目录 ⇒ 模型知道哪些领域已经有人管了，
 * 不会把该属于子目录的内容写在这里。
 */
function openPointsDialog(c) {
  closeCascade()
  treeDialog.mode = 'points'
  treeDialog.category = c
  treeDialog.visible = true
}

/**
 * 偏好面板顶部的「AI 能力」入口：跳 AI 设置页。
 *
 * 它是**设置**，不是"关于我"——所以不在用户菜单里，在齿轮的浮层里。
 */
function goAiSettings() {
  router.push({ name: 'AppAiSettings' })
}

/**
 * ⚠️ **这里原来还挂着一份「AI 配置作废」**（退出登录 / 改完密码时清）。
 * 现在归 `useAi.js` 自己订阅 `onAuthChange` 管了：AI 配置是按用户的，
 * 登录态一变就得清，而"登录态变了"这个信号只有一个出处。
 * 挂在这儿只有登出那一条路，**重新登录就漏了** ——
 * 症状是"登进来 AI 开关是关的，刷新一下才对"。
 */

// 落点浮层的位置：**锚在起拖那一刻**（dnd.ax / dnd.ay），整个拖拽过程不动。
// 贴到视口右/下边缘就翻到另一侧，免得浮层一半露在屏幕外。
//
// 树的宽高是 CSS 里 `264px` 算出来的，所以这里**读同一个变量**
// 而不是写死常数——对不上的后果不是"浮层小一点"，是**翻边判断算错**：本来该翻到左边，
// 翻完反而露出一半在屏幕外，而且用户看不出是哪一步错了。
//
// 读 getComputedStyle 而不是用 ref：dnd.ax 每次起拖都会变，这个 computed 天然会重算，
// 用户改完档位再拖也是对的，不需要额外维护一份响应式的缩放比。
//
// 注意 dnd.ax / dnd.ay 和 window.innerWidth **不用乘任何缩放比**：它们是视口坐标，
// 而这个定位写在缩放区之外（弹层 teleport 到 body），两边本来就同一空间。
// 以前这里读 `--ui-scale` 再乘一遍，那个乘数 2026-10-07 已经删掉了（见 utils/uiPrefs.js
// 开头：它乘的是 66 个不相干的数，说不清是什么比例、也调不动单独某一类）。
//
// 浮层的实际尺寸**读 token 而不是抄常数**——抄一份的话 CSS 改一边 JS 不同步，
// 症状是"该翻到左边却翻到右边、还露一半在屏幕外"。token 在 base.css 的 --w-drop / --h-drop。
const dropTreeStyle = computed(() => {
  const cs = getComputedStyle(document.documentElement)
  // **读 token，不抄常数**（base.css 的 --w-drop / --h-drop / --sp-9）。
  // 抄一份的话两边一改就不同步，症状是"浮层翻到了该翻的反面、还露一半在屏幕外"。
  // 读不到就**不猜**：不翻转，浮层从指针右下展开（宁可位置差一点，
  // 也别拿猜的数把它翻到屏幕外去）。
  const num = (name) => {
    const v = parseFloat(cs.getPropertyValue(name))
    return Number.isFinite(v) ? Math.round(v) : null
  }
  const w = num('--w-drop')
  const h = num('--h-drop')
  const gap = (num('--sp-9') ?? 0) + 9
  const flipX = w != null && dnd.ax + gap + w > window.innerWidth
  const flipY = h != null && dnd.ay + gap + h > window.innerHeight
  return {
    left: `${flipX ? dnd.ax - gap - w : dnd.ax + gap}px`,
    top: `${Math.max(8, flipY ? dnd.ay - h - gap : dnd.ay + gap)}px`
  }
})

// 落点全站只有拖拽浮出的那棵树一处（CategoryTree 的 mode="drop"），它只渲染
// data-cat-id 标记，命中由 store 的 hitCategory() 用 elementFromPoint 算。
// 侧边栏、药丸都不再当落点：侧边栏的目录项是导航，「待归类」药丸是纯筛选。

onMounted(async () => {
  // 侧边栏偏好（宽度 / 密度）写到 <html> 行内；teleport 到 body 的浮层也继承得到。
  // 读不到偏好就什么都不写，base.css 的 :root 说了算。
  // 卸载时 clearUiPrefs 摘掉——admin 壳压根没有侧边栏，留着是死重量。
  applyUiPrefs()
  // 菜单/资料和业务数据并行拉：菜单决定左侧导航，数据决定目录树。
  // 三个 ensure 收敛在 loadShellData 一处（原来这里和 AppHomeView 各写了一遍）。
  await loadShell()
})

onBeforeUnmount(() => {
  clearUiPrefs()
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
  /* 往下偏一点，别正压在指针下的那一行上——正压着的话"正在放哪"和"这是哪一行"
     两个字面糊在一起（2026-10-07 实机截图就是这个样子）。
     pointer-events: none 保证它遮不住命中检测，所以这里只管好不好看。 */
  transform: translate(10px, 14px) rotate(2deg);
  max-width: 240px;
  padding: var(--sp-8) var(--sp-12);
  border-radius: var(--radius);
  border: 1px solid var(--cc-primary);
  background: var(--cc-surface);
  box-shadow: 0 8px 24px rgb(31 35 41 / 18%);
  font-size: var(--text-base);
  line-height: 1.5;
  color: var(--cc-text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.95;
}
/* 两处"点外面关掉"的透明遮罩，用途一样、只有层级不同，收成一个基类 + 两个 z-index。
   - .pick-scrim  z 19，压在归类落点树（20）之下，树上的行照常可点
   - .cascade-scrim z 39，压在目录树弹层（40）之下，弹层里的行照常可点
   两处都是"透明只为接住点击"，所以不画任何背景。
   position/inset 只能写在基类上：漏挂 `scrim` 的话元素退化成 .app-shell 里
   一个宽度 0 的 flex item，点击永远打不中，而且 build / 编译都不会报。 */
.scrim {
  position: fixed;
  inset: 0;
}
.pick-scrim {
  z-index: 19;
}
.cascade-scrim {
  z-index: 39;
  /* **只盖主区，不盖侧边栏**。侧边栏不是"弹层外面"：它是弹层的锚点所在，
     也是「把弹层里的子目录拖到一级目录上」唯一的落点区。
     盖住它的后果不是"多点不到"而是**拖拽整个失效**——落点靠
     document.elementFromPoint + closest('[data-cat-id]') 算（store 的 hitCategory），
     指针落在遮罩上时两个 closest 都落空 → over = null → 不高亮、松手无事。
     左边距用同一个变量，不能各写各的：body 上有 zoom(--cc-scale)，
     两个地方读同一个 token，改一边另一边自动跟。 */
  left: var(--w-side);
}
.app-shell {
  display: flex;
  /* 不是 100vh：body 上有 zoom(--cc-scale)，视口单位会被重复放大，
     壳就会被撑到 1.5 倍视口高、整页开始滚。百分比在缩放坐标系里解析，天然正确。 */
  height: 100%;
  background: var(--cc-bg);
  overflow: hidden;
}

/* ---------- 左侧栏 ---------- */
.app-side {
  width: var(--w-side);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--cc-surface);
  border-right: 1px solid var(--cc-border);
  padding: var(--sp-14) var(--sp-11) var(--sp-11);
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
  gap: var(--sp-10);
  padding: 0 var(--sp-8) var(--sp-13);
}
.app-side__logo {
  width: var(--ctl-md);
  height: var(--ctl-md);
  border-radius: var(--radius);
  background: var(--cc-logo-gradient);
  color: var(--cc-surface);
  font-size: var(--text-md);
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.app-side__name {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--cc-text-1);
  letter-spacing: var(--ls-tight);
}

.app-nav {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}
.app-nav__item {
  display: flex;
  align-items: center;
  gap: var(--side-gap);
  padding: var(--side-py) var(--side-px);
  border-radius: var(--radius);
  color: var(--cc-text-2);
  font-size: var(--text-xl);
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
  font-size: var(--text-2xl);
}

.app-side__section {
  margin-top: var(--sp-14);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
/* 拖着一个目录时，这一整块是"移成一级目录"的落点。
   和树上那两套（蓝=能放 / 红=不能放）用同一组色，别搞出第二套视觉语言。 */
.app-side__section.is-drop {
  outline: 2px solid var(--cc-primary);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}
.app-side__section.is-bad {
  outline: 2px solid var(--cc-danger-deep);
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}
.app-side__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--side-px) var(--side-py);
  font-size: var(--text-md);
  color: var(--cc-text-3);
  font-weight: 500;
}
.app-side__add {
  border: none;
  background: transparent;
  color: var(--cc-text-3);
  cursor: pointer;
  font-size: var(--text-2xl);
  line-height: 1;
  padding: 0 var(--sp-4);
  border-radius: var(--radius-xs);
}
.app-side__add:hover {
  color: var(--cc-primary);
  background: var(--cc-primary-soft);
}
.app-cats {
  display: flex;
  flex-direction: column;
  gap: var(--side-list-gap);
  overflow-y: auto;
  max-height: 40vh;
}
.app-cats__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--side-gap);
  padding: var(--side-py) var(--side-px);
  border-radius: var(--radius-sm);
  color: var(--cc-text-2);
  font-size: var(--text-base);
  text-decoration: none;
  /* 行尾的「⋯」默认不显形，悬停/键盘聚焦这一行时才露出来。
     一排行里全是点会像一堆图标噪点，而且点它的人只有少数几个。
     focus-within 一起带上：opacity:0 的按钮仍然能被 Tab 选中，
     只挂 :hover 的话键盘用户会 Tab 到一个看不见的按钮上。 */
  --row-tools: 0;
}
.app-cats__item:hover,
.app-cats__item:focus-within {
  --row-tools: 1;
}
.app-cats__item:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.app-cats__item.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
  font-weight: 500;
}
/* 拖拽时这一行是落点。跟树上的行同一组色（蓝=能放 / 红=不能放），
   不另起一套视觉语言。压在 :hover 之后：hover 是常态、落点是一次性事件，
   一次性的要压过常态，否则拖到哪行都看着像"只是悬停着"。 */
.app-cats__item.is-drop {
  background: var(--cc-primary);
  color: var(--cc-surface);
}
.app-cats__item.is-bad {
  background: var(--cc-danger-soft);
  color: var(--cc-danger-deep);
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
/* 右对齐 + 最小宽度：一位数和两位数右边沿对齐，图标那一列也不会左右跳。
   数量跟「⋯」和展开箭头共用行上的 --row-tools，悬停/聚焦这一行才露出来。
   一行平时只剩目录名，扫视的时候干净，动手的时候工具都在。 */
.app-cats__count {
  font-size: var(--text-sm);
  color: var(--cc-text-4);
  flex-shrink: 0;
  min-width: 17px;
  text-align: right;
  opacity: var(--row-tools, 1);
  transition: opacity 0.15s;
}
.app-cats__expand {
  display: inline-flex;
  align-items: center;
  /* border/background 这两条和同文件的 .app-side__add（＋）、RowMenu 的
     .row-menu__btn（⋯）是**同一组**：全局 reset 只清了 margin/padding，
     没清 border（base.css 的 `* { margin: 0; padding: 0 }`）。漏了的话
     `<button>` 的默认边框就露成一个黑框——2026-10-07 实机截图就是这个。 */
  border: none;
  background: transparent;
  padding: var(--sp-2);
  margin-left: calc(-1 * var(--sp-4));
  border-radius: var(--radius-xs);
  color: var(--cc-text-4);
  cursor: pointer;
  opacity: var(--row-tools, 1);
  transition: opacity 0.15s;
}
.app-cats__expand:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.app-side__empty {
  padding: var(--sp-5) var(--sp-10);
  margin: 0;
  font-size: var(--text-md);
  color: var(--cc-text-4);
}

.app-side__spacer {
  flex: 1;
}

.app-side__user {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  padding: var(--sp-10) var(--sp-10) var(--sp-5);
  border-top: 1px solid var(--cc-surface-2);
}
/* 用户菜单在底栏里要**撑满**，齿轮才能被顶到最右。
 *
 * ⚠️ **撑的是 wrapper，不是触发器。** `ElDropdown` 的根是它自己包的一层
 * `<div class="el-dropdown">`，触发器在那一层**里面**。所以给触发器写 flex: 1
 * 只会在 wrapper 内部把尖角推到名字右边，**footer 层面那个 flex item 仍按内容收窄**，
 * 齿轮照样贴着尖角（2026-10-07 实机截图）。
 *
 * 现在用户菜单是独立的 `UserMenu` 组件，**它的根元素**承担这个 flex: 1
 * （ElDropdown 没声明 class prop，但只有一个根元素，Vue 的 fallthrough attrs
 * 会把 class 合并到根 div 上——所以这个 class 确实落在 wrapper 上）。
 * `min-width: 0` 别省：不然长用户名的 min-content 会把这一格顶开、名字不省略号。 */
.app-side__user-menu {
  flex: 1;
  min-width: 0;
}

/* 启动加载失败时顶掉用户名那一格。文案用警示色而不是灰——"加载失败"是需要
   用户点一下重试的状态，不该和"未设置昵称"那种中性缺省长得一样。 */
.app-side__failed {
  flex: 1;
  font-size: var(--text-base);
  color: var(--cc-danger);
}
/* 用户菜单的触发区：头像 + 名字 + 尖角，整块可点。
   以前这一格是「头像 + 名字 + 一个独立的退出图标」，现在退出收进下拉了，
   触发区整体变成菜单入口，所以要自己给 cursor 和 hover 反馈。

/* 用户菜单的触发区（头像 + 名字 + 尖角）的样式在 components/UserMenu.vue ——
   两个壳共用那一份组件，样式也该跟着走一份。 */

/* 偏好面板顶部的「AI 能力」入口：一条整行的跳转。
   它是插槽内容（由本组件给 PrefsPanel），所以样式归这里 ——
   插槽内容的 scope 属于**给内容的那一方**，不是组件自己。 */
.prefs__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: var(--sp-6);
  padding: var(--sp-3) var(--sp-4);
  border: 0;
  border-radius: var(--cc-radius-sm);
  background: transparent;
  color: var(--cc-text-1);
  font-size: var(--text-md);
  text-align: left;
  cursor: pointer;
}
.prefs__link:hover {
  background: var(--cc-surface-2);
  color: var(--cc-primary);
}

/* ---------- 右侧 ---------- */
.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-top {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-13);
  padding: 0 var(--sp-14);
  background: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid var(--cc-border);
}
.app-top__left {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  min-width: 0;
}
.app-top__crumb {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--cc-text-1);
  /* 标题就一两个词，绝不该折行。左边被挤窄时应该先让搜索框收（下面给了 min-width），
     而不是把「知识点」竖着排成一行一个字 —— 那是最难看的破法。 */
  white-space: nowrap;
}
.app-top__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
}
.app-search {
  width: 260px;
  /* 视口不够宽时先收搜索框，别把标题挤折行（el-input 默认不缩，得显式给 min-width） */
  min-width: 110px;
  flex-shrink: 1;
}
.app-search :deep(.el-input__wrapper) {
  border-radius: var(--radius-xl);
  box-shadow: none;
  background: var(--cc-surface-2);
}
.app-top__icon-btn {
  border: none;
  background: transparent;
  color: var(--cc-icon);
  cursor: pointer;
  padding: var(--sp-7);
  font-size: var(--text-2xl);
  border-radius: var(--cc-radius-sm);
  display: flex;
}
.app-top__icon-btn:hover {
  background: var(--cc-surface-2);
  color: var(--cc-primary);
}

/* 内容区：居中窄栏——这是"产品"的视觉签名，不是"后台"。
   宽度只剩一档（base.css 的 --cc-content-w）。原来还有一档 --cc-content-w-reading
   专给详情页的 920px，#134 解除了列表页/首页的 1440 上限后**把详情页留作例外**，
   而那个例外正是用户反复投诉的"窗口一变宽、卡片比例就掉"的来源，已一并解除
   （见 base.css 同名注释）。
   1280 只在约 1330px 以上的视口才咬：1440 / 1536 这类笔记本已经铺满，
   1920 收起侧栏时左右留白从 476px 收到 296px。 */
.app-content {
  flex: 1;
  overflow-y: auto;
  /* 滚动条常驻留槽。**这不是美化，是修一个横跳**：内容是 `margin: 0 auto` 居中的，
     而滚动条长在本元素里 —— 它一出现，容器内容宽度就少一条，**居中的起点跟着变**。
     1920 / scale 1.3 下实测：内容被 max-width 截到 1664（宽度不变），
     左起点从 (1864-1664)/2 = 100 跳到 (1849-1664)/2 ≈ 92，**整块内容横移 8px**。
     触发条件只是"这一屏放不放得下"——「全部 33」和「待归类 3」卡片数差很多，
     切一下筛选滚动条就时有时无，看着像筛选条自己动了一下。
     留槽之后有没有滚动条都一样宽，起点也就固定了。 */
  scrollbar-gutter: stable;
  /* 底部 72px 是给**会滚动**的页面留的翻页呼吸位（列表页拉到最底时最后一张卡
     不会贴着视口边）。首页 AppHomeView 是唯一一页「三排刚好铺满一屏」的，它不滚动，
     不该继承这份呼吸位——见下面那条覆写。

     72 直接写字面量，不提成变量：#126 之前中间过一层 `--app-pad-b`，那是因为
     当时的错误做法让**子页面**去读它算补偿。现在子页面已经不读了（它在 .home 上
     老老实实 min-height:100%），这个变量只剩"被自己定义的 padding 消费"一次，
     纯属多绕一层。 */
  padding: var(--sp-15) var(--sp-15) 72px;
}
/* 首页不留底部呼吸位，改成和左右一样的 --sp-15。
   左右留白交给偏好面板（--home-pad-x，见 uiPrefs.js 的 HOME_PAD_X）。

   ⚠️ **这里改 padding，不要让子页面去"补偿"**（#124 / #125 连续返工两次就在这）。
   之前首页写的是 `min-height: calc(100% + 72px - var(--sp-15))`，看着是把 72−32
   补回来了，实际是**错的**：`100%` 量的是内容盒，`.home` 顶到 y=32、底到 y=H−32，
   而**内容盒底边在 y=H−72** —— 元素多出来 40px 扎进了 padding 区。padding 不吸收
   溢出，滚动容器照样按内容盒 + 溢出算 scrollHeight，于是**凭空多出 40px 滚动条**。
   补 padding 只能在**拥有这个 padding 的元素自己身上**改。 */
.app-content:has(.home) {
  padding-bottom: var(--sp-15);
  padding-left: var(--home-pad-x, var(--sp-15));
  padding-right: var(--home-pad-x, var(--sp-15));
}
/* 知识点列表：只有左右留白（--learn-pad-x，见 uiPrefs.js 的 LEARN_PAD_X），底部呼吸位照旧。

   判据用 :has(根节点) 而不是路由名：这一页"要不要底部留白 / 左右留白多大"是**它自己的
   形态**（三排铺满一屏 / 卡片网格）决定的，认根节点比认路由贴近事实，路由改名也不会失效。 */
.app-content:has(.learn) {
  padding-left: var(--learn-pad-x, var(--sp-15));
  padding-right: var(--learn-pad-x, var(--sp-15));
}
.app-content > * {
  margin: 0 auto;
}
/* **首页 / 知识点列表 / 知识点详情都解除 1440 读宽上限**——但三者的理由不一样，
   别把其中一条当通用理由：

   · **首页**：三排是数据概览、不是正文，卡在 1440 意味着窗口一拉满两侧就空出 100px 以上。
   · **知识点列表**：`auto-fill minmax(--w-grid, 1fr)` 的卡片网格**在内部就已经把列宽封住了**
     ——列数 = floor((可用宽+gap)/(400+gap))，列宽在"刚好塞不下一列"时被 1fr 拉到最大，
     上界约 **537px**。**卡片宽度本来就有界，屏幕再宽只是多排列**，外面这层封顶是多余的。
   · **知识点详情**：⚠️ #134 当初**只解除了前两页、把详情页留作例外**（它当时自己压着
     920px）。但用户随后发现详情页是**同一个毛病**，原话："**在比例相同的情况下 不应该
     被是否全屏影响啊 为什么会存在长度限制**"。根因就是这个 max-width——定值，宽过它卡片
     就卡住、两侧空白一路增长、占屏比例一路往下掉。**#134 留下的那个例外，正是问题本身的来源。**
     详情页自己那条封顶后来也整个删掉了（2026-10-21：「阅读宽度」那根滑块默认 2400 ≈ 不封顶，
     **调它像调了个摆设**），所以这条必须一起解除：
     **不解除的话，删掉详情页自己的 920 只会让它退到 1440 这个定值上，问题一点没解决。**

   三条的共同后果（用户原话："很明显缩放的更好看"、"拉满就两侧空一大片"）：浏览器一放大、
   视口宽度掉下来跨过上限，**内容宽度当场停住**，两侧开始长空白。
   ⚠️ 1440 现在**没有任何消费者**了（--cc-content-w 仍是 admin 之外的默认值来源）。 */
.app-content:has(.home, .learn, .detail) > :where(*) {
  max-width: none;
}
.app-content > :where(*) {
  max-width: var(--cc-content-w);
}
</style>
