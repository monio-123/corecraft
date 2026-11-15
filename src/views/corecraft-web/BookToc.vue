<template>
  <section>
    <!--
      树视图：**一个一级目录的整棵目录树**。

      <p>为什么是"树"而不是"平铺的卡片"（2026-11-03 晚）：
      一级目录是**一本书**（「计算机网络」195 条、「Java基础」191 条），
      平铺出来是两百多张卡片按时间倒着排 —— 那不是"看书"，是把整本书倒过来抖一遍。
      真正能读的单元是**小节**（5~10 条）。

      <p>所以分两层：**结构看树，内容看卡片**。
      树这一层只回答"这本书里有什么"（目录 + 知识点清单）；
      点目录名 = 进那个目录的**卡片视图**（该目录子树的知识点平铺），
      点知识点叶子 = 直接进详情。

      <p>⚠️ **树上面那一整块（面包屑 / 筛选条 / 目录标题行）仍然不要**：
      这一屏是结构导航，"筛知识点""考这个目录"对它都没意义 ——
      有意义的那些操作在卡片视图那一层。
      ⚠️ **顶部那条工具条 2026-11-04 删掉了**（原来两个按钮：展开⇄收起 + 用卡片看）：
      「用卡片看」挪到**侧边栏目录行的 hover 按钮**（形态切换统一收在那一处）；
      「展开⇄收起」挪到**树里一级目录那一行**（见 CategoryTree 的 `ct__expand`）——
      单独占一行不好看，而它管的就是这棵树，放在根行上才是它该在的位置。
    -->
    <CategoryTree
      :items="items"
      :active="active"
      :mode="mode"
      :collapse-below-depth="1"
      @pick="(c) => emit('pick', c)"
      @pick-topic="(t) => emit('pick-topic', t)"
      @mode-change="(m) => emit('mode-change', m)"
      @add="(c) => openCreate(c.id, c.name)"
      @rename="openRename"
      @points="openPoints"
    >
      <!-- 卡片模式的内容盒：**外层（AppLearnView）的卡片视图块**原样递下去 ——
           和首页点进去的卡片视图是同一套（筛选条 + 分页网格），这里不自制第二套。 -->
      <template v-if="$slots.cards" #cards>
        <slot name="cards" />
      </template>
      <template v-if="$slots.tools" #tools>
        <slot name="tools" />
      </template>
    </CategoryTree>

    <!-- 「给目录起个名」：新建子级 / 改名是同一个弹窗，靠 mode + initialId 区分。 -->
    <CategoryNameDialog
      v-model="nameDialog.visible"
      :mode="nameDialog.mode"
      :parent-id="nameDialog.parentId"
      :parent-label="nameDialog.parentLabel"
      :initial-name="nameDialog.initialName"
      :initial-id="nameDialog.initialId"
    />

    <!-- 给这个目录写知识点（目录现成，只写内容）。**跟生成主题不是一回事**：
         那个建一整套（主题 → 规划 → 知识点），这个只填内容。
         两者都**提交任务到服务端**跑：关页面不断，进度在侧边栏「任务」里看。 -->
    <!-- ⚠️ **@done 要绑**（2026-11-12）：这个弹窗提交任务之后要刷目录树和任务列表。
         原来没绑 ⇒ 「写知识点」这条路上建完目录，侧边栏的树不刷新
         （而 AppLayout 那份绑了 ⇒ 两条路表现不一致）。
         任务列表的刷新由弹窗自己走统一出口（genTaskCreated），
         这里这个回调只管**目录树 / 知识点列表**。 -->
    <CategoryTreeDialog
      v-model="pointsDialog.visible"
      mode="points"
      :category="pointsDialog.category"
      @done="onTreeDone"
    />
  </section>
</template>

<script setup>
/**
 * 树视图。**只管"看结构"和"目录自己的操作"**；
 * 筛知识点、考试那些在卡片视图那一层（同一份筛选条，两层共用）。
 */
import { computed, reactive } from 'vue'
import CategoryTree from './CategoryTree.vue'
import CategoryNameDialog from './CategoryNameDialog.vue'
import CategoryTreeDialog from './CategoryTreeDialog.vue'

const props = defineProps({
  /** 当前这个目录（{ id, name, ... }）—— 树的根。 */
  category: { type: Object, required: true },
  /**
   * 这一屏现在是不是"在眼前"（**由页面传下来**）。
   *
   * <p>树用它判断"要不要定位回刚才那一行"—— 页面被 keep-alive 缓存着，
   * **"我回来了"只有页面知道** ⇒ 由它传，不依赖后代组件的 activated 钩子。
   */
  active: { type: Boolean, default: true },
  /** 这一屏的形态（'tree' | 'cards'）—— 状态在外层（AppLearnView 的 ？view=），这里只透传。 */
  mode: { type: String, default: 'tree' }
})
/**
 * 弹窗建完结构（目录 + 知识点占位）并提交了任务 ⇒ 刷目录树。
 *
 * <p>⚠️ **只刷目录树**：任务列表由弹窗自己走统一出口（`genTaskCreated`），
 * 这里不重复刷（刷两次就是两次请求）。
 */
function onTreeDone() {
  emit('structure-done')
}

// ⚠️ **`structure-done`**（2026-11-12）：目录「写知识点」这条路建完结构后，
// 目录树和知识点列表都要刷 —— 以前这个弹窗的 @done 没人绑 ⇒ 这条路上建完树不刷新。
const emit = defineEmits(['pick', 'pick-topic', 'mode-change', 'structure-done'])

/**
 * 树的根 = 当前目录。**用 computed 包一层**：`items` 每次渲染都新建一个数组的话，
 * 下游的 computed 会跟着一起重算（这棵树有两百多行）。
 */
const items = computed(() => [props.category])

// ==================== 目录自己的操作 ====================

/**
 * 「给目录起个名」弹窗的状态。
 *
 * **visible 常驻 + 其余五项（mode / parentId / parentLabel / initialName / initialId）
 * 每次由 openCreate / openRename 现填**——它们是给模板绑 `CategoryNameDialog` 的 prop
 * 用的"当次参数"，不是初值：reactive 里不写它们，避免造出一份没人读的默认值
 * （AppLayout 那边也是同一种形态）。
 */
const nameDialog = reactive({ visible: false })

const pointsDialog = reactive({ visible: false, category: null })

/** 新建子目录。父目录 = 传进来的那个 c。 */
function openCreate(parentId, parentLabel) {
  nameDialog.mode = 'create'
  nameDialog.parentId = parentId
  nameDialog.parentLabel = parentLabel
  nameDialog.initialName = ''
  nameDialog.initialId = null
  nameDialog.visible = true
}

function openRename(c) {
  nameDialog.mode = 'rename'
  nameDialog.parentLabel = c.name
  // initialName 只用来回显输入框、判"没改就别提交"；真正发给后端的是 id
  nameDialog.initialName = c.name
  nameDialog.initialId = c.id
  nameDialog.visible = true
}

function openPoints(c) {
  pointsDialog.category = c
  pointsDialog.visible = true
}
</script>
