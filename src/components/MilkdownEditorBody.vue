<template>
  <div class="milkdown-editor-body">
    <!-- 工具栏：简洁模式下整行不渲染（"简洁"就是只要正文）。
         底边一条线把操作台和标题/正文分开——工具栏是"操作台"，不该和内容混在一块。
         开关按钮不在这里，它由使用方放在自己的顶栏里（见 MilkdownEditor.vue 顶部注释）。 -->
    <!-- @mousedown.prevent：点工具栏按钮**不夺走编辑器的选区**。
         默认行为是焦点跑到按钮上，ProseMirror 收不到后续输入，"插入链接"这类
         "拿当前选区当作用对象"的动作就没对象了。整条工具栏统一挡掉，
         免得每加一个依赖选区的按钮就漏一个。 -->
    <!-- 两排按钮的提示统一走 el-tooltip，不再用原生 title：
         原生 title 的出现延迟是**浏览器定的**（Windows Chrome 约 1~2 秒），
         样式也管不了。工具栏是"扫一眼就知道是什么"的控件，这个延迟是纯损耗。
         show-after 压到 100ms —— 立即出，又不至于鼠标扫过一排时闪成一片。
         ⚠️ 按钮套 el-tooltip 必须外面再包一层 <span>：<button disabled> 不派发鼠标事件，
         直接套的话"插入链接（先选中文字）"这条**最该被看到的提示反而永远看不到**。 -->
    <!-- 顶栏（色带）：**格式组靠左、右端是页面注入的集群**（文件动作 + 类型）。
         ⚠️ **集群与顶栏同生共死**（2026-11-16 用户：「这两个是跟顶栏一起的 显示也是一起的」）：
         页面把集群注入 `toolbar-end` 插槽 ⇒ 它住在**顶栏右端**（原来放在标题行右端，
         被判"不是右上角"）；顶栏隐藏时它一起隐藏（简洁模式就是没有这一行）。 -->
    <div v-if="showToolbar" class="md-toolbar" @mousedown.prevent>
      <!-- **分组**（2026-11-15/16）：四组格式（文字 / 块 / 链接 / 字号字体），组间一条细线；
           组间分隔项是数组里的 { sep: true }（一个 v-for 渲染两种东西，别在模板里再分组）。
           ⚠️ **图标全部自绘**（KbSvgIcon 那一族 1.7px 细线）：原来用的是 EP 老图标
           （粗描边、方块感），而 meta 行是自绘的 ⇒ 同一屏两种画风。 -->
      <template v-if="showToolbar">
        <template v-for="b in MD_BUTTONS" :key="b.id">
          <span v-if="b.sep" class="md-sep" />
          <KbTooltip v-else :content="b.tip" placement="bottom" :show-after="100">
            <span class="md-btn-wrap">
              <el-button text size="small" :class="b.cls" :aria-label="b.tip" :disabled="b.disabled" @click="b.run($event)">
                <KbSvgIcon v-if="b.icon" :name="b.icon" />
                <template v-else>{{ b.text }}</template>
              </el-button>
            </span>
          </KbTooltip>
        </template>
      </template>
      <!-- 导入：input 藏在工具栏里，点按钮弹**系统文件选择器**（页面调 pickImportFile()）。 -->
      <input
        ref="importInput"
        type="file"
        accept=".md,.markdown,text/markdown,text/plain"
        class="md-import-input"
        @change="onImportFile"
      />
      <!-- 右端集群：弹性空档把它顶到最右（页面注入：文件动作 + 类型） -->
      <span class="md-fill" />
      <slot name="toolbar-end" />
    </div>

    <!-- 使用方塞进来的东西（现在是知识点详情页的标题）。位置：工具栏之下、正文之上。
         插槽而不是让使用方把工具栏搬上来，是因为工具栏必须在 MilkdownProvider 内部
         才能拿到编辑器 ctx；反过来把标题递进来，顺序就对了。

         **外面套一层 div 是为了对齐**：标题和正文必须落在同一条左边缘上
         （语雀那样，标题就是正文的第一行）。而正文左边有一条给折叠箭头留的
         空白（--kb-gutter），标题不跟着缩进的话就会贴在框边、比正文缩进去一截。
         这层 div 的 margin/width 由 kb-editor.css 的 .editor__before 给，
         用的就是正文那条同一个 --kb-gutter ⇒ 两边永远是一条线。 -->
    <div class="editor__before">
      <slot name="before-content" />
    </div>

    <Milkdown />

    <!-- 链接浮层：正文里单击一个链接就出现（Ctrl/⌘+单击直接新标签打开，不经过它）。
         **2026-11-09 合并成一条**（用户拍板 ui-preview/link-pop.html）：AI 动作与链接
         四动作（访问 / 编辑 / 复制 / 取消）同排一条 —— 链接场景**不再叠独立的选区浮层**
         （原来「问 AI」胶囊会压住「访问」等按钮，见 syncAskFloat 的链接选区判据）。
         AI 是两态：先按钮，点了才展开输入框（输入框聚焦会吃掉编辑器选区，见 ask-float 注释）。
         @mousedown.prevent：点了不夺编辑器焦点，否则下面两个依赖选区的动作（编辑 / 取消）
         会因为选区被收成空而反过来给光标处**加**一个链接（输入框自己 @mousedown.stop 豁免）。
         定位锚 = 链接 range：滚动/改窗时 reanchorFloat 跟着重算，贴着链接走。 -->
    <div
      v-if="linkBar.visible"
      ref="linkBarEl"
      class="editor-float link-pop"
      :class="{ 'is-ask': linkBar.askOpen, 'is-away': linkBar.away }"
      :style="{ left: linkBar.x + 'px', top: linkBar.y + 'px' }"
      @mousedown.prevent
      @click.stop
    >
      <template v-if="!linkBar.askOpen">
        <KbTooltip content="让 AI 解读选中的内容" placement="top" :show-after="100">
          <button type="button" class="float-ai" aria-label="AI 解读" @click="openLinkAsk()">
            <span class="float-ico"><KbSvgIcon name="sparkle" /></span>
            AI 解读
          </button>
        </KbTooltip>
        <div class="link-pop__sep" />
        <KbTooltip v-for="b in visibleLinkButtons" :key="b.tip" :content="b.tip" placement="top" :show-after="100">
          <button type="button" class="link-pop__act" :class="{ 'is-danger': b.id === 'unlink' }" :aria-label="b.tip" @click="b.run()">
            <KbSvgIcon :name="b.icon" />
          </button>
        </KbTooltip>
      </template>
      <template v-else>
        <input
          ref="linkAskInput"
          v-model="linkBar.value"
          class="float-ai-input"
          aria-label="AI 解读"
          placeholder="补充一句你想弄明白的…"
          @mousedown.stop
          @keyup.enter="submitLinkAsk()"
        />
        <button type="button" class="float-send" aria-label="发送" @click="submitLinkAsk()">
          <KbSvgIcon name="arrow-up" />
        </button>
      </template>
    </div>

    <!-- URL 输入气泡：「插入链接」和工具条的「编辑链接」共用这一个。
         不用居中对话框：选区和输入框分处两块地方，视线要来回跳（见 insertLink 注释）。 -->
    <div
      v-if="linkInput.visible"
      ref="linkInputElBox"
      class="editor-float link-input"
      :style="{ left: linkInput.x + 'px', top: linkInput.y + 'px' }"
      @click.stop
    >
      <el-input
        ref="linkInputEl"
        v-model="linkInput.value"
        size="small"
        placeholder="https://..."
        @keyup.enter="confirmLinkInput()"
      />
      <el-button size="small" type="primary" @click="confirmLinkInput">确定</el-button>
    </div>

    <!-- 目录选择气泡：「设为目录链接」用。和上面那个 URL 气泡同一套浮层机制（showFloat），
         里面装的东西不同——树形选择器，而不是一个要手打的地址框。
         两个气泡互斥出现（都是"先选中文字再点工具栏"），所以放在一起，彼此不会同时在场。
         filterable：目录一多，光靠点开层级找一条会很慢，打两个字能筛。
         teleported=false：下拉不传送到 body，留在浮层里——浮层是 fixed 的，
         传送到 body 会跑到 body 下的另一个层叠上下文里，被正文盖住。
         placeholder 留空串而不是删属性：EP 的 select 对 undefined 会 `??` 兜底出语言包
         默认的「请选择」（2.9.10 useSelect 已核），空串才是真的什么都不显示。 -->
    <div
      v-if="catLink.visible"
      ref="catLinkEl"
      class="editor-float cat-link"
      :style="{ left: catLink.x + 'px', top: catLink.y + 'px' }"
      @click.stop
    >
      <el-tree-select
        v-model="catLinkValue"
        :data="categoryTree"
        :props="{ label: 'label', children: 'children' }"
        :render-after-expand="false"
        :filterable="true"
        :teleported="false"
        node-key="value"
        size="small"
        placeholder=""
        @change="onCategoryPicked"
      />
    </div>

    <!-- 工具菜单浮层（2026-11-15）：标题档位 / ··· 更多 / 导出 三种内容共用一张脸，
         和字号字体浮层（format-pick）同一套机制——贴着按钮弹、点外面收、Esc 收。
         soon 项（PDF / Word）不禁用不隐藏：入口常驻可感知，点了给一句官方说法。 -->
    <div
      v-if="toolMenu.visible"
      ref="toolMenuEl"
      class="editor-float tool-menu"
      :style="{ left: toolMenu.x + 'px', top: toolMenu.y + 'px' }"
      @mousedown.prevent
      @click.stop
    >
      <button
        v-for="it in toolMenuItems"
        :key="it.key"
        class="tool-menu__item"
        @click="onToolMenuPick(it)"
      >
        {{ it.label }}<span v-if="it.soon" class="tool-menu__soon">暂不能使用</span>
      </button>
    </div>

    <!-- 字号 / 字体档位浮层：点工具栏的 Aa / 字体按钮弹出来，选一档就套到选区上。
         和链接浮层同一套机制（showFloat 贴着选区），一个浮层两种内容。 -->
    <div
      v-if="formatPick.visible"
      ref="formatPickEl"
      class="editor-float format-pick"
      :style="{ left: formatPick.x + 'px', top: formatPick.y + 'px' }"
      @mousedown.prevent
      @click.stop
    >
      <button
        v-for="opt in formatOptions"
        :key="opt.key"
        class="format-pick__item"
        :style="opt.preview ? { fontSize: opt.preview } : null"
        @click="pickFormat(opt)"
      >
        {{ opt.label }}
      </button>
    </div>

    <!-- 选区浮层：正文里选中一段就出现一个「AI 解读」按钮；点了才展开输入框，
         可补一句问题后交给 AI 解释。
         和上面几个浮层同一套机制（showFloat 贴着选区，reanchorFloat 跟随滚动）。
         **只在 AI 开着时才有**（askEnabled）——没开的人不该看见 AI 的影子，
         而编辑器是通用组件，不该自己去问"AI 开了没"（那件事归上层）。
         留空回车 = 用默认问题（就是"解读这一段"）。
         ⚠️ **链接的选区不弹这个浮层**（2026-11-09）：AI 入口在链接浮层里，
         两个浮层叠在同一处会互相压住（syncAskFloat 的 isLinkOnlySelection 判据）。

         ⚠️ **为什么先弹按钮、再弹输入框**（2026-11-05 用户定）：
         正文的工具栏（插入链接 / 字号 / 字体…）**全都靠当前选区做事**，
         而输入框一聚焦，编辑器就失焦、选区随之没了 —— 那几个按钮就都成了空转。
         所以第一态只给一个按钮；按钮 @mousedown.prevent，**不夺编辑器的焦点**，
         选区还在，工具栏照样能用。 -->
    <div
      v-if="askFloat.visible"
      ref="askFloatEl"
      class="editor-float ask-float"
      :class="{ 'is-open': askFloat.open }"
      :style="{ left: askFloat.x + 'px', top: askFloat.y + 'px' }"
      @click.stop
    >
      <button
        v-if="!askFloat.open"
        type="button"
        class="float-ai"
        aria-label="AI 解读"
        @mousedown.prevent
        @click.stop="openAskFloat()"
      >
        <span class="float-ico"><KbSvgIcon name="sparkle" /></span>
        AI 解读
      </button>
      <template v-else>
        <input
          ref="askFloatInput"
          v-model="askFloat.value"
          class="float-ai-input"
          aria-label="AI 解读"
          @keyup.enter="submitAskFloat()"
        />
        <button
          type="button"
          class="float-send"
          aria-label="发送"
          @mousedown.prevent
          @click="submitAskFloat()"
        >
          <KbSvgIcon name="arrow-up" />
        </button>
      </template>
    </div>

    <!-- 表格右键菜单：监听 document contextmenu，过滤"在 .ProseMirror 的 td/th 内"
         时弹出（preventDefault 阻止浏览器原生菜单）；菜单 4 项 = 上下插行 + 左右插列 -->
    <div
      v-if="ctxMenu.visible"
      class="editor-float ctx-menu"
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
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Link, Grid, List, Rank, FolderOpened } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Milkdown, useEditor } from '@milkdown/vue'
import { Editor, rootCtx, commandsCtx, defaultValueCtx, editorViewOptionsCtx, editorViewCtx, parserCtx } from '@milkdown/core'
import { $useKeymap, $prose, replaceAll } from '@milkdown/utils'
import { Fragment, Slice } from '@milkdown/prose/model'
import { Plugin, TextSelection } from '@milkdown/prose/state'
import { listener, listenerCtx } from '@milkdown/plugin-listener'
import { history } from '@milkdown/plugin-history'
import { clipboard } from '@milkdown/plugin-clipboard'
import { commonmark, linkSchema } from '@milkdown/preset-commonmark'
import KbSvgIcon from './corecraft-web/KbSvgIcon.vue'
import KbTooltip from './corecraft-web/KbTooltip.vue'
import { gfm } from '@milkdown/preset-gfm'
import { exitCode, joinBackward, selectAll } from 'prosemirror-commands'
import { copyText } from '../utils/clipboard'
import { uploadFile, fileUrl } from '../api/file'
import { imageNodePlugin, imageResizeView } from './kbImageNode'
import { fileNodePlugin, fileNodeView } from './kbFileNode'
import { mermaidPlugin } from './kbMermaid'
import { $applyToVisualLines } from './kbLineBlocks'
import { fontSchema, remarkFontSpan, remarkFontSpanStringify, setFontCommand, clearFormattingCommand, FONT_SIZES, FONT_FAMILIES } from './kbFontMark'
import { fixEmphasisTail } from '../utils/kbMarkdown'
import { CATEGORY_HREF_PREFIX, TOPIC_HREF_PREFIX } from '../utils/categoryNav'
import { foldPlugin, getOutline, scrollToHeading as scrollToHeadingInDoc } from './kbHeadingFold'
import { editorEmits, editorProps } from './milkdownEditorContract'
import {
  searchHighlightPlugin,
  searchHighlightKey,
  setSearchKeyword as setSearchKeywordInView,
  gotoMatch as gotoMatchInView
} from './kbSearchHighlight'
import { autoLinkPlugin, setAutoLinks as setAutoLinksInView } from './kbAutoLink'

/**
 * 从视图里读搜索状态（命中列表 / 游标）。
 *
 * <p>⚠️ **读 plugin state 而不是自己存一份**：装饰画在文档上、计数显示在界面上 ——
 * 两份状态各算一遍就会分裂（界面上写着 3 个命中、实际高亮 5 处）。
 */
function getSearchState(view) {
  if (!view) return null
  return searchHighlightKey.getState(view.state)
}

// 必须赋值给变量：Vue 3 <script setup> 里 defineProps 不赋值时，编译器只在模板把它当 props，
// script 代码里访问 props.xxx 会 ReferenceError（这次踩了，所以 props.modelValue 用上时挂了）
// ⚠️ **props / emits 的契约只有一份**（`milkdownEditorContract.js`）：本组件是
// `MilkdownEditor.vue`（薄封装）的实现，父子必须认同一份 ⇒ 改一处两边一起改。
const props = defineProps(editorProps)
const emit = defineEmits(editorEmits)

/**
 * markdown → ProseMirror doc（**用编辑器自己的解析器**）。解析不了返回 null。
 *
 * **只有一个出口**：写回正文（`replaceContent`）和对比区渲染（`docOf`）
 * 要的是同一件事 —— "用编辑器这套解析器把 markdown 变成 doc"。
 * 以前这两个出口各写一遍 `editor.action(ctx => ctx.get(parserCtx))`，
 * 改一处（比如换了 schema）就只改了一半 ⇒ **收成这一个函数**。
 *
 * **doc 里有 schema**（`doc.type.schema`）⇒ 调用方不用再拿 schema 就能序列化。
 */
function parseDoc(markdown) {
  const editor = get()
  if (!editor) return null
  let doc = null
  editor.action((ctx) => {
    const parse = ctx.get(parserCtx)
    if (!parse) return
    doc = parse(markdown) || null
  })
  return doc
}

/**
 * 暴露给外层用的方法。**大纲点击跳转要走这条**：
 * 编辑器在 `MilkdownProvider` 里面，外层拿不到它的 ctx，只能由这里代劳。
 *
 * **这里没有折叠相关的出口**：折叠是正文里的事（鼠标移到标题上出现的那个箭头），
 * 大纲那栏折的是它自己，不碰正文（2026-10-21 用户定）。
 */
defineExpose({
  /** 滚到指定标题（按 id 定位），返回是否找得到 */
  scrollToHeading(id) {
    const view = getView()
    if (!view) return false
    return scrollToHeadingInDoc(view, id)
  },
  /**
   * 正文关键词检索：设词（边打字边调）⇒ 高亮所有命中。
   *
   * @returns {number} 命中处数（0 = 没命中）
   */
  searchInBody(keyword) {
    const view = getView()
    if (!view) return 0
    setSearchKeywordInView(view, keyword)
    const st = getSearchState(view)
    return st ? st.matches.length : 0
  },
  /**
   * 跳到第 n 个命中（0 起）并滚过去。返回是否跳成功。
   */
  gotoMatch(index) {
    const view = getView()
    if (!view) return false
    return gotoMatchInView(view, index)
  },
  /** 当前命中处数（外面算「N 个命中」用） */
  searchHitCount() {
    const view = getView()
    if (!view) return 0
    const st = getSearchState(view)
    return st ? st.matches.length : 0
  },
  /**
   * 知识点自动链接：给正文里**首次出现**的其它知识点标题加链接装饰。
   *
   * @param {Array<{term:string,id:number,href?:string}>} entries 词表（标题 → 目标）
   */
  setAutoLinks(entries) {
    const view = getView()
    if (!view) return
    setAutoLinksInView(view, entries)
  },
  /** 当前在第几个命中（1 起；0 = 还没定位） */
  searchHitIndex() {
    const view = getView()
    if (!view) return 0
    const st = getSearchState(view)
    return st ? st.activeIndex + 1 : 0
  },
  /**
   * 整篇替换正文（AI 改写后写回）。返回有没有替换成功。
   *
   * **为什么外面改 v-model 不行**：`props.modelValue` 只在初始化时被
   * `ctx.set(defaultValueCtx, ...)` 读一次（见上面那段注释），之后编辑器内部的变化
   * 只靠 `markdownUpdated` 往上报。外层把 modelValue 换成新内容，
   * ProseMirror 视图里渲染的还是旧 doc —— **界面看着没变**，只有别处读到的值变了。
   * 详情页原来靠 `:key="topic.id"` 重建编辑器来换内容，正是因为这条。
   *
   * **走官方宏 `replaceAll`**（`@milkdown/utils`，这个文件本来就在用它）：
   * 它内部就是「解析成 doc → 整个 doc 换掉」这一下事务 ⇒ 进编辑器自己的 undo 栈
   * （history 插件已装），用户按 Ctrl+Z 能退回去；替换完 `markdownUpdated` 照常上报，
   * 外层的 v-model 和自动保存那条链自动跟上。
   *
   * **先自己解析一遍，只为知道成不成功**（`replaceAll` 不给返回值）：
   * 解析不了就别动编辑器 —— 调用方要能告诉用户「没写进去」，而不是假装成功。
   */
  replaceContent(markdown) {
    if (!parseDoc(markdown)) return false
    get().action(replaceAll(markdown))
    return true
  },
  /**
   * **换一条知识点**：整篇替换正文，**并且清空撤销栈**。
   *
   * <p>和 `replaceContent` 的区别只有一处，但那一处很要紧：
   * `replaceContent`（AI 改写写回）**故意留在撤销栈里** —— 用户按 Ctrl+Z 能退回去。
   * 而换知识点**必须清栈**：编辑器是复用的（详情页不再每条重建一个 Milkdown），
   * 栈不清的话 Ctrl+Z 会把**上一条知识点**的内容撤回来，
   * 而自动保存那条链会把那份内容**写进当前这条** ⇒ 串内容。
   *
   * <p>**清栈怎么做的**（prosemirror-history 没有公开的 clear API）：
   * 它的 apply 里有一条 `let historyTr = tr.getMeta(historyKey); if (historyTr) return historyTr.historyState`
   * ⇒ 用 meta 塞一份**空的历史状态**进去就行；
   * 而"空的历史状态"可以问插件自己要：`plugin.spec.state.init()`。
   * ⚠️ **这条依赖 prosemirror 的内部结构** ⇒ 已在 Node 里验过
   * （撤销深度 1 → 0、正文不变、undo 直接返回 false = 撤不动）。
   * 哪天 prosemirror 改了这里，症状是「Ctrl+Z 能撤出上一条知识点」——
   * 见到这个，先查这段 meta 还能不能生效。
   */
  switchContent(markdown) {
    const doc = parseDoc(markdown)
    if (!doc) return false
    const view = getView()
    if (!view) return false
    // 换内容，**不进撤销栈**
    const tr = view.state.tr.replaceWith(0, view.state.doc.content.size, doc.content)
    tr.setMeta('addToHistory', false)
    // ⚠️ **换内容不许上报**：外层拿到新值会当成"用户改了正文" ⇒ 触发自动保存
    // （把当前这条的正文又存一遍）。换知识点是**载入**，不是编辑。
    suppressEmit = true
    view.dispatch(tr)
    // 同步回调已经跑完；再兜一个微任务（万一插件把回调推迟了）
    Promise.resolve().then(() => { suppressEmit = false })
    // 再把栈清空（上面的 meta 只是让这一次替换不进栈，旧的那一叠还在）
    const plugin = view.state.plugins.find((p) => p.key.startsWith('history$'))
    if (plugin) {
      view.dispatch(view.state.tr.setMeta(plugin.spec.key, { historyState: plugin.spec.state.init() }))
    }
    return true
  },
  /** markdown → ProseMirror doc（**不建视图**）。解析不了返回 null */
  docOf: parseDoc,
  /**
   * **打开导出菜单**（贴在给定按钮下面）——文件动作由页面自己画（要和「类型」并列
   * 放在页面右上角），导出这件实情仍归编辑器（它有原文、它有菜单）。
   *
   * @param {HTMLElement} anchor 按钮元素
   */
  openExportMenu(anchor) {
    openToolMenu('export', anchor)
  },
  /**
   * **弹系统文件选择器**（导入 .md）——同上，导入的实情在编辑器里（读文件、解析、
   * 插到光标处），页面只负责画那颗按钮。
   */
  pickImportFile() {
    pickImport()
  },
  /**
   * 一段范围里的**顶层块**（对比区用）。
   *
   * @param from 起点（doc 位置）
   * @param to   终点（doc 位置）
   * @returns {Array<{node: object, from: number, to: number}>} 相交的顶层块
   */
  blocksInRange(from, to) {
    const view = getView()
    if (!view) return []
    return blocksInRangeNodes(view.state.doc, from, to)
  },
  /**
   * 在某段块**之后**写入新内容（markdown）。
   *
   * ## 为什么是「整段替换」而不是 insert
   *
   * 直接 `insert` 到块边界上，ProseMirror 的 fitter 可能把新块并进相邻的段落
   * （"插一段进去，结果变成一段"）—— 结果不确定。
   * 整段替换的结果是确定的：**原来那几块原样保留，后面接上新块**。
   *
   * @param blockFrom 起点块的位置（doc 位置）
   * @param blockTo   终点块之后的位置
   * @param markdown  要写进去的内容
   * @returns 是否写进去了
   */
  insertAfter(blockFrom, blockTo, markdown) {
    const view = getView()
    if (!view) return false
    const blocks = blocksInRangeNodes(view.state.doc, blockFrom, blockTo)
    if (!blocks.length) return false
    const parsed = parseDoc(markdown)
    if (!parsed) return false
    const added = []
    parsed.content.forEach((node) => added.push(node))
    if (!added.length) return false
    const tr = view.state.tr.replaceWith(
      blockFrom,
      blockTo,
      Fragment.from([...blocks.map((b) => b.node), ...added])
    )
    // 写完正文会变 ⇒ 视图更新 ⇒ 选区还非空的话浮层会立刻弹回来，
    // 正好挡在刚写进去的内容上面。这一下不许它弹。
    suppressAskFloat = true
    view.dispatch(tr)
    suppressAskFloat = false
    hideAskFloat()
    return true
  }
})

// 点目录链接时跳列表页。**和侧边栏点目录走的是同一个地址**（AppLayout 的 goCategory
// 也是 router.push 到 /app/learn?category=id），所以正文里点进去看到的东西
// 和从左栏点进去完全一样。这里必须用 router 而不是 <a href>：后者是整页刷新，
// 会把 AppLayout 已经拉好的目录树、菜单全部丢掉重来。
const router = useRouter()

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

// ---------- 链接交互 ----------
// 链接浮层（2026-11-09 合并一条）：正文里单击一个链接就出现，
// 「AI 解读」+ 四个动作（访问 / 编辑 / 复制 / 取消）同排。
// 光标/选区落在链接上的那串文档位置（不是 href）：编辑和取消都要作用在文档上，
// 只有 href 的话，"取消链接"根本不知道该摘哪几个字的标记。
//
// **range 收在 state 里，不另开模块级变量**：另开一份就得靠"显示时赋值、隐藏时手工置 null"
// 两边手动同步，漏写一次的症状是浮层还指着上一段文字——而且不报错，只是点取消链接摘错了地方。
// away = 锚点滑出编辑器可视区（reanchorFloat 置，CSS 渐隐）。
/**
 * 链接工具条的状态。
 *
 * <p>`temp` = 这条链接是**渲染层临时算出来的**（正文自动链接：Decoration，不是 link mark）。
 * 它只影响按钮列表 —— 「编辑链接 / 取消链接」改的是 doc 里的 mark，对装饰没有作用对象，
 * 摆上去就是"点了没反应"。见 {@link visibleLinkButtons}。
 */
const linkBar = ref({ visible: false, x: 0, y: 0, href: '', range: null, askOpen: false, value: '', away: false, temp: false })
// URL 输入气泡：「插入链接」和「编辑链接」共用这一个 —— 两种入口做的是同一件事
// （给某段字改一个 URL），拆成两个弹层就是同一份逻辑写两遍。
// **cmd 而不是 mode**：两个入口唯一的差别就是"确定之后跑哪条命令"，
// 存 mode 再在确定时翻译成命令，是同一件事的第二份说法。
const linkInput = ref({ visible: false, x: 0, y: 0, value: '', cmd: 'ToggleLink', range: null })
// 目录选择气泡：「设为目录链接」用。和 URL 气泡是**同一类浮层**（贴着选区、点完即走），
// 区别只是里面装的东西——一个 el-tree-select 而不是手打的输入框。
// 存 value 而不是"选中的那棵树上的节点"：存节点就得存一份目录数据的副本，
// 而这个气泡开着期间目录可能被别处改了，副本会过期。
const catLink = ref({ visible: false, x: 0, y: 0, range: null })
// 插入按钮的置灰依据：没有选中文字就不该能点（"先选中再点"是这个功能的前提）
const hasSelection = ref(false)
const linkBarEl = ref(null)
const linkAskInput = ref(null)
const linkInputElBox = ref(null)
const linkInputEl = ref(null)
const catLinkEl = ref(null)
const catLinkValue = ref(null)

/**
 * 上一次报给外部的大纲签名。**只报"结构变了"**：正文改字不动标题时大纲没变，
 * 每次打字都 emit 会把右侧栏拖垮。
 *
 * ⚠️ **初值必须是 null，不能是 ''**：
 * 没有标题的文档签名也是 ''（`[].join('|')` 就是空串），
 * 拿 '' 当初值 = "没标题"和"什么都没报过"是同一个值 ⇒ 第一次就被当重复拦掉
 * ⇒ **空大纲永远报不出去** ⇒ 页面留着上一条知识的大纲
 * （用户看到的"在一个没有目录的知识点里，挂着上一个知识点的大纲"）。
 * null 不可能是任何合法签名 ⇒ 第一次一定报得出去。
 */
let lastOutlineSig = null

/**
 * 把正文里的标题结构报给外层（右侧大纲栏用）。
 *
 * **为什么不能只挂在 `markdownUpdated` 上**：那个监听器第一次不会触发
 * （`prevDoc` 还是空的），而标题 id 是 `syncHeadingIdPlugin` 在第一次
 * update 时补的 —— 只等 markdownUpdated，初次打开的大纲永远是空的。
 * 所以就绪时也报一次。
 */
function emitOutline() {
  const view = getView()
  if (!view) return
  const outline = getOutline(view.state.doc)
  const sig = outline.map((o) => `${o.id}:${o.level}:${o.text}`).join('|')
  if (sig === lastOutlineSig) return
  lastOutlineSig = sig
  emit('outline', outline)
}

/**
 * 字号 / 字体的档位浮层。**一个浮层两种内容**（kind 决定列表）：
 * 两个按钮做的事只是"弹一个档位列表，选一个"——拆成两个浮层就是两份
 * 一模一样的壳。
 */
const formatPick = ref({ visible: false, x: 0, y: 0, kind: 'size', range: null })
const formatPickEl = ref(null)

// ---------- 选区提问（AI 解读） ----------
// 正文里选中一段 ⇒ 贴着一个「AI 解读」按钮 ⇒ 点它 ⇒ 展开输入框 ⇒ 补一句问题 ⇒ 把这段交给 AI 解释。
//
// **为什么第一态是按钮而不是输入框**（2026-11-05 用户定）：正文的工具栏
// （插入链接 / 字号 / 字体…）全都靠**当前选区**做事，而输入框一聚焦，编辑器就失焦、
// 选区随之没了 —— 那几个按钮就都成了空转。按钮 @mousedown.prevent，点了不夺焦点。
//
// 留空回车 = 用默认问题（"解读这一段"）。
// 提交负载（text / 块锚）提交时从 range 现算（askPayloadFor），状态里不存第二份。
const askFloat = ref({ visible: false, x: 0, y: 0, range: null, value: '', open: false, away: false })
const askFloatEl = ref(null)
const askFloatInput = ref(null)
/** 已经为这个选区弹过浮层。空串 = 还没弹过（也用来判"要不要再弹一次"） */
let askFloatKey = ''
/** 鼠标按着：拖选过程中不弹（等松手再弹，位置才是最终位置） */
let mouseDown = false
/** 写正文那一下不许弹浮层（写完正文一变，选区还非空 ⇒ 会立刻弹回来挡住刚写的内容） */
let suppressAskFloat = false

/**
 * 顶层块区间：doc 里那些**与选区相交**的顶层节点。
 *
 * **为什么自己遍历而不用 `resolve().start(1)`**：选区正好停在块边界时，
 * 那套算出来会把**下一块**也带上（用户根本没选它）—— 差一段就是"解释被写到不该在的地方"。
 * 这里按节点自己的起止判断相交，只认真正被划到的块。
 *
 * @param {object} doc ProseMirror doc
 * @param {number} from 起点（doc 位置）
 * @param {number} to 终点（doc 位置）
 * @returns {Array<{node: object, from: number, to: number}>}
 */
function blocksInRangeNodes(doc, from, to) {
  const out = []
  doc.forEach((node, offset) => {
    const bFrom = offset
    const bTo = offset + node.nodeSize
    if (bTo > from && bFrom < to) out.push({ node, from: bFrom, to: bTo })
  })
  return out
}

function hideAskFloat() {
  askFloatKey = ''
  if (askFloat.value.visible) askFloat.value = { ...askFloat.value, visible: false, open: false }
}

/**
 * 点「AI 解读」按钮 ⇒ 展开输入框并聚焦。
 *
 * **聚焦放在这里，不在 showFloat 里**：showFloat 是"选区定下来"那一刻跑的，
 * 那时候就给输入框焦点 = 用户还没说要问就把编辑器的焦点抢走了（工具栏的选区就没了）。
 */
function openAskFloat() {
  if (!askFloat.value.visible) return
  askFloat.value = { ...askFloat.value, open: true }
  nextTick(() => askFloatInput.value?.focus())
}

/** 链接浮层的「AI 解读」两态展开。聚焦放这里，理由同 openAskFloat。 */
function openLinkAsk() {
  if (!linkBar.value.visible) return
  linkBar.value = { ...linkBar.value, askOpen: true }
  nextTick(() => linkAskInput.value?.focus())
}

/**
 * 选区定下来了就弹浮层；选区空了就把浮层收掉。
 *
 * **同一个选区只弹一次**（askFloatKey）：视图每变一次都会走这里（打字、选中、点击…），
 * 不去重的话浮层会被反复重画，位置还会跟着抖。
 *
 * @param {object} view ProseMirror EditorView
 */
/** 链接 mark 类型（linkInteractions 初始化时从 ctx 取一次，选区判据用） */
let linkMarkType = null

/**
 * 这段选区**恰好是链接的文字**（不是含链接的更长选区）？
 * 点链接会把选区落到整个链接上 ⇒ 这种选区的 AI 入口在链接浮层里，
 * 独立的「AI 解读」单钮不叠上来（两浮层同位会互相压住，2026-11-09 用户报的）。
 */
function isLinkOnlySelection(view, from, to) {
  if (!linkMarkType) return false
  let sawText = false
  let ok = true
  view.state.doc.nodesBetween(from, to, (node) => {
    if (!node.isText || !ok) return
    sawText = true
    if (!linkMarkType.isInSet(node.marks)) ok = false
  })
  return ok && sawText
}

function syncAskFloat(view) {
  if (!props.askEnabled || suppressAskFloat) {
    hideAskFloat()
    return
  }
  const sel = view.state.selection
  if (sel.empty) {
    hideAskFloat()
    return
  }
  // 链接的选区不弹这个：AI 入口在链接浮层里（见 isLinkOnlySelection）。
  if (isLinkOnlySelection(view, sel.from, sel.to)) {
    hideAskFloat()
    return
  }
  const key = sel.from + '-' + sel.to
  if (key === askFloatKey) return
  const blocks = blocksInRangeNodes(view.state.doc, sel.from, sel.to)
  if (!blocks.length) {
    hideAskFloat()
    return
  }
  askFloatKey = key
  showFloat(
    askFloat,
    askFloatEl,
    { from: sel.from, to: sel.to },
    {
      value: '',
      // 每次换选区都回到第一态（按钮），别把上一个选区展开着的输入框带过来
      open: false
    }
  )
}

/**
 * 「AI 解读」的提交负载：选中的那段 + 顶层块锚（写回时"这段之后另起一段"用，
 * 所以锚点是块，不是选区）。
 * **提交时从 range 现算**——在浮层状态里存 text/block* 就得跟文档改动手动同步，
 * 漏一次的症状是"解释被写到不该在的地方"。
 * 两个提交口（选区浮层 / 链接浮层的 AI）共用这一条。
 */
function askPayloadFor(from, to, supplement) {
  const view = getView()
  if (!view) return null
  const blocks = blocksInRangeNodes(view.state.doc, from, to)
  if (!blocks.length) return null
  return {
    text: view.state.doc.textBetween(from, to, '\n'),
    supplement,
    blockFrom: blocks[0].from,
    blockTo: blocks[blocks.length - 1].to,
    blockText: blocks.map((b) => b.node.textContent).join('\n')
  }
}

/**
 * 提交这次提问：把「选中的那段 + 用户补的问题」交给上层（详情页）去问 AI。
 *
 * **这里不自己发请求**：编辑器是 components/ 下的通用组件，不该知道 AI 怎么调；
 * 上层拿这个 payload 去开问答抽屉、发一轮问答。
 */
function submitAskFloat() {
  const f = askFloat.value
  if (!f.visible) return
  const payload = askPayloadFor(f.range.from, f.range.to, (f.value || '').trim())
  hideAskFloat()
  if (payload) emit('ask-ai', payload)
}

/** 链接浮层的「AI 解读」提交：负载与选区浮层同一条（askPayloadFor）。 */
function submitLinkAsk() {
  const f = linkBar.value
  if (!f.visible) return
  const payload = askPayloadFor(f.range.from, f.range.to, (f.value || '').trim())
  hideLinkBar()
  if (payload) emit('ask-ai', payload)
}

/**
 * 目录链接的 href 前缀。
 *
 * **只有一份字面量**：`utils/categoryNav` 里那一份（编辑器、正文里的链接、
 * AI 生成的内容都从那儿取）。CSS 选择器写不了变量（见 .ProseMirror a[href^=...]
 * 那段注释），那里是这个前缀的**另一份拷贝** —— 改的时候两处一起改。
 */

// 两排按钮的清单。**写成表而不是九个写死的 <KbTooltip><el-button>**：
// 每个按钮外面都得套一层 el-tooltip（见模板注释），写死就是九份一模一样的壳，
// 而按钮本身只是"一句提示 + 一个动作"，把它拆散到九个壳里等于让它无处可查。
// computed 而不是常量：提示文案要跟着置灰状态走（下面「插入链接」那条）。
//
// ⚠️ **块级命令走 `ApplyToVisualLines`，加粗/斜体走 milkdown 原生命令** ——
// 前五个是块级命令，粒度是段落；而粘贴进来的长文本是**一个段落**（见 handlePaste，
// 那是 markdown 精确保留空行的唯一办法），于是在里面选一行点 H2 会连整块一起变。
// 补丁在命令执行前先按换行把选区切开。后两个是 mark，粒度天然是"字符"，
// `addMark` 只作用于 text 节点，本来就对 —— **同一类问题只修受影响的那一半**。
// 详见 kbLineBlocks.js。
const MD_BUTTONS = computed(() => {
  // 置灰的时候，提示要说清楚**为什么**点不动 —— 灰着一个没人解释的按钮，
  // 用户只会以为是坏了。所以提示跟着 hasSelection 走，不是写死的。
  // 「（先选中文字）」这半句好几处都要，抄几遍就是几份会走散的说法。
  const noSel = !hasSelection.value
  const needSel = (name) => (noSel ? `${name}（先选中文字）` : name)
  const sep = (id) => ({ id: `sep-${id}`, sep: true })
  const list = [
  // —— 组 1：文字格式（mark + 标题）——
  { id: 'strong', tip: '加粗 (Ctrl+B)', text: 'B', cls: 'md-btn-bold', run: () => runCmd('ToggleStrong') },
  { id: 'em', tip: '斜体 (Ctrl+I)', text: 'I', cls: 'md-btn-italic', run: () => runCmd('ToggleEmphasis') },
  // H2/H3 合并成一个「标题」下拉（2026-11-15）：两个按钮占两位、H4 没地方放；
  // 下拉里 H2/H3/H4/正文 档位齐了。块级命令要选区（ApplyToVisualLines 空选区直接 false）。
  { id: 'heading', tip: needSel('标题'), icon: 'heading', run: (e) => openToolMenu('heading', e.currentTarget) },
  sep('block'),
  // —— 组 2：块结构（图标全部自绘，2026-11-16：原来是 EP 老图标，与 meta 行两种画风）——
  { id: 'ul', tip: '无序列表', icon: 'bullets', run: () => runCmd('ApplyToVisualLines', { name: 'bullet_list', mode: 'wrap' }) },
  { id: 'ol', tip: '有序列表', icon: 'numbers', run: () => runCmd('ApplyToVisualLines', { name: 'ordered_list', mode: 'wrap' }) },
  { id: 'code', tip: '代码块', icon: 'code-block', run: () => runCmd('ApplyToVisualLines', { name: 'code_block', attrs: { language: '' } }) },
  { id: 'quote', tip: '引用块', icon: 'quote-block', run: () => runCmd('ApplyToVisualLines', { name: 'blockquote', mode: 'wrap' }) },
  { id: 'hr', tip: '分割线', icon: 'divider', run: insertHr },
  { id: 'clearformat', tip: needSel('清除格式'), icon: 'eraser', disabled: noSel, run: () => runCmd('ClearFormatting') },
  { id: 'table', tip: '插入表格', icon: 'table', run: openTableDialog },
  sep('link'),
  // —— 组 3：链接 ——
  // 两个都是"给选中的字挂一个跳转目标"，挨着放；隔开会让用户在整排里来回找。
  {
    id: 'link',
    tip: needSel('插入链接'),
    icon: 'link',
    disabled: noSel,
    run: insertLink
  },
  {
    id: 'catlink',
    tip: needSel('设为目录链接'),
    icon: 'folder-link',
    // 没有目录树 = 一个都选不出来，按钮灰掉。不留"点了弹个空框"那条路。
    disabled: noSel || !props.categoryTree.length,
    run: insertCategoryLink
  },
  sep('font'),
  // —— 组 4：字号字体 ——
  {
    id: 'fontsize',
    tip: needSel('字号'),
    text: 'Aa',
    cls: 'md-btn-aa',
    disabled: noSel,
    // 传按钮自己：档位浮层要贴着**这个按钮**弹，不是贴着整条工具栏 ——
    // 否则字号和字体两个按钮弹出来的位置一模一样。
    run: (e) => openFormatPick('size', e.currentTarget)
  },
  {
    id: 'fontfamily',
    tip: needSel('字体'),
    icon: 'typeface',
    disabled: noSel,
    run: (e) => openFormatPick('family', e.currentTarget)
  }
  ]
  // ⚠️ **文件动作（导入/导出）不再由工具栏渲染**（2026-11-16）：它们要和「类型」
  // 并列放在页面右上角（知识点编辑页 = 标题行右端；首页 = 卡片右上角）——
  // 类型是页面级控件，文件动作也是页面级，工具栏不该夹在中间。
  // ⇒ 页面自己画那两颗按钮，调编辑器的 `openExportMenu(btn)` / `pickImportFile()`。
  return list
})

const LINK_BUTTONS = [
  { id: 'visit', tip: '访问', icon: 'external', run: visitLink },
  { id: 'edit', tip: '编辑链接', icon: 'edit', run: editLink },
  { id: 'copy', tip: '复制文字', icon: 'copy', run: copyLink },
  { id: 'unlink', tip: '取消链接', icon: 'unlink', run: unlink }
]

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
  if (e.key !== 'Escape') return
  // 编辑器里四个浮层（表格右键菜单 / 链接工具条 / URL 气泡 / 目录气泡）走同一条 Esc，
  // 各自在自己的 keymap 里判一次是四份重复，而且焦点在输入框时编辑器那条根本收不到事件。
  closeCtxMenu()
  hideLinkBar()
  hideLinkInput()
  hideCatLink()
  hideFormatPick()
  hideToolMenu()
  hideAskFloat()
}

/**
 * 鼠标在**编辑器外面**松开时，编辑器那份 mouseup 收不到
 * （事件没落在 view.dom 里）⇒ "鼠标还按着"那个标记会永远留着，
 * 之后浮层再也不弹（鼠标明明是松开的）。这里兜一下。
 */
function onDocumentMouseUp() {
  mouseDown = false
}

// 点浮层以外的地方收起链接浮层。
// 挂 mousedown 而不是 click：单击链接时 ProseMirror 的 click handler 先把工具条显示出来，
// document 的 click 冒泡监听后到，会把它**立刻收回去**——工具条一晃就没了。
// mousedown 在 click 之前，"点链接开工具条"和"点别处关工具条"才能共存于同一个监听。
function onDocumentMouseDown(e) {
  // 点到浮层自己时不收，否则刚点"编辑链接"弹出的输入框会被自己的这次点击关掉
  if (e.target.closest && e.target.closest('.link-pop, .link-input, .cat-link, .format-pick, .tool-menu, .ask-float')) return
  hideLinkBar()
  hideFormatPick()
  hideToolMenu()
  // 选区浮层：点外面 = 放弃这次提问（没提交的东西不作数）
  hideAskFloat()
  // 目录气泡点外面 = **取消**，和 URL 气泡那条相反：那里提交的是"框里输到一半的东西"
  // （用户已经打了不少字，点外面丢掉太亏，见上面的注释），这里根本不存在半成品——
  // el-tree-select 是"点一下节点就已经选定了"，没选就等于不想选，按回车提交是凭空多一步。
  if (catLink.value.visible) hideCatLink()
  else if (linkInput.value.visible) confirmLinkInput()
  else hideLinkInput()
}

onMounted(() => {
  document.addEventListener('contextmenu', onDocumentContextMenu)
  document.addEventListener('click', closeCtxMenu)
  document.addEventListener('keydown', onDocumentKeydown)
  document.addEventListener('mousedown', onDocumentMouseDown)
  document.addEventListener('mouseup', onDocumentMouseUp)
  window.addEventListener('scroll', onScrollReanchor, true)
  window.addEventListener('resize', onScrollReanchor)
})

onBeforeUnmount(() => {
  document.removeEventListener('contextmenu', onDocumentContextMenu)
  document.removeEventListener('click', closeCtxMenu)
  document.removeEventListener('keydown', onDocumentKeydown)
  document.removeEventListener('mousedown', onDocumentMouseDown)
  document.removeEventListener('mouseup', onDocumentMouseUp)
  window.removeEventListener('scroll', onScrollReanchor, true)
  window.removeEventListener('resize', onScrollReanchor)
})

function hideFloat(state) {
  if (state.value.visible) state.value = { ...state.value, visible: false }
}

/**
 * 工具菜单浮层（2026-11-15）：标题档位 / ··· 更多 / 导出 三种内容共用一张脸。
 * 和字号字体浮层（format-pick）同一套机制——贴着按钮弹、点外面收、Esc 收；
 * 菜单本身 `@mousedown.prevent`，编辑器选区不会被夺走（块级命令靠它）。
 */
const toolMenu = ref({ visible: false, kind: 'heading', x: 0, y: 0 })

function openToolMenu(kind, btn) {
  if (!btn) return
  const rect = btn.getBoundingClientRect()
  toolMenu.value = { visible: true, kind, x: rect.left, y: rect.bottom + 6 }
}

function hideToolMenu() {
  hideFloat(toolMenu)
}

/** 菜单项跟着浮层的 kind 走。2026-11-16 二稿：导出三项全真做，占位与「···」退役。 */
const toolMenuItems = computed(() => {
  if (toolMenu.value.kind === 'heading') {
    return [
      { key: 'h2', label: 'H2 标题' },
      { key: 'h3', label: 'H3 标题' },
      { key: 'h4', label: 'H4 标题' },
      { key: 'p', label: '正文' }
    ]
  }
  // 导出：md = 原文直出；PDF = 浏览器打印转存（矢量、mermaid 原样、零服务器）；
  // Word = HTML-.doc（复用 docOf 序列化，WPS/Word 直开）。
  return [
    { key: 'md', label: 'Markdown' },
    { key: 'pdf', label: 'PDF' },
    { key: 'word', label: 'Word' }
  ]
})

function onToolMenuPick(it) {
  const kind = toolMenu.value.kind
  hideToolMenu()
  if (kind === 'heading') {
    if (it.key === 'p') runCmd('ApplyToVisualLines', { name: 'paragraph' })
    else runCmd('ApplyToVisualLines', { name: 'heading', attrs: { level: Number(it.key.slice(1)) } })
    return
  }
  if (kind === 'export') {
    if (it.key === 'md') {
      // 正文跟着事件带给外层（它才知道文件名该叫什么），这里不自己落盘
      emit('export-md', props.modelValue)
    } else {
      // PDF / Word：交给使用方（打印样式与 Word 包装需要页面级上下文）
      emit('export-file', { format: it.key, markdown: props.modelValue })
    }
  }
}

/** 分割线：不走命令字符串（key 写错是静默 no-op，最难查）——直接按 schema 插节点。
 *  ⚠️ milkdown preset-commonmark 里这个节点叫 **hr**（不是 prosemirror-markdown 的 horizontal_rule） */
function insertHr() {
  const view = getView()
  const hrNode = view?.state.schema.nodes.hr
  if (view && hrNode) {
    view.dispatch(view.state.tr.replaceSelectionWith(hrNode.create()).scrollIntoView())
  }
}

/** 导入的隐藏 input（模板工具栏里那颗）：点「导入」弹**系统文件选择器**。 */
const importInput = ref(null)

function pickImport() {
  importInput.value?.click()
}

/**
 * 选中 .md 之后：读文本 → **编辑器自己的解析器** → 插到光标处。
 *
 * <p>不整篇覆盖（replaceContent 是 AI 改写那条路）：导入是"往正在写的东西里
 * 补一段"，已写的内容一个字都不能丢。`Slice(doc.content, 0, 0)` = 块级内容
 * 以块的形式插进选区。
 */
async function onImportFile(e) {
  const file = e.target.files?.[0]
  // 清掉 value：同一个文件连导两次也要触发 change
  e.target.value = ''
  if (!file) return
  const md = await file.text().catch(() => '')
  const doc = md.trim() ? parseDoc(md) : null
  const view = getView()
  if (!doc || !view) return
  view.dispatch(view.state.tr.replaceSelection(new Slice(doc.content, 0, 0)).scrollIntoView())
}

function hideLinkBar() {
  hideFloat(linkBar)
}

function hideLinkInput() {
  hideFloat(linkInput)
}

function hideCatLink() {
  // 连选中的值一起清：这个气泡下次打开时不能带着上次的选中项——
  // el-tree-select 的 v-model 还在组件里，不归零的话用户会看到上次选的那个目录
  // 填在框里，以为已经选过了。
  hideFloat(catLink)
  catLinkValue.value = null
}

// 拿到当前 EditorView。工具条按钮的四个动作都在组件层，而文档位置在编辑器里。
// ⚠️ **编辑器没建好时必须返回 null，不能抛**（2026-11-09 定位 bug 修出来的）：
// `useEditor` 的 `get()` 在 `editor.create()` 完成前是 **undefined** ⇒ 原来的
// `get().action(...)` 直接 TypeError ⇒ 详情页「点进卡片自动定位」那一步整个炸掉
// （症状：进详情页什么都没发生，连高亮都没有，界面上看不出任何异常）。
// ⇒ 拿不到视图就当"还没好"，由调用方等 @ready 再来。
function getView() {
  let view = null
  const ed = get()
  if (!ed) return null
  ed.action((ctx) => {
    view = ctx.get(editorViewCtx)
  })
  return view
}

// 选区矩形：coordsAtPos 给的是单点坐标，一段文字的宽度得自己合。
function rangeRect(view, from, to) {
  const a = view.coordsAtPos(from)
  const b = view.coordsAtPos(to)
  return {
    left: Math.min(a.left, b.left),
    right: Math.max(a.right, b.right),
    top: Math.min(a.top, b.top),
    bottom: Math.max(a.bottom, b.bottom)
  }
}

// 浮层贴着链接/选区显示：优先放上方，顶到屏幕上边就翻到下方
//（一律放上方的话，贴着顶栏的那行链接的工具条会被顶出视口）。
// 水平方向夹进视口用**实测宽度**，不写死"半个浮层宽度"：按钮一多一少宽度就变，
// 写死的常数必然对不上，而且改的时候没有任何地方会报错。
function floatPos(rect, el) {
  const pad = 8
  const gap = 6
  const w = el ? el.offsetWidth : 0
  const h = el ? el.offsetHeight : 0
  return {
    x: Math.min(Math.max(rect.left, pad), Math.max(pad, window.innerWidth - w - pad)),
    y: rect.top - h - gap >= 0 ? rect.top - h - gap : rect.bottom + gap
  }
}

// 浮层贴着链接/选区显示。两个浮层（链接工具条 / URL 气泡）走同一条：
// 先按原始位置显示出来，下一帧才量得到真实尺寸，量完再贴一次。
// **必须是两帧**：第一帧里元素还没渲染，offsetWidth 是 0，按它算出来的位置是错的，
// 而这个错只表现为"浮层歪了一点"，不会报错。
// after 是定位完成后的额外动作（只有 URL 气泡要聚焦输入框）。
function showFloat(state, el, range, extra, after) {
  const view = getView()
  if (!view) return
  const rect = rangeRect(view, range.from, range.to)
  state.value = { ...state.value, ...extra, visible: true, away: false, range, x: rect.left, y: rect.top }
  nextTick(() => {
    state.value = { ...state.value, ...floatPos(rect, el.value) }
    after?.()
  })
}

/**
 * 工具条上**当前该显示哪几个**动作。
 *
 * <p>自动链接（{@link linkBar.temp}）只给「访问」：它没有 link mark，
 * 「编辑链接」要往选区上盖 mark、「取消链接」要摘 mark、「复制文字」还算能用但
 * 三个里两个是空操作 —— 一条工具条上摆两个点了没反应的钮，比少摆更糟。
 */
const visibleLinkButtons = computed(() =>
  linkBar.value.temp ? LINK_BUTTONS.filter((b) => b.id === 'visit') : LINK_BUTTONS
)

function showLinkBar(range, href, temp = false) {
  // askOpen / value 归零：上次展开过的输入框不能带到下一个链接上
  // temp **必须显式写**：showFloat 是 Object.assign 合并，不传就留着上一条链接的值
  showFloat(linkBar, linkBarEl, range, { href, askOpen: false, value: '', temp })
}

function openLinkInput(cmd, href, range) {
  showFloat(linkInput, linkInputElBox, range, { cmd, value: href || '' }, () => linkInputEl.value?.focus())
}

/**
 * 浮层跟着锚点走（2026-11-09）：滚动 / 改窗时按 range 重算位置 ——
 * 原来坐标只算一次，正文一滚浮层就和链接 / 选区脱节（用户报的）。
 * 锚点滑出编辑器可视区 ⇒ 置 away 渐隐；回来自动恢复。
 * 只管"读的时候要盯着"的两个浮层（链接浮层 / 选区浮层）；
 * 输入气泡、档位浮层是点完即走的瞬态，不跟。
 */
function reanchorFloat(state, el) {
  if (!state.value.visible || !state.value.range) return
  const view = getView()
  if (!view) return
  const rect = rangeRect(view, state.value.range.from, state.value.range.to)
  const host = view.dom.getBoundingClientRect()
  const away = rect.bottom < host.top + 2 || rect.top > host.bottom - 2
  state.value = { ...state.value, ...floatPos(rect, el.value), away }
}

// scroll 挂 capture：v10 起正文自己滚（.ProseMirror），scroll 事件不冒泡，
// capture 才能从 window 一路收到任意滚动容器的滚。rAF 合帧，别每像素都算。
let reanchorQueued = false
function onScrollReanchor() {
  if (reanchorQueued) return
  reanchorQueued = true
  requestAnimationFrame(() => {
    reanchorQueued = false
    reanchorFloat(linkBar, linkBarEl)
    reanchorFloat(askFloat, askFloatEl)
  })
}

// 把选区按范围设回去。工具条已经 @mousedown.prevent 不夺焦点，理论上选区还在，
// 但确定/取消之前显式设一次：ProseMirror 在失焦时可能把选区收成空，一旦收成空，
// "取消链接"会变成"给光标处加一个链接"——正好和用户点的那个相反。
function restoreRange(range) {
  const view = getView()
  if (!view || !range) return null
  view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, range.from, range.to)))
  view.focus()
  return view
}

function visitLink() {
  const href = linkBar.value.href
  if (!href) return
  // 站内地址（目录引用）走 router：不整页刷新，AppLayout 已经拉好的目录树、
  // 菜单全都留着。整页刷新会把这些全部重来一遍。
  //
  // **拆成 path + query 两块，不直接 push 字符串**：字符串形式里的 `?query`
  // 要靠 router 自己解析成 query 对象再拼回去，多一层转换、多一个能出错的地方。
  // 目录引用的 href 是我们自己拼的（`/app/learn?category=<id>`），拆开最直接。
  if (href.startsWith(CATEGORY_HREF_PREFIX)) {
    const [path, qs] = href.split('?')
    router.push({ path, query: Object.fromEntries(new URLSearchParams(qs || '')) })
    hideLinkBar()
    return
  }
  // 知识点引用（`/app/topic/<id>`）：**没有 query**，href 本身就是 path。
  if (href.startsWith(TOPIC_HREF_PREFIX)) {
    router.push(href)
    hideLinkBar()
    return
  }
  window.open(href, '_blank', 'noopener,noreferrer')
}

/** 当前链接在文档里覆盖的那段文字。单击链接时 range = 整段（`to = pos + node.nodeSize`） */
function linkText() {
  const view = getView()
  const range = linkBar.value.range
  if (!view || !range) return ''
  return view.state.doc.textBetween(range.from, range.to)
}

// **复制完就收起浮层**，和「取消链接」一致：四个动作里三个是"做完就走"，
// 只有「访问」要留着（它开新标签，当前页没变化）。
// 失败时**不收**——让用户能直接再点一次，而不是对着一个空输入框发呆。
//
// 复制的是**链接的文字**，不是地址：地址多半一长串、粘到别处也没有上下文，
// 而"这块文字链到这里"才是通常要分享的东西。#157 放开空 href 之后更是只能复制文字。
async function copyLink() {
  if (await copyText(linkText())) hideLinkBar()
}

function editLink() {
  openLinkInput('UpdateLink', linkBar.value.href, linkBar.value.range)
}

function unlink() {
  // 选区此刻正好是这个链接的全部文字（单击时设好的），toggle = 摘掉 link 标记，文字留着。
  if (!restoreRange(linkBar.value.range)) return
  runCmd('ToggleLink')
  hideLinkBar()
}

// 代码块里的两个键。
//
// ## Enter = 在代码块里插一行（用户要的就是这个）
//
// **不要自己抢 Enter**。ProseMirror 基础 keymap 的 Enter 链是
// `chainCommands(newlineInCode, createParagraphNear, liftEmptyBlock, splitBlock)`，
// `newlineInCode` **排第一**：在代码块里它就是插一个 `\n`（代码块里换行是文本里的
// `\n`，不是块）。谁抢在它前面，代码块里的回车就不是"换一行"了。
//
// ⚠️ **上一版就是抢了，而且抢得特别准**：自定义 keymap 用 priority 100 抢在基础
// keymap 前面，判据是"光标在文本末尾"。而代码块里的换行是**文本里的 \n**，
// 所以"最后一行末尾"**就是**"文本末尾" —— 判据命中的恰恰是最常见的场景：
// 写到最后一行按回车想换行，结果被踢出代码块（用户原话："在代码块里回车肯定是
// 把这个代码块扩充一行而不是直接跳到外面来"）。
//
// ## Shift+Enter = 跳出代码块
//
// 光靠 Enter 出不去的话，代码块就成了只能进不能出的坑。基础 keymap 本来就把
// `exitCode` 挂在 Shift-Enter 上（`chainCommands(exitCode, insertHardbreak)`），
// 但 milkdown 的 hardbreak 也占着 Shift-Enter，所以这里补一条**只在代码块里**
// 生效的：段落里的 Shift-Enter 仍然是硬换行（我的命令返回 false，链条继续往下走）。
//
// priority 100：同一个键的所有 handler 按 (priority ?? 50) 降序排，不给优先级
// 就跟别人并列，顺序不定。
const codeBlockKeymap = $useKeymap('codeBlockKeymap', {
  ExitCodeBlock: {
    shortcuts: 'Shift-Enter',
    priority: 100,
    command: () => (state, dispatch, view) => {
      const { $from, empty } = state.selection
      if (!empty) return false
      // 和 exitCode 内部同一个判据：spec.code 标的是代码类块（code_block / code）
      if (!$from.parent.type.spec.code) return false
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

// 链接交互（单击选中 / Ctrl+单击打开 / 工具条四个动作 / 插入链接置灰）。
//
// ⚠️ 用的是 `handleDOMEvents.click` 而不是 `handleClickOn`：
// 后者的签名是 `(view, pos, node, nodePos, event, direct)`——第三个参数是**节点**不是事件，
// 而且它是为 node view 设计的（按深度一层层往上问），mark 上命中不到。按 mark 的直觉签名去写，
// 代码能编过、能运行、什么也不发生——这轮就是这么翻车的。
// `handleDOMEvents` 拿的是原封不动的 DOM 事件，命中判据自己写：`closest('a')` 拿到 <a>，
// `view.posAtDOM(el, 0)` 把 DOM 位置换回文档位置，剩下的就和 mark 无关了。
const linkInteractions = $prose((ctx) => {
  const mark = linkSchema.type(ctx)
  // 选区判据（isLinkOnlySelection）也要用这个 mark，取一次存下
  linkMarkType = mark
  return new Plugin({
    view: () => ({
      update(view) {
        hasSelection.value = !view.state.selection.empty
        // 选区移出这个链接就把工具条收掉：它钉在链接上方，不跟着选区走，
        // 留着就是个指着不相干文字的浮层。
        if (linkBar.value.visible && !view.state.doc.rangeHasMark(view.state.selection.from, view.state.selection.to, mark)) {
          hideLinkBar()
        }
        // 选区变了就同步「AI 解读」浮层。
        // **鼠标按着的时候不同步**：拖选过程中选区一路变，浮层会一路跟着重画，
        // 位置还不是最终位置（松手才算数）。松手那一下由 mouseup 处理。
        if (!mouseDown) syncAskFloat(view)
      }
    }),
    props: {
      handleDOMEvents: {
        // 拖选过程中先记着"鼠标还按着"：那时候选区一路变，浮层不该跟着重画。
        mousedown() {
          mouseDown = true
          return false
        },
        // 松手 = 选区定了 ⇒ 这时候弹「AI 解读」浮层。
        //
        // **为什么不在 update 钩子里弹**：Chrome 的 selectionchange 是在 mouseup
        // **之后**才发的，那时候视图还没拿到最终选区；拖到一半弹出来的位置是错的。
        // 这里读 mouseup 时刻的 state，选区已经是对的（拖动过程中 Chrome 就一直在报）。
        mouseup(view) {
          mouseDown = false
          syncAskFloat(view)
          return false
        },
        click(view, event) {
          // ① 正文自动链接（`kbAutoLink` 的 Decoration）—— **不是**真正的 link mark。
          //
          // 这里必须单独接一条：下面那套判据（`closest('a')` + `nodeAt(pos).marks` 里有
          // link）对装饰**一条都不成立** ⇒ 不接的表现就是用户报的那句
          // 「看着像链接，点了没反应」（连工具条都不弹）。
          // 交互对齐真链接：单击 = 选中这段并弹同一条工具条（只给「访问」），
          // Ctrl/⌘ + 单击 = 新标签直接打开。
          const auto = event.target.closest && event.target.closest('.kb-autolink')
          if (auto && view.dom.contains(auto)) {
            const autoHref = auto.getAttribute('data-href') || ''
            if (!autoHref) return false
            event.preventDefault()
            if (event.ctrlKey || event.metaKey) {
              window.open(autoHref, '_blank', 'noopener,noreferrer')
              return true
            }
            const from = view.posAtDOM(auto, 0)
            const range = { from, to: from + auto.textContent.length }
            view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, range.from, range.to)))
            view.focus()
            showLinkBar(range, autoHref, true)
            return true
          }
          const el = event.target.closest && event.target.closest('a')
          if (!el || !view.dom.contains(el)) return false
          const pos = view.posAtDOM(el, 0)
          const node = view.state.doc.nodeAt(pos)
          if (!node || !mark.isInSet(node.marks)) return false
          // 返回 true 只是让 ProseMirror 别再跑它自己那套点击处理，
          // **不会**阻止浏览器跟着 <a> 走，这一句是省掉"Ctrl+单击在当前标签也跳了"。
          event.preventDefault()
          const href = node.marks.find((m) => m.type === mark)?.attrs.href || ''
          // 附件卡片：单击 = 下载，不弹工具条。
          // 工具条那四个动作（访问 / 编辑 / 复制 / 取消）全是**改文字**的，
          // 对着一个文件卡片毫无意义；而「点一下就把文件拿走」正是它长得像卡片的原因。
          // 判据和渲染层认附件的那条完全一致（a[href^="/api/files/"]），两边不各认一套。
          if (href.startsWith('/api/files/')) {
            window.open(href, '_blank', 'noopener,noreferrer')
            return true
          }
          // Ctrl/⌘+单击 = 直接新标签打开，不选也不弹工具条。
          if (event.ctrlKey || event.metaKey) {
            if (href) window.open(href, '_blank', 'noopener,noreferrer')
            return true
          }
          // 普通单击 = 把选区落到整个链接上（语雀那套），四个动作才有作用对象。
          //
          // **目录引用也走这一条**（不再单击直接跳转）：用户要的是"点一下先弹工具条，
          // 我自己决定是去访问还是取消链接"，而不是被动地被带走。工具条上的
          // 「访问链接」会判断是不是站内地址，站内走 router 跳转（不刷新整页）。
          const range = { from: pos, to: pos + node.nodeSize }
          view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, range.from, range.to)))
          view.focus()
          showLinkBar(range, href)
          return true
        }
      }
    }
  })
})

// 纯文本粘贴：**每个非空行 = 一个独立段落**。
//
// 为什么不让 @milkdown/plugin-clipboard 那条路走：它把纯文本按 **markdown** 解析，
// 于是 `a\nb\nc`（本来没有空行）会被折成**一个段落**，连续空行也一律折叠。
// 折叠本身不是问题，问题是它让"看着的"和"文档里的"不是一回事 —— 而这个编辑器
// 的块级命令（标题 / 列表 / 代码块）是**段落粒度**的，结果就是：
// 在一段外部贴进来的长文本里给某一行加标题，**整段一起变**。
// 所以自己走这条路，把"一行 = 一段"这件事定死，让本地敲的和外面贴的是同一种结构。
//
// ⚠️ 这里曾经试过另一条路：每个 `\n` 变成一个真 hardbreak（`<br>`），好处是
// "空了几行"能精确还原（markdown 存不住，`<br>` 能存住），代价是**整篇变成一个段落**。
// 那个"精确保留空行"其实**没有兑现价值**：全局 reset 把段落 margin 归零，
// 段落之间本来就是紧靠的，空行保不保存在屏幕上看不出差别 —— 代价全留下了，收益没拿到。
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

/**
 * **换知识点时**把 `markdownUpdated` 的上报压住。
 *
 * <p>详情页复用同一个编辑器（不再每条知识点重建一个 Milkdown），
 * 换知识点 = 把新正文**换进已有的编辑器**。
 * 那一下也会触发 `markdownUpdated` ⇒ 外层拿到新值 ⇒ 当成"用户改了正文" ⇒
 * **触发自动保存**（把当前这条的正文又存一遍，还多一个"已保存"状态跳动）。
 *
 * <p>换知识点是**载入**，不是编辑 ⇒ 不该上报。
 */
let suppressEmit = false

/**
 * 标题行首的「退格 / Delete」：**按语雀那套来**，不按 Milkdown 自带的那套。
 *
 * ## Milkdown 自带的是什么
 *
 * `@milkdown/preset-commonmark` 的 `headingKeymap` → `DowngradeHeading`
 * （`shortcuts: ['Delete','Backspace']`）：**光标在标题行首时按退格 = 把标题降一级**
 * （h3→h2→h1→段落），**不是"删掉这一行"**。
 *
 * ⇒ 空标题要按 3~5 次才回上一行（次数 = 层级数 + 1），而且**每按一次块类型就变一次**
 * ⇒ 字号、行高、上下间距跟着变 ⇒ 症状是"光标竖线的大小和间距每次都不一样"
 * （2026-10-27 用户实机报的）。
 *
 * ## 本项目要的行为（2026-10-27 用户定，参照语雀）
 *
 * | 情况 | 行为 |
 * |---|---|
 * | 标题是空的 | 删掉整块，光标落到上一行末尾 |
 * | 上一行是空行 | 标题**上移一个位置**（与空行换位，仍是标题） |
 * | 上一行有内容 | 标题**降级为普通正文**（内容不变，独立段落） |
 *
 * **下一行不参与判断。**
 *
 * ## 为什么「上一行是空行 ⇒ 上移」是这套的关键
 *
 * 上一行空着的时候，"往上移动"在用户心里是**"把这两块并到一起"**的意思 ——
 * 并的是**上面那块有数据的**（也就是移动之后的上一行 = 没移动之前的上两行）。
 * 所以这时候**不该降级**：标题保持标题、只是往上挪一格，挪完正好贴住那块数据。
 *
 * ## 挂在哪、为什么能抢在 milkdown 前面
 *
 * 挂 `editorViewOptionsCtx` 的 `handleKeyDown` ⇒ 进 `view._props`，
 * prosemirror-view 查 props 的顺序是 `view._props → directPlugins → state.plugins`
 * ⇒ **无条件第一个被查到**，比所有插件 keymap 都早（同文件里粘贴处理就是这么挂的）。
 * **返回 false = 交回默认行为**（milkdown 的降级照旧生效，其它块一点不动）。
 */
function handleHeadingEdgeKey(view, event) {
  if (event.key !== 'Backspace' && event.key !== 'Delete') return false
  const { state } = view
  const { $from, empty } = state.selection
  if (!empty) return false
  // 只管标题，而且只在标题**行首**（光标在文字中间时退格是删字，不归这里）
  if ($from.parent.type.name !== 'heading') return false
  if ($from.parentOffset !== 0) return false

  // ① 空标题 ⇒ 删掉整块，光标落到上一行末尾。
  //    joinBackward 对空块做的就是这件事（删块 + 把光标放到上一块末尾）。
  if ($from.parent.content.size === 0) {
    return joinBackward(state, view.dispatch, view)
  }

  const headingPos = $from.before()
  const heading = state.doc.nodeAt(headingPos)
  const prev = state.doc.resolve(headingPos).nodeBefore

  // ② 上一行是空块 ⇒ 标题上移一个位置（和空行换位，仍是标题）
  if (prev && prev.isTextblock && prev.content.size === 0) {
    return moveHeadingUp(view, headingPos, heading, prev)
  }

  // ③ 上一行有内容 ⇒ 标题降级为普通正文（内容不变，独立段落）
  const tr = state.tr.setNodeMarkup(headingPos, state.schema.nodes.paragraph)
  view.dispatch(tr.scrollIntoView())
  return true
}

/**
 * 标题上移一个位置：和上一块**换位** —— 标题跑到上面那一行，原来那一行落到标题原来的位置。
 *
 * **为什么是"换位"而不是"合并"**：上一行是**空块**（判据见 handleHeadingEdgeKey）。
 * 合并等于把标题的内容塞进那个空段落 ⇒ 标题就变成正文了 ⇒ 那不是要的行为。
 * 换位保住了"标题仍是标题"，只把它挪高一格。
 *
 * ⚠️ **`Fragment.from` 必须传数组**：本项目装的 prosemirror-model 里
 * `Fragment.from(nodes)` **只收一个参数**（`nodes.attrs` 存在就包成单节点 fragment），
 * 写成 `Fragment.from(heading, prev)` 的话 **prev 会被静默丢掉** ——
 * 结果就是「标题上去了、空段落没了」，而代码看起来完全正常。
 * （这个坑是 2026-10-27 用 Node 复刻事务、打印 step.toJSON() 才抓到的：
 * slice 里只有 heading、没有 paragraph。）
 */
function moveHeadingUp(view, headingPos, heading, prev) {
  const { state } = view
  const prevPos = headingPos - prev.nodeSize
  const tr = state.tr
  tr.delete(prevPos, headingPos + heading.nodeSize)
  tr.insert(prevPos, Fragment.from([heading, prev]))
  // 光标跟着标题走：标题现在从 prevPos 开始，内容起点 = prevPos + 1
  tr.setSelection(TextSelection.near(tr.doc.resolve(prevPos + 1)))
  view.dispatch(tr.scrollIntoView())
  return true
}

const { get, loading } = useEditor((root) =>
  Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, root)
      // 关键：把外部 v-model 的 markdown 作为初始 doc，否则 Editor 永远是空 doc。
      // defaultValueCtx 默认值是 ""（@milkdown/core/lib/index.js:366），不显式 set 会导致：
      //   - 详情页：保存的 markdown 不渲染
      //   - 记录页：能"假正常"是因为用户输入直接打在 contenteditable，markdown 解析其实也没生效
      // 切 topic 时需重建 MilkdownEditor（keep-alive 同 route 名不复用组件）才能让新内容生效。
      ctx.set(defaultValueCtx, props.modelValue)
      ctx.get(listenerCtx).markdownUpdated((mctx, markdown) => {
        // **换知识点时不上报**（见 switchContent）：那是载入，不是用户编辑。
        // 上报了外层会当成"用户改了正文" ⇒ 触发自动保存，把当前这条又存一遍。
        if (suppressEmit) {
          emitOutline()
          return
        }
        // 序列化出来的 markdown 可能**自己读不回来**（闭合的 `**` 紧跟字母时
        // CommonMark 闭不上，粗体会丢、`**` 变字面量）。修完再往外给。
        // 详见 utils/kbMarkdown.js 的说明。
        emit('update:modelValue', fixEmphasisTail(markdown))
        emitOutline()
      })
      // 纯文本粘贴：原样逐行贴，不当 markdown 解析（理由见上面那段注释）。
      ctx.update(editorViewOptionsCtx, (prev) => ({
        ...prev,
        /**
         * 粘贴的块格式**跟随目标行**，不跟随源内容。
         *
         * WPS / 语雀都是这个规矩：往标题行里粘，粘完还是标题。
         * 而反过来的毛病正在这儿——从正文复制一段粘进标题行，
         * 源格式（正文）把标题冲掉了，标题行变成了正文。
         *
         * ## 只在「标题 ↔ 正文段落」之间换
         *
         * 别的块类型不动：
         * - **多段内容**（childCount > 1）不换 —— 好几段塞进一个标题是荒谬的；
         * - **列表项 / 代码块 / 表格**不换 —— 它们的结构和标题不是一回事
         *   （`list_item` 的内容本身还得是一个 paragraph，换类型会换坏）。
         *
         * ## 为什么 html 和纯文本两条路径都覆盖到
         *
         * 纯文本那条（下面 handlePaste 里自己处理）本来就是 `replaceSelection`
         * 插进当前段落，天然跟随目标；html 那条走 clipboard 插件的
         * `preProcessedSlice`，带着源块结构 —— 要改的就是它。
         * `transformPasted` 是在 `parseFromClipboard` 之后跑的，
         * 所以改这个切片正好改的是 html 那条路径用的那份。
         */
        transformPasted: (slice, view) => {
          const target = view.state.selection.$from.parent
          if (!target || !target.isTextblock) return slice
          const content = slice.content
          if (content.childCount !== 1) return slice
          const node = content.firstChild
          if (!node || !node.isTextblock) return slice
          const isHeading = (t) => t.name === 'heading'
          const isParagraph = (t) => t.name === 'paragraph'
          // 目标标题、源正文 → 变成标题；目标正文、源标题 → 变成正文。其余不动
          if (!(isHeading(target.type) && isParagraph(node.type))
            && !(isParagraph(target.type) && isHeading(node.type))) return slice
          return new Slice(Fragment.from(target.type.create(target.attrs, node.content)), 0, 0)
        },
        handlePaste: (view, event) => {
          const { clipboardData } = event
          if (!clipboardData) return false

          // 光标在代码块里：图片也不插。image 节点是 inline 且 marks:''，
          // 塞进 code_block 会失败 —— 交回默认行为，行为一点不动。
          if (view.state.selection.$from.parent.type.spec.code) return false

          // ---- 图片：先上传成链接，再插节点 ----
          // **必须在交回 clipboard 插件之前拦掉**：那个插件会把图片当 markdown 解析，
          // data URL 原样写进正文。而 `kp_topic.content` 是 TEXT（65535 字节），
          // base64 是二进制的 4/3 倍 —— 一张 50KB 截图就是 67KB，直接撑爆列、
          // insert 报 Data too long。正文里应该只有链接。
          //
          // **图片插 image 节点、其它文件插链接节点** —— markdown 没有"附件"概念，
          // 附件就是 `[文件名](url)`。读的时候靠「是不是 `<a>` 而不是 `<img>`」
          // 加上 href 前缀 `/api/files/` 认出它（见 .kb-file 那段 CSS）。
          const files = Array.from(clipboardData.files || [])
          const images = files.filter((f) => f.type && f.type.startsWith('image/'))
          const attachments = files.filter((f) => !f.type || !f.type.startsWith('image/'))
          if (images.length || attachments.length) {
            event.preventDefault()
            images.forEach((file) => insertUploadedImage(view, ctx, file))
            attachments.forEach((file) => insertUploadedFile(view, ctx, file))
            return true
          }

          // 有 text/html = 网页 / Word / 富文本；vscode-editor-data = 从 VS Code 复制
          // （原插件会按语法高亮建代码块）；光标在代码块里 = 原生逐行插入。
          // 这三种一律交回原插件，行为一点不动。
          if (clipboardData.getData('text/html') || clipboardData.getData('vscode-editor-data')) return false

          const raw = clipboardData.getData('text/plain')
          if (!raw) return false

          // 文本文件结尾那一个换行是"文件结束标记"不是内容，去掉，否则末尾凭空多一行
          const lines = raw.replace(/\r\n?/g, '\n').replace(/\n$/, '').split('\n')

          // 每个 \n → 一个**真 hardbreak**（渲染成 <br>），整篇留在**同一个段落**里。
          //
          // ⚠️ 这条曾被改成"每行一个段落"，**用户明确要求改回来**：那份改动把空行
          // 一起弄没了（1 空行 2 空行 3 粘进去变成三个紧挨的段落）。
          // 原因：markdown 里**「空行」和「段落」是两回事，而视觉上长得一模一样** ——
          //   · 空一行 = 两个连续 hardbreak（\n\`\n）→ **markdown 存得住**，往返也稳
          //     实测过：remark-parse 解析成 paragraph[text, break, break, text...]，
          //     stringify 回去一模一样
          //   · 空段落 / 多个空行 = CommonMark **直接折叠**，存不住
          //   · 段落间距 = 视觉量，markdown 里根本没有这个概念，只能由 CSS 统一给
          // 所以「精确保留空行」这件事**只有段内 hardbreak 能做到**。
          //
          // 代价是整篇一个段落 → 标题/列表是段落粒度 → 给某一行加标题会连整块一起变。
          // **那由 kbLineBlocks.js 的 `ApplyToVisualLines` 兜**：它在命令执行前
          // 先按选区内的换行把段落切开，选中的行才成为独立块。
          // 也就是说：**空行归粘贴管，粒度归命令管，两边不重叠**。
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
        },
        // 标题行首的退格 / Delete：按语雀那套（见 handleHeadingEdgeKey 的说明）。
        // 放在 view._props 里 ⇒ 比 milkdown 自带的「降级标题」早一步。
        handleKeyDown: handleHeadingEdgeKey
      }))
    })
    .use(listener)
    .use(commonmark)
    .use(gfm)
    // 可缩放图片。**必须排在 commonmark 之后** —— Milkdown 的 $node 用 upsertById
    // 按 id 覆盖，同名后注册的赢；放前面会被 preset 那份顶掉。
    // 细节（尺寸为什么存在 title 里、为什么用 nodeView 而不是 CSS）见这个文件。
    .use(imageNodePlugin())
    .use(imageResizeView())
    // 附件节点（2026-11-16）：正文里的「一个文件」是原子块（光标进不去、按 Delete 整块删），
    // 节点自带按扩展名分的图标。markdown 往返不变（仍是 `[文件名](url)`），老正文自动变节点。
    // ⚠️ 同样必须排在 commonmark 之后（$node 按 id 覆盖，后注册的赢）。
    .use(fileNodePlugin())
    .use(fileNodeView())
    // mermaid 代码块渲染成图。**纯视图层**：不改 schema、不改 markdown 解析与回写。
    .use(mermaidPlugin())
    // 通用交互能力：项目走"单独引入"模式没用 @milkdown/kit 聚合包，
    // history / clipboard 都装在 node_modules 但未 use——这一行补齐 undo/redo + markdown 粘贴解析。
    .use(history)
    .use(clipboard)
    // Ctrl+A 全选 + Mod-Enter 提交 + 代码块的 Shift-Enter 跳出（上面三个 keymap plugin）
    .use(selectAllKeymap)
    .use(submitKeymap)
    .use(codeBlockKeymap)
    // 块级命令的粒度对齐「视觉行」——见这个文件顶部说明与 kbLineBlocks.js
    .use($applyToVisualLines)
    // 链接交互（单击选中 / Ctrl+单击打开 / 工具条 / 插入置灰）
    .use(linkInteractions)
    // 字号 / 字体：mark + remark 解析插件 + remark 输出 handler（详见 kbFontMark.js）
    // ⚠️ `$markSchema` / `$remark` 都是 **[ctx, plugin] 两个插件**，只注册后半个
    // 会报 "Context \"font\" not found" —— 那个 ctx 存的是 schema 工厂，没人 inject 它。
    .use([fontSchema.ctx, fontSchema.mark])
    .use([remarkFontSpan.options, remarkFontSpan.plugin])
    .use(remarkFontSpanStringify)
    // 命令必须注册，否则 runCmd 会报 Context "SetFont" not found
    .use(setFontCommand)
    .use(clearFormattingCommand)
    // 标题折叠（箭头 + 隐藏后续内容）
    .use(foldPlugin)
    // 正文关键词高亮 + 定位（详情页的检索框用；纯装饰，不改内容）
    .use(searchHighlightPlugin)
    // 知识点自动链接（正文里提到别的知识点时自动变链接；纯装饰，不改内容）
    .use(autoLinkPlugin)
)

// 编辑器就绪信号。`@milkdown/vue` 的 useEditor 返回 `{ loading, get }`（见其 types.d.ts），
// **loading 由 true 翻 false 的那一刻，就是视图建好、正文已经按 defaultValueCtx 渲染完**。
//
// 给谁用：详情页要在这个时刻才去量滚动位置。编辑器没就绪时正文只有 `min-height: 135px`，
// 外层容器看起来"装得下" → 不滚；等正文长出来又没人再触发 —— 表现就是"有时候进来正常、
// 有时候没反应"。**等一个异步组件画完，要用它自己的就绪信号，不要用定时器猜。**
watch(loading, (v) => {
  if (!v) {
    emit('ready')
    // 大纲要在这时候也报一次：`markdownUpdated` 第一次不触发（见 emitOutline 的说明）
    emitOutline()
  }
})

// ==================== 粘贴图片 ====================

/**
 * 插一张粘贴进来的图片：**先本地预览，再换成真链接**。
 *
 * ## 为什么先插 blob 预览
 *
 * 等上传完再插的话，那一两秒里编辑器**什么都没有**，用户会以为没贴上、于是再贴一次。
 * 先用 `URL.createObjectURL` 把本地图显出来，上传完再把节点的 `src` 换成真地址，
 * 视觉上就是"图稳稳在那儿，然后自己变成了能存下来的东西"。
 *
 * ## 用 blob URL 当"这一次上传"的标识
 *
 * 换成真链接时要按节点位置改，而**上传是异步的**：等它回来，文档可能已经被
 * 用户继续编辑过了，之前记的下标早就废了。所以按 `src` 反查节点位置 ——
 * `createObjectURL` 每次都产生一个独一无二的 URL，正好当这次上传的身份证。
 *
 * ⚠️ **已知的一个窄口子**：预览期间正文里是 `blob:` 地址，而正文是**失焦自动保存**的。
 * 那一两秒里如果用户正好切走，存进去的就是个失效的 blob 地址（刷新后图裂）。
 * 上传成功会立刻触发一次 markdown 更新把正确地址覆盖回去，窗口极小。
 * 真要彻底堵，得让"上传中"期间不落库 —— 那是另一件事，这里没做。
 */
function insertUploadedImage(view, ctx, file) {
  const blobUrl = URL.createObjectURL(file)
  // 命令返回 false = 这个位置插不进去（比如光标在一个收不住 inline 节点的容器里）。
  // **必须接住**：返回 false 而不吭声的话，用户看到的是"粘贴了但什么都没发生"，
  // 且控制台零报错 —— 那是最难查的一类症状。
  const inserted = ctx.get(commandsCtx).call('InsertImage', { src: blobUrl, alt: file.name || '' })
  if (!inserted) {
    URL.revokeObjectURL(blobUrl)
    ElMessage.error('这个位置插不了图片')
    return
  }
  uploadFile(file, 'contents')
    .then((res) => {
      const real = fileUrl(res?.data?.path)
      if (!real) throw new Error('返回路径为空')
      swapImageSrc(view, blobUrl, real)
    })
    .catch(() => {
      // 撤掉预览图（正文不能留着一个失效的 blob: 地址），提示交给 request.js
      removeImageBySrc(view, blobUrl)
    })
    .finally(() => URL.revokeObjectURL(blobUrl))
}

/**
 * 粘贴一个**非图片**文件（pdf / docx / mp4 …），插一条普通 markdown 链接。
 *
 * ## 为什么是链接而不是新节点
 *
 * markdown 没有「附件」这种东西，语雀那种卡片是它自己的私有节点。
 * 我们用标准 `[文件名](url)`：任何 markdown 工具都认，正文是纯文本，
 * 而「这是个附件」的判断放在渲染层（`<a href>` 以 `/api/files/` 开头且不是图片）。
 * 代价是**编辑态里它就是个普通链接**，不像语雀那样显示成卡片 —— 见 .kb-file 那段 CSS，
 * 至少视觉上能认出是个文件。
 *
 * ## 和图片不一样的地方
 *
 * 没有 blob 预览：附件不是「看一眼内容」，用户要的就是这个文件名，
 * 所以直接等上传完再插。多等的那一两秒里编辑器没东西，但用户知道自己在传文件。
 */
function insertUploadedFile(view, ctx, file) {
  const name = file.name || '文件'
  uploadFile(file, 'contents')
    .then((res) => {
      const url = fileUrl(res?.data?.path)
      if (!url) throw new Error('返回路径为空')
      const { schema } = view.state
      // ⚠️ **插附件节点，不再插一段链接文字**（2026-11-16）：
      // 链接是文字 ⇒ 光标能停在文件名中间（点文件右侧的空白会被吸进文件名里），
      // 节点是原子 ⇒ 进不去、选中即整体、Delete 整块删。markdown 里仍是 `[名](url)`。
      const fileType = schema.nodes.kbFile
      if (!fileType) {
        // 节点没注册上（理论上不会）⇒ 退回老路插链接，别让文件传不上去
        const link = schema.marks.link.create({ href: url })
        view.dispatch(
          view.state.tr.replaceSelection(
            new Slice(Fragment.from([schema.nodes.paragraph.create(null, [
              schema.text(name, link),
              schema.text(' ')
            ])]), 0, 0)
          )
        )
        return
      }
      view.dispatch(
        view.state.tr.replaceSelectionWith(
          fileType.create({ href: url, name })
        )
      )
    })
    // 不弹提示：走的是 axios 实例，request.js 已经是唯一提示出口。
    // 这里原来弹的「文件上传失败」会让一次失败出两条、还内容不同。
    .catch(() => {})
}

/** 按 src 找 image 节点的位置；找不到返回 null */
function findImagePos(doc, src) {
  let found = null
  doc.descendants((node, pos) => {
    if (found !== null) return false
    if (node.type.name === 'image' && node.attrs.src === src) {
      found = pos
      return false
    }
    return true
  })
  return found
}

function swapImageSrc(view, from, to) {
  const pos = findImagePos(view.state.doc, from)
  if (pos === null) return
  const node = view.state.doc.nodeAt(pos)
  view.dispatch(view.state.tr.setNodeMarkup(pos, null, { ...node.attrs, src: to }))
}

function removeImageBySrc(view, src) {
  const pos = findImagePos(view.state.doc, src)
  if (pos === null) return
  view.dispatch(view.state.tr.delete(pos, pos + view.state.doc.nodeAt(pos).nodeSize))
}

function runCmd(key, payload) {
  // 根因：@milkdown/core 7.x 的 commandsCtx 默认值是 CommandManager 对象，不是 CmdTuple 数组，
  // 没有 .find() 方法。正确 API 是 CommandManager.call(key, payload)。
  // 命令 key 是 $command("ToggleStrong", ...) 里那个 PascalCase 字符串，不是小写驼峰。
  get().action((ctx) => {
    ctx.get(commandsCtx).call(key, payload)
  })
}

/**
 * 当前选区（**非空**），没有就返回 null。
 *
 * "先选中一段文字再点按钮"这个前置，三个按钮（插入链接 / 设为目录链接 / 字号字体）
 * 都要查一遍。查法三处各写一遍的话，哪天加个新按钮就会漏掉一处判空 ——
 * 漏掉的表现是**点了没反应**（不是报错），最难查的那一类。
 */
function currentRange() {
  if (!hasSelection.value) return null
  const view = getView()
  if (!view) return null
  const { from, to } = view.state.selection
  if (from === to) return null
  return { from, to }
}

// 插入链接：**先选中一段文字，再点工具栏那个按钮**（没选中时按钮是灰的，点不动）。
// URL 在贴着选区的小输入框里输，不再弹居中对话框——原来那个对话框的问题是
// 选区和输入框分处屏幕中间和正文两处，输完 URL 眼睛要重新找一遍刚才选的是哪几个字。
function insertLink() {
  const range = currentRange()
  if (!range) return
  openLinkInput('ToggleLink', '', range)
}

function confirmLinkInput() {
  const { cmd, value, range } = linkInput.value
  // **href 允许为空**：链接可以只是个标记——先把这段文字标成链接，地址以后再填。
  // 原来这里是 `if (!href || !range) return`，输入框关了、链接没插、界面上什么也没发生。
  const href = value.trim()
  hideLinkInput()
  if (!range || !restoreRange(range)) return
  runCmd(cmd, { href, title: '' })
  hideLinkBar()
}

/**
 * 「设为目录链接」：和「插入链接」**同一个前置**（先选中文字）、**同一个落点**
 * （浮层贴着选区，用的是同一个 showFloat）、**同一条命令**（ToggleLink）。
 * 唯一换掉的是气泡里那个输入框——手输地址没人会输，
 * 而"这段字指向哪个目录"是个能选、不该手打的问题。
 */
function insertCategoryLink() {
  const range = currentRange()
  if (!range) return
  showFloat(catLink, catLinkEl, range, {})
}

/**
 * 选完即生效，**没有「确定」按钮**。
 * el-tree-select 点一下节点就已经是选定了，弹层随即收起——确定按钮得另找个地方挂，
 * 而挂哪儿都会变成"多点一次"。点别处 / Esc = 取消（见 onDocumentMouseDown）。
 */
function onCategoryPicked(value) {
  const range = catLink.value.range
  hideCatLink()
  // 空 value 只可能是"没选就关掉了"，不 insert —— 那时候没有可插的东西，
  // 而 runCmd 会拿空 href 去建一个"指向当前页"的链接，是静默的错误结果而不是失败。
  if (!value || !range || !restoreRange(range)) return
  runCmd('ToggleLink', { href: CATEGORY_HREF_PREFIX + value, title: '' })
}

// ==================== 字号 / 字体 / 清除格式 ====================

/**
 * 当前档位浮层该显示哪些选项。**两套档位形状一样**（key / label / size / family /
 * preview），所以这里一行切换就够，不用写两套映射。
 */
const formatOptions = computed(() =>
  (formatPick.value.kind === 'size' ? FONT_SIZES : FONT_FAMILIES)
)

/**
 * 打开档位浮层。**贴在触发它的那个按钮下面**，不是选区下面——用户刚点的是按钮。
 *
 * btn 是按钮自己（从点击事件拿），不是整条工具栏：两个按钮共用一条工具栏，
 * 贴工具栏的话字号和字体弹出来的位置会一模一样。
 */
function openFormatPick(kind, btn) {
  const range = currentRange()
  if (!range || !btn) return
  const rect = btn.getBoundingClientRect()
  formatPick.value = {
    visible: true,
    kind,
    range,
    x: rect.left,
    y: rect.bottom + 6
  }
  nextTick(() => {
    const el = formatPickEl.value
    if (!el) return
    const h = el.offsetHeight
    // 顶到屏幕下边就翻到按钮上方
    const below = rect.bottom + 6 + h
    const y = below > window.innerHeight ? Math.max(8, rect.top - h - 6) : rect.bottom + 6
    formatPick.value = { ...formatPick.value, x: rect.left, y }
  })
}

function hideFormatPick() {
  hideFloat(formatPick)
}

/**
 * 选一档 → 套到当前选区上。
 *
 * **一个命令管两个维度**（SetFont）：档位里没带的那一维是 null，意思是"这一维我不管"，
 * 命令会保留选区上已有的值。分两个命令的话，选字号那个命令必须把 family 也传上去，
 * 不然会把已设的字体抹掉。
 */
function pickFormat(opt) {
  const range = formatPick.value.range
  hideFormatPick()
  if (!range || !restoreRange(range)) return
  runCmd('SetFont', { size: opt.size, family: opt.family })
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
