<template>
  <div class="milkdown-editor-body">
    <div v-if="showToolbar" class="md-toolbar">
      <el-button size="small" class="md-btn-bold" @click="runCmd('ToggleStrong')" title="加粗 (Ctrl+B)">B</el-button>
      <el-button size="small" class="md-btn-italic" @click="runCmd('ToggleEmphasis')" title="斜体 (Ctrl+I)">I</el-button>
      <el-button size="small" @click="runCmd('WrapInHeading', 2)" title="二级标题">H2</el-button>
      <el-button size="small" @click="runCmd('WrapInHeading', 3)" title="三级标题">H3</el-button>
      <el-button size="small" @click="runCmd('CreateCodeBlock')" title="代码块">{ }</el-button>
      <el-button size="small" @click="insertLink" title="链接">
        <el-icon><Link /></el-icon>
      </el-button>
      <el-button size="small" @click="openTableDialog" title="插入表格">
        <el-icon><Grid /></el-icon>
      </el-button>
    </div>
    <Milkdown />

    <!-- 表格右键菜单：监听 document contextmenu，过滤"在 .ProseMirror 的 td/th 内"
         时弹出（preventDefault 阻止浏览器原生菜单）；菜单 4 项 = 上下插行 + 左右插列 -->
    <div
      v-if="ctxMenu.visible"
      class="ctx-menu"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
      @click.stop
    >
      <div class="ctx-menu__item" @click="onCtxMenuClick('AddRowBefore')">上方插入行</div>
      <div class="ctx-menu__item" @click="onCtxMenuClick('AddRowAfter')">下方插入行</div>
      <div class="ctx-menu__item ctx-menu__item--sep" />
      <div class="ctx-menu__item" @click="onCtxMenuClick('AddColBefore')">左侧插入列</div>
      <div class="ctx-menu__item" @click="onCtxMenuClick('AddColAfter')">右侧插入列</div>
    </div>

    <!-- 插入表格 dialog：只问行数 + 列数两个数字，按"极简"原则不做列宽/对齐/样式配置 -->
    <el-dialog
      v-model="tableDialogVisible"
      title="插入表格"
      width="320px"
      :show-close="true"
      @close="onTableDialogClose"
    >
      <div class="table-dialog">
        <div class="table-dialog__row">
          <span class="table-dialog__label">行数</span>
          <el-input-number v-model="tableRows" :min="1" :max="20" size="small" controls-position="right" />
        </div>
        <div class="table-dialog__row">
          <span class="table-dialog__label">列数</span>
          <el-input-number v-model="tableCols" :min="1" :max="10" size="small" controls-position="right" />
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="tableDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmInsertTable">插入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Link, Grid } from '@element-plus/icons-vue'
import { Milkdown, useEditor } from '@milkdown/vue'
import { Editor, rootCtx, commandsCtx, defaultValueCtx } from '@milkdown/core'
import { $useKeymap } from '@milkdown/utils'
import { listener, listenerCtx } from '@milkdown/plugin-listener'
import { history } from '@milkdown/plugin-history'
import { clipboard } from '@milkdown/plugin-clipboard'
import { commonmark } from '@milkdown/preset-commonmark'
import { gfm } from '@milkdown/preset-gfm'
import { selectAll } from 'prosemirror-commands'

// 必须赋值给变量：Vue 3 <script setup> 里 defineProps 不赋值时，编译器只在模板把它当 props，
// script 代码里访问 props.xxx 会 ReferenceError（这次踩了，所以 props.modelValue 用上时挂了）
const props = defineProps({
  modelValue: { type: String, default: '' },
  // 是否显示工具栏（B/I/H2/H3/...）。默认 false = 简洁模式，只露 ProseMirror 编辑区。
  showToolbar: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

// 插入表格 dialog state：默认 3x3，跟 Markdown 文档里常见的初始规模一致
const tableDialogVisible = ref(false)
const tableRows = ref(3)
const tableCols = ref(3)

// 表格右键菜单 state：监听 document.contextmenu，过滤"在编辑器 .ProseMirror 的 td/th 内"
// 才弹出（其他位置不拦截，让浏览器原生菜单照常显示）。
// 为什么不绑在 <Milkdown @contextmenu>：milkdown 函数式组件的事件转发不稳（class fallthrough 不稳同理）。
const ctxMenu = ref({
  visible: false,
  x: 0,
  y: 0
})

function onDocumentContextMenu(e) {
  // 只处理编辑器内部的右键（不污染编辑器外的页面）
  const inProseMirror = e.target.closest && e.target.closest('.ProseMirror')
  if (!inProseMirror) return
  // 只在 td/th 内才拦截（其他段落 / heading / list 用浏览器原生菜单）
  const inCell = e.target.closest && e.target.closest('td, th')
  if (!inCell) return
  e.preventDefault()
  ctxMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY
  }
}

function closeCtxMenu() {
  if (ctxMenu.value.visible) ctxMenu.value.visible = false
}

function onCtxMenuClick(cmdKey) {
  runCmd(cmdKey)
  closeCtxMenu()
}

// Esc 关闭 + 全局 click 关闭。click 监听挂 document 上，菜单本身的 click 用 @click.stop
// 阻止冒泡避免"点菜单项的同时立刻被 document.click 关掉"导致点击事件失效。
function onDocumentKeydown(e) {
  if (e.key === 'Escape') closeCtxMenu()
}

onMounted(() => {
  document.addEventListener('contextmenu', onDocumentContextMenu)
  document.addEventListener('click', closeCtxMenu)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('contextmenu', onDocumentContextMenu)
  document.removeEventListener('click', closeCtxMenu)
  document.removeEventListener('keydown', onDocumentKeydown)
})

// Ctrl+A 全选 keymap：prosemirror-commands 提供 selectAll 函数，封装成 $useKeymap plugin。
// @milkdown/preset-commonmark / @milkdown/preset-gfm 都不绑 Mod-a（prosemirror 默认 keymap 也只绑 Backspace/Enter/方向键 等，没绑 Ctrl+A），
// 必须自己加。history plugin 自动绑 Mod-z/Mod-y/Shift-Mod-z（undo/redo），clipboard plugin 自动接管 paste 事件做 markdown 解析。
const selectAllKeymap = $useKeymap('selectAllKeymap', {
  SelectAll: {
    shortcuts: 'Mod-a',
    command: () => () => selectAll
  }
})

// 关键：本组件被 <MilkdownEditor> 包在 <MilkdownProvider> 子树中
// Provider 的 setup 跑完后才进入本组件 setup → useEditor() inject ✓
// 编辑器内部变化 → 通过 v-model 上抛给外层
const { get } = useEditor((root) =>
  Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, root)
      // 关键：把外部 v-model 的 markdown 作为初始 doc，否则 Editor 永远是空 doc。
      // defaultValueCtx 默认值是 ""（@milkdown/core/lib/index.js:366），不显式 set 会导致：
      //   - 详情页：保存的 markdown 不渲染
      //   - 记录页：能"假正常"是因为用户输入直接打在 contenteditable，markdown 解析其实也没生效
      // 切 topic 时需重建 MilkdownEditor（keep-alive 同 route 名不复用组件）才能让新内容生效。
      ctx.set(defaultValueCtx, props.modelValue)
      ctx.get(listenerCtx).markdownUpdated((_, markdown) => {
        emit('update:modelValue', markdown)
      })
    })
    .use(listener)
    .use(commonmark)
    .use(gfm)
    // 通用交互能力：项目走"单独引入"模式没用 @milkdown/kit 聚合包，
    // history / clipboard 都装在 node_modules 但未 use——这一行补齐 undo/redo + markdown 粘贴解析。
    .use(history)
    .use(clipboard)
    // Ctrl+A 全选（上面 selectAllKeymap 的 plugin）
    .use(selectAllKeymap)
)

function runCmd(key, payload) {
  // 根因：@milkdown/core 7.x 的 commandsCtx 默认值是 CommandManager 对象，不是 CmdTuple 数组，
  // 没有 .find() 方法。正确 API 是 CommandManager.call(key, payload)。
  // 命令 key 是 $command("ToggleStrong", ...) 里那个 PascalCase 字符串，不是小写驼峰。
  get().action((ctx) => {
    ctx.get(commandsCtx).call(key, payload)
  })
}

async function insertLink() {
  try {
    const { value } = await ElMessageBox.prompt('请输入链接 URL', '插入链接', {
      inputPlaceholder: 'https://...',
      confirmButtonText: '插入',
      cancelButtonText: '取消'
    })
    if (value) runCmd('ToggleLink', { href: value, title: '' })
  } catch { /* user cancel */ }
}

// 插入表格：preset-gfm 注册的 InsertTable 命令（payload: { row, col }）。
// 表格的渲染 / 单元格内 tab/enter 键行为 / 增删行列全部由 prosemirror-tables 接管，
// 这里只负责"插入一张初始表格"。
function openTableDialog() {
  tableRows.value = 3
  tableCols.value = 3
  tableDialogVisible.value = true
}

function confirmInsertTable() {
  const row = Number(tableRows.value) || 3
  const col = Number(tableCols.value) || 3
  runCmd('InsertTable', { row, col })
  tableDialogVisible.value = false
}

// dialog 关闭时把 rows/cols 还原成默认值，避免下次打开残留上次输入
function onTableDialogClose() {
  tableRows.value = 3
  tableCols.value = 3
}
</script>

<style>
/* 全局（非 scoped）：Milkdown 由第三方函数式组件渲染，实际 DOM 结构是
   <div data-milkdown-root> > .milkdown > .ProseMirror[contenteditable]。
   - focus 落在 .ProseMirror（contenteditable）上，不是外层容器 → outline:none 必须加在 .ProseMirror
   - min-height 也加在 .ProseMirror 上，整个空白区才能点击聚焦输入（加外层容器只让容器变高，空白区仍不可聚焦）
   项目里 Milkdown 编辑器只此一处，无污染风险。 */
.md-toolbar {
  display: flex;
  gap: 4px;
  padding: 4px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.milkdown-editor-body .ProseMirror {
  height: 270px;       /* 固定高度（之前是 min-height:200px 内容多了会拉长；#58 改 200 后用户嫌短，再+35%≈1/3 到 270）*/
  overflow-y: auto;    /* 内容超出时显示滚动条 */
  padding: 8px 12px;
  outline: none;  /* 去掉浏览器对 contenteditable 默认画的 focus outline（黑色长方形边框） */
}
.md-btn-bold,
.md-btn-italic {
  /* 用 Times New Roman 让字母 B/I 跟中文按钮视觉上区分开，不再加字重/斜体（之前 font-weight:700 + font-style:italic 看起来像 demo 按钮而不是工具栏） */
  font-family: 'Times New Roman', serif;
}

/* 插入表格 dialog：行/列两个数字输入紧凑展示 */
.table-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}
.table-dialog__row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.table-dialog__label {
  width: 50px;
  font-size: 14px;
  color: #606266;
}

/* 表格右键菜单：fixed 定位（不依赖父容器 position），z-index 提到 el-dialog 之上避免被遮 */
.ctx-menu {
  position: fixed;
  z-index: 9999;
  min-width: 140px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 4px 0;
  font-size: 14px;
  color: #303133;
}
.ctx-menu__item {
  padding: 8px 16px;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
  transition: background 0.12s, color 0.12s;
}
.ctx-menu__item:hover {
  background: #ecf5ff;
  color: #409eff;
}
.ctx-menu__item--sep {
  padding: 0;
  margin: 4px 0;
  height: 1px;
  background: #f0f2f5;
  cursor: default;
}
.ctx-menu__item--sep:hover {
  background: #f0f2f5;
}

/* 表格渲染样式：prosemirror-tables 自带 CSS（@milkdown/prose/tables/style/tables.css）
   只设了 border-collapse / table-layout:fixed / 选中单元格 overlay，**没设 border 宽度和颜色**——
   不补这层样式，DOM 里有 <table><td> 但视觉上完全无边框，用户以为"没插入表格"。
   这里补齐 border / padding / 单元格最小宽度，让表格"看得见"。 */
.milkdown-editor-body .ProseMirror table {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  margin: 8px 0;
  overflow: hidden;
}
.milkdown-editor-body .ProseMirror th,
.milkdown-editor-body .ProseMirror td {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  vertical-align: top;
  min-width: 80px;
  position: relative;
}
.milkdown-editor-body .ProseMirror th {
  background: #f5f7fa;
  font-weight: 600;
}
/* 多 cell 选中态：prosemirror-tables 的 drawCellSelection 自动给选中的 cell 加 class="selectedCell"，
   但 prosemirror-tables 自带 CSS（之前分析过不带 border）也没被我们引入——视觉上看不到高亮，
   用户以为"没选上"。这里用 Element Plus 浅蓝主题色覆盖，跟项目主题一致。
   选 background 直接覆盖而不是用 :after 伪元素（之前 td 已设 position:relative 兜得住，
   但 background 方案更简单，不需要伪元素 + 透明度计算的额外开销）。 */
.milkdown-editor-body .ProseMirror td.selectedCell,
.milkdown-editor-body .ProseMirror th.selectedCell {
  background: #ecf5ff;
}
.milkdown-editor-body .ProseMirror .tableWrapper {
  overflow-x: auto;
}
@media (max-width: 720px) {
  .md-toolbar {
    gap: 2px;
  }
}
</style>