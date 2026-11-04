<template>
  <section class="toc">
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
      mode="nav"
      :active="active"
      :collapse-below-depth="1"
      @pick="(c) => emit('pick', c)"
      @pick-topic="(t) => emit('pick-topic', t)"
      @add="(c) => openCreate(c.id, c.name)"
      @rename="openRename"
      @points="openPoints"
    />

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
         那个建一整套（主题 → 骨架 → 知识点），这个只填内容。 -->
    <CategoryTreeDialog
      v-model="pointsDialog.visible"
      mode="points"
      :category="pointsDialog.category"
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
  active: { type: Boolean, default: true }
})
const emit = defineEmits(['pick', 'pick-topic'])

/**
 * 树的根 = 当前目录。**用 computed 包一层**：`items` 每次渲染都新建一个数组的话，
 * 下游的 computed 会跟着一起重算（这棵树有两百多行）。
 */
const items = computed(() => [props.category])

// ==================== 目录自己的操作 ====================

/**
 * 「给目录起个名」弹窗的状态。
 *
 * ⚠️ **只留 visible**：原来这里还把 mode / parentId / parentLabel / initialName /
 * initialId 五项都写了一遍初值 —— 那是 `CategoryNameDialog` 自己的 prop 默认值的
 * **第二份**，而且 openCreate / openRename 每次都会把这五项重设一遍 ⇒ 初值永远读不到。
 * ⇒ 这里只留"开没开"，其余由调用方给（AppLayout 那边早就这么改了）。
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

<style scoped>
/* ⚠️ **这里原来有 `.toc__bar` / `.toc__btn`**（工具条那一行的样式）——
   工具条 2026-11-04 整个删掉了（两个按钮分别去了侧边栏 hover 按钮和树的根行），
   样式一起删，别留两条没人用的规则。

   `.toc` 现在只有**一个孩子**（那棵树）⇒ 也不需要 gap / flex 列了。 */
</style>
