<template>
  <div class="milkdown-editor-body">
    <!-- 工具栏：简洁模式下整行不渲染（"简洁"就是只要正文）。
         底边一条线把操作台和标题/正文分开——工具栏是"操作台"，不该和内容混在一块。
         开关按钮不在这里，它由使用方放在自己的顶栏里（见 MilkdownEditor.vue 顶部注释）。 -->
    <div v-if="showToolbar" class="md-toolbar">
      <el-button text size="small" class="md-btn-bold" @click="runCmd('ToggleStrong')" title="加粗 (Ctrl+B)">B</el-button>
      <el-button text size="small" class="md-btn-italic" @click="runCmd('ToggleEmphasis')" title="斜体 (Ctrl+I)">I</el-button>
      <el-button text size="small" @click="runCmd('WrapInHeading', 2)" title="二级标题">H2</el-button>
      <el-button text size="small" @click="runCmd('WrapInHeading', 3)" title="三级标题">H3</el-button>
      <el-button text size="small" @click="runCmd('WrapInBulletList')" title="无序列表">
        <el-icon><List /></el-icon>
      </el-button>
      <el-button text size="small" @click="runCmd('WrapInOrderedList')" title="有序列表">
        <el-icon><Rank /></el-icon>
      </el-button>
      <el-button text size="small" @click="runCmd('CreateCodeBlock')" title="代码块">{ }</el-button>
      <el-button text size="small" @click="insertLink" title="链接">
        <el-icon><Link /></el-icon>
      </el-button>
      <el-button text size="small" @click="openTableDialog" title="插入表格">
        <el-icon><Grid /></el-icon>
      </el-button>
    </div>

    <!-- 使用方塞进来的东西（现在是知识点详情页的标题）。位置：工具栏之下、正文之上。
         插槽而不是让使用方把工具栏搬上来，是因为工具栏必须在 MilkdownProvider 内部
         才能拿到编辑器 ctx；反过来把标题递进来，顺序就对了。 -->
    <slot name="before-content" />

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
import { Link, Grid, List, Rank } from '@element-plus/icons-vue'
import { Milkdown, useEditor } from '@milkdown/vue'
import { Editor, rootCtx, commandsCtx, defaultValueCtx, editorViewOptionsCtx } from '@milkdown/core'
import { $useKeymap } from '@milkdown/utils'
import { Fragment, Slice } from '@milkdown/prose/model'
import { listener, listenerCtx } from '@milkdown/plugin-listener'
import { history } from '@milkdown/plugin-history'
import { clipboard } from '@milkdown/plugin-clipboard'
import { commonmark } from '@milkdown/preset-commonmark'
import { gfm } from '@milkdown/preset-gfm'
import { exitCode, selectAll } from 'prosemirror-commands'

// 必须赋值给变量：Vue 3 <script setup> 里 defineProps 不赋值时，编译器只在模板把它当 props，
// script 代码里访问 props.xxx 会 ReferenceError（这次踩了，所以 props.modelValue 用上时挂了）
const props = defineProps({
  modelValue: { type: String, default: '' },
  // 是否显示工具栏（B/I/H2/H3/...）。默认 false = 简洁模式，只露 ProseMirror 编辑区。
  showToolbar: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'submit'])

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

// 跳出代码块。
//
// **Milkdown 的 commonmark preset 根本没给 code_block 注册任何 keymap**，也没有绑 exitCode
// （它只注册了 hardbreak 的 Shift-Enter 和 list-item 的 Enter/Tab）。所以在代码块里按 Enter
// 落到 ProseMirror 基础 keymap 的 splitBlock —— 在代码块里就是"插一行新代码"。
// 而 Shift-Enter 被硬换行占了、Mod-Enter 被上面的提交占了，结果就是**进了代码块就出不来**。
//
// 行为对齐语雀 / Notion：光标在代码块**最后一行末尾**按 Enter，在块下方开一个新段落。
// 光标在中间行按 Enter 照常换一行（那里用户显然是想继续写代码），靠返回 false 把活交回
// 基础 keymap——KeymapManager.build 用 chainCommands 串起来依次试，谁先返回 true 谁生效。
//
// priority 100：同一个键的所有 handler 按 (priority ?? 50) 降序排，不给优先级就会排在
// 基础 keymap 后面，永远轮不到。
const exitCodeBlockKeymap = $useKeymap('exitCodeBlockKeymap', {
  ExitCodeBlock: {
    shortcuts: 'Enter',
    priority: 100,
    command: () => (state, dispatch, view) => {
      const { $from, empty } = state.selection
      if (!empty) return false
      // 和 exitCode 内部同一个判据：spec.code 标的是代码类块（code_block / code）
      if (!$from.parent.type.spec.code) return false
      // 必须在文本末尾，否则中间行按 Enter 也会跳出——那就变成"写代码写一半被踢出去"
      if ($from.parentOffset !== $from.parent.content.size) return false
      return exitCode(state, dispatch, view)
    }
  }
})

// Ctrl+A 全选 keymap：prosemirror-commands 提供 selectAll 函数，封装成 $useKeymap plugin。
// @milkdown/preset-commonmark / @milkdown/preset-gfm 都不绑 Mod-a（prosemirror 默认 keymap 也只绑 Backspace/Enter/方向键 等，没绑 Ctrl+A），
// 必须自己加。history plugin 自动绑 Mod-z/Mod-y/Shift-Mod-z（undo/redo），clipboard plugin 自动接管 paste 事件做 markdown 解析。
//
// **command 只能是 `() => selectAll`，不能再套一层箭头。** $useKeymap 要的是
// `command: (ctx) => Command`，而 selectAll 本身已经是 Command 了。
// 写成 `() => () => selectAll` 的话，ProseMirror 拿到的是 `() => selectAll`，
// 调用时参数全被忽略、**返回 selectAll 这个函数对象本身**——函数是真值，
// keymap 于是认为"事件已处理"并 preventDefault 掉浏览器默认行为。
// 结果两头都没发生：原生 Ctrl+A 被吞、ProseMirror 的选区也没改成全选，
// **按 Ctrl+A 一点反应都没有**。
// 对照下面的 submitKeymap：它的内层自己做事并 return true，那才是 Command。
const selectAllKeymap = $useKeymap('selectAllKeymap', {
  SelectAll: {
    shortcuts: 'Mod-a',
    command: () => selectAll
  }
})

// Mod-Enter 提交：原先这个快捷键挂在随手记的 <el-input type="textarea"> 的 keydown 上，
// 换成 Milkdown（contenteditable）后那条监听没了，不补的话用户按了没反应、
// 而且**没有任何报错**——是那种最难发现的静默失效。这里把事件抛给使用方接保存。
// 返回 true 表示"这个按键我处理了"，阻止它继续冒泡到 ProseMirror 默认行为。
const submitKeymap = $useKeymap('submitKeymap', {
  SubmitNote: {
    shortcuts: 'Mod-Enter',
    command: () => () => {
      emit('submit')
      return true
    }
  }
})

// 纯文本粘贴：原样逐行贴。
//
// 为什么不让 @milkdown/plugin-clipboard 那条路走：它把纯文本按 **markdown** 解析，
// 而 markdown 把 `\n\n` 和 `\n\n\n` **都**折叠成"段落边界"——**空了几行这个信息
// 在解析那一步就丢了**。所以不管后面怎么调 CSS，`a\n\nb\n\n\nc` 都还原不出
// "空 1 行"和"空 2 行"的区别，而且 `c\nd\ne` 这种本来没有空行的也看不出差别。
// 用户要的正是这个区分，所以纯文本这条路改成自己走：一个段落，每个 \n 放一个
// 真 hardbreak（渲染成 <br>），空一行 = 两个 hardbreak，间距就是精确的。
//
// 挂在 `editorViewOptionsCtx` 而不是自己注册一个 prose plugin：prosemirror-view 的
// someProp 查 props 的顺序是 **view._props → directPlugins → state.plugins**
// （prosemirror-view/dist/index.js:5684）。`editorViewOptionsCtx` 会被展开进 EditorView
// 构造参数（@milkdown/core 的 editorView 里 `...options`）、进入 `view._props`，
// **无条件第一个被查到**，不依赖 `.use(clipboard)` 的注册顺序。
// 放在 config() 里改是安全的：config 先 ConfigReady → init(initTimerCtx: [ConfigReady])
// → schema → … → editorView 才建视图，这条链串行。

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
      // 纯文本粘贴：原样逐行贴，不当 markdown 解析（理由见上面那段注释）。
      ctx.update(editorViewOptionsCtx, (prev) => ({
        ...prev,
        handlePaste: (view, event) => {
          const { clipboardData } = event
          if (!clipboardData) return false
          // 有 text/html = 网页 / Word / 富文本；vscode-editor-data = 从 VS Code 复制
          // （原插件会按语法高亮建代码块）；光标在代码块里 = 原生逐行插入。
          // 这三种一律交回原插件，行为一点不动。
          if (clipboardData.getData('text/html') || clipboardData.getData('vscode-editor-data')) return false
          if (view.state.selection.$from.parent.type.spec.code) return false

          const raw = clipboardData.getData('text/plain')
          if (!raw) return false

          // 文本文件结尾那一个换行是"文件结束标记"不是内容，去掉，否则末尾凭空多一个空行
          const lines = raw.replace(/\r\n?/g, '\n').replace(/\n$/, '').split('\n')

          // 每个 \n → 一个真 hardbreak（渲染成 <br>）。空一行就是两个 \n = 两个 hardbreak，
          // 屏幕上就是一个空行；c/d/e 之间没有 \n 就是没有 hardbreak = 紧挨着。
          // 空了几行这么算出来是精确的——markdown 解析做不到，它把 \n\n 和 \n\n\n 一起折叠。
          const { schema } = view.state
          const content = []
          lines.forEach((line, i) => {
            if (i > 0) content.push(schema.nodes.hardbreak.create({ isInline: false }))
            if (line) content.push(schema.text(line))
          })
          view.dispatch(
            view.state.tr.replaceSelection(
              new Slice(Fragment.from([schema.nodes.paragraph.create(null, content)]), 0, 0)
            )
          )
          return true
        }
      }))
    })
    .use(listener)
    .use(commonmark)
    .use(gfm)
    // 通用交互能力：项目走"单独引入"模式没用 @milkdown/kit 聚合包，
    // history / clipboard 都装在 node_modules 但未 use——这一行补齐 undo/redo + markdown 粘贴解析。
    .use(history)
    .use(clipboard)
    // Ctrl+A 全选 + Mod-Enter 提交 + Enter 跳出代码块（上面三个 keymap plugin）
    .use(selectAllKeymap)
    .use(submitKeymap)
    .use(exitCodeBlockKeymap)
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
/* 工具栏：透明底、无边框、灰字的一排。底边一条线把操作台和标题/正文分开。
   按钮原来是 <el-button size="small">，EP 默认给的是白底 + 浅边框 + 圆角，
   一排七个框看着像一组表单控件而不是工具栏（用户原话："这些按钮本身我觉得不好看"）。
   换 text 变体去掉边框和底色；hover 底色我们自己给——EP 的 .is-text:hover 用的是
   --el-fill-color-light(#f7f8fa)，和这里的 --cc-surface-2(#f4f6f8) 不是同一个色。 */
.md-toolbar {
  display: flex;
  align-items: center;
  gap: calc(2px * var(--ui-scale));
  flex-wrap: wrap;
  padding-bottom: calc(6px * var(--ui-scale));
  border-bottom: 1px solid var(--cc-border);
}
.md-toolbar .el-button {
  height: calc(26px * var(--ui-scale));
  margin: 0;
  padding: 0 calc(7px * var(--ui-scale));
  font-size: calc(13px * var(--ui-scale));
  color: var(--cc-text-2);
  border-radius: var(--cc-radius-sm);
}
.md-toolbar .el-button:hover {
  background: var(--cc-surface-2);
  color: var(--cc-text-1);
}
.milkdown-editor-body .ProseMirror {
  /* 高度三段式，跟内容走：
     min-height 135 —— 空编辑器只占这么点。随手记是"随手记一下"，空着的时候
       占掉小半屏会把下面的卡片列表挤到首屏之外，是纯粹的浪费。
     不设 height     —— 内容越长框越高，长笔记不会被塞进小窗口里挪滚动条。
     max-height 80vh —— 涨到约一屏就封顶，这时 overflow-y 才开始起作用出内层滚动条。
       没有这个上限的话，一条超长笔记能把整个页面顶到几千像素，页面本身的滚动条
       变成唯一的导航手段，翻找任一段都要滚很久。
     这三个值随手记和知识点详情页共用一份，不按实例区分：详情页读长笔记时靠
     "跟着内容长"顶起来，旧的 270px 封顶反而是它嫌小的原因。 */
  min-height: calc(135px * var(--ui-scale));
  max-height: 80vh;
  overflow-y: auto;
  outline: none;       /* 去掉浏览器对 contenteditable 默认画的 focus outline（黑色长方形边框） */
  /* 内边距交给外层容器（知识点详情页的 .content 自己给），这里不再叠一层。
     原来这里是 8px 12px，和外层的 14px 18px 叠成双层内缩，正文离框线过远。 */
  padding: 0;
  /* 字号 / 行高：原来**根本没设**，contenteditable 继承浏览器默认 16px。
     知识点的正文比它在列表卡片里的摘要（12.5px）还大一档，同一条知识在两个页面
     看着像两种东西，一行放不下几个字。 */
  font-size: calc(14px * var(--ui-scale));
  line-height: 1.75;
  color: var(--cc-text-1);
  word-break: break-word;
}
/* 沉浸模式（首页全屏录入 / 详情页全屏）：解除三段式高度，改成"写满一屏"。
   is-immersive 由 AppLayout 挂在 .app-content 上。

   规则写在这个全局块里而不是业务 view 的 scoped 块：.ProseMirror 是 Milkdown
   渲染出来的，节点上没有业务文件的 data-v，从那边 :deep 过来不可靠
   （AppHomeView 当年就是这么写的，注释还留着）。

   用 min-height: 60vh 而不是 flex 链撑满：Milkdown 渲染出来的中间层
   （MilkdownProvider 是不是吐节点、data-milkdown-root / .milkdown）
   是第三方内部结构，链式传高度随时会断。给一个大底高最稳：
   空的时候是一整屏可点的书写面，长了跟着涨，页面自己滚。 */
.is-immersive .milkdown-editor-body .ProseMirror {
  max-height: none;
  min-height: 60vh;
}

/* ---------- 软换行 ----------
   markdown 段落里的单换行（软换行）在 milkdown 里**不是** "\n"，也不是 `<br>`：
   `remark-line-break.ts` 插件（`composed/plugins.ts` 里确实注册了）会把 text 节点里的每个
   \n 拆成一个 mdast `break` 节点并打上 `data.isInline = true`；`hardbreak.ts` 的 toDOM 对
   isInline=true 渲染成 **`<span data-type="hardbreak" data-is-inline="true"> </span>`
   ——一个装着空格的 span**（源码注释原话："The inline line break should be treated as
   a `space`"）。库里已存的、带软换行的笔记重新打开就会全部并成一行。

   这条只负责**渲染**已经存在的软换行（新粘贴的内容走上面的 handlePaste，已经是 <br>，
   不经过这个 span）。真 hardbreak（Shift+Enter，isInline=false）渲染的是 <br>，
   选择器只认 span，两条路互不干扰。

   ::after 里用 '\A'（CSS 换行转义）而不是改 span 本身：span 里那个空格是节点自带的，
   删掉会改变 doc 的可选择文本。留一个行尾空格本来也就是标准 markdown 硬换行
   （行尾两空格 / 反斜杠）在视觉上的样子。 */
.milkdown-editor-body .ProseMirror span[data-type="hardbreak"]::after {
  content: '\A';
  white-space: pre;
}
/* ---------- 列表 ----------
   全局 reset（base.css 的 `* { margin: 0; padding: 0 }`）把浏览器默认那套
   40px 缩进和项目符号挂位**全归零了**，prosemirror.css 自己也没有任何列表规则。
   不写这一段，列表是"生成了但没法看"：没有缩进，bullet 顶在正文最左边。

   缩进用 em 而不是 px：1.6em ≈ 22px，跟着字号走；浏览器默认的 40px 在 920px
   的阅读栏里会把列表项的可用宽度吃掉一大截。

   **不加纵向 margin**：段落间距由编辑器渲染层负责，这里不重复给。 */
.milkdown-editor-body .ProseMirror ul,
.milkdown-editor-body .ProseMirror ol {
  padding-left: 1.6em;
}
/* 项目符号压到 text-3：默认的纯黑圆点在一屏文字里太抢眼，
   而项目符号是"这一项属于某个列表"的提示，不是内容本身。 */
.milkdown-editor-body .ProseMirror li::marker {
  color: var(--cc-text-3);
}
/* 行内代码 / 代码块：知识库里大量是代码，不上等宽字体没法读。
   prosemirror 官方样式表只管排版不管字体，默认还是正文的无衬线。 */
.milkdown-editor-body .ProseMirror code {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: calc(13px * var(--ui-scale));
  background: var(--cc-surface-2);
  color: #c7254e;
  padding: calc(1px * var(--ui-scale)) calc(5px * var(--ui-scale));
  border-radius: calc(4px * var(--ui-scale));
}
.milkdown-editor-body .ProseMirror pre {
  background: var(--cc-surface-2);
  border-radius: calc(8px * var(--ui-scale));
  padding: calc(12px * var(--ui-scale)) calc(14px * var(--ui-scale));
  overflow-x: auto;
  margin: calc(10px * var(--ui-scale)) 0;
}
.milkdown-editor-body .ProseMirror pre code {
  background: transparent;
  color: var(--cc-text-1);
  padding: 0;
  font-size: calc(13px * var(--ui-scale));
  line-height: 1.7;
}
.milkdown-editor-body .ProseMirror blockquote {
  margin: calc(10px * var(--ui-scale)) 0;
  padding: calc(2px * var(--ui-scale)) 0 calc(2px * var(--ui-scale)) calc(12px * var(--ui-scale));
  border-left: 2px solid var(--cc-border-strong);
  color: var(--cc-text-2);
}
.milkdown-editor-body .ProseMirror a {
  color: var(--cc-primary);
  text-decoration: none;
}
.milkdown-editor-body .ProseMirror a:hover {
  text-decoration: underline;
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
  gap: calc(12px * var(--ui-scale));
  padding: calc(4px * var(--ui-scale)) 0;
}
.table-dialog__row {
  display: flex;
  align-items: center;
  gap: calc(12px * var(--ui-scale));
}
.table-dialog__label {
  width: calc(50px * var(--ui-scale));
  font-size: calc(14px * var(--ui-scale));
  color: #606266;
}

/* 表格右键菜单：fixed 定位（不依赖父容器 position），z-index 提到 el-dialog 之上避免被遮 */
.ctx-menu {
  position: fixed;
  z-index: 9999;
  min-width: calc(140px * var(--ui-scale));
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: calc(4px * var(--ui-scale));
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: calc(4px * var(--ui-scale)) 0;
  font-size: calc(14px * var(--ui-scale));
  color: #303133;
}
.ctx-menu__item {
  padding: calc(8px * var(--ui-scale)) calc(16px * var(--ui-scale));
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
  margin: calc(4px * var(--ui-scale)) 0;
  height: calc(1px * var(--ui-scale));
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
  margin: calc(8px * var(--ui-scale)) 0;
  overflow: hidden;
}
.milkdown-editor-body .ProseMirror th,
.milkdown-editor-body .ProseMirror td {
  border: 1px solid #dcdfe6;
  padding: calc(6px * var(--ui-scale)) calc(10px * var(--ui-scale));
  vertical-align: top;
  min-width: calc(80px * var(--ui-scale));
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
</style>
