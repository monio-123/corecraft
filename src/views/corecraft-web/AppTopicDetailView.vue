<template>
  <div ref="detailRef" class="detail">
    <!-- ⚠️ **页内顶栏（.bar）整条撤了**（2026-11-08 阅读 Dock 改版，用户拍板）：
         进详情页第一眼只剩标题和正文。
         **2026-11-09 v10 再改版**：头部（标题 + meta + 可选工具栏）**固定不滚**，
         正文（.ProseMirror）是**本页唯一的滚动容器**（壳 .app-content 在详情页收滚），
         底部 Dock 退役。返回 = 卡片左边框上的玉签钮（悬停展开）；
         AI / 大纲 / 工具栏 / 全屏 / 删除 = meta 行右侧图标一排；
         类型 = meta 行的 chip 下拉（低频收起）；进度线贴头部底缘。
         —— **滚到正文任何位置都能直接返回**，不用先滚回页顶。
         「这条知识在知识库里处于什么位置」由 **AppLayout 顶栏**的目录路径回答
         （2026-11-08 移上去的，原来是这一页页头里的面包屑）。目录归属这里**只读不改**
         （⚠️ **2026-11-05 晚「拖动归类」整套删了** ⇒ 详情页不再有"归类在别处做"这件事）。
         未归类（categoryId 为 null）时顶栏什么都不显示：没有位置信息可呈现时，
         一个"未归类"的标签只是噪音。 -->

    <!-- ⚠️ **载入中 = 同构文档骨架**（2026-11-13 统一加载层）。
         骨架画在**正文那张白卡里**（.content 那个盒子），形状 = 标题 + meta + 段落。
         为什么不用转圈：转圈只说"在加载"，骨架同时说了"加载完会长什么样"，
         而且**加载前后容器高度接近** ⇒ 内容到达时页面不跳一下。
         ⚠️ 前 160ms 不画骨架（快请求直接出内容，不闪）。 -->
    <div v-if="loading">
      <KbLoading :loading="loading">
        <template #loading>
          <div class="doc">
            <div class="content">
              <KbSkeletonDoc />
            </div>
          </div>
        </template>
      </KbLoading>
    </div>
    <template v-else-if="topic">
      <!-- 内容框：白底卡片。**标题是框内第一行**（语雀那种"第一行就是标题"），
           不再是框外一个孤立的表单字段——框外那行 23px 粗体压在正文上方，
           整页看起来像一张详情表单，而不是一份文档。
           title 字段本身没变，只是换了个渲染位置：卡片、搜索、删除确认、首页
           照旧读 kp_topic.title，零后端改动。 -->
      <div class="doc">
      <div class="content">
        <!-- 内容：Milkdown WYSIWYG（ProseMirror 内核 + 可选工具栏，封装在 MilkdownEditor 内）
             **没有 :key** —— 编辑器是**复用**的（2026-10-27 性能优化）：
             每条知识点重建一整个 Milkdown（ProseMirror + 8 个插件 + 视图）要好几十秒的启动成本，
             而点知识点本该是"看一眼正文"的动作。
             换知识点时由 load() 调 `switchContent` 把新正文换进去（并清空撤销栈）。

             keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
             不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
             不会更新到 ProseMirror 视图 —— 所以换内容**必须**走 switchContent。

             标题走 #before-content 插槽递进去，于是框内顺序是
             「工具栏 → 标题 + meta → 正文」——工具栏在框顶、标题是文档第一行，和语雀一致。
             不用插槽就得把工具栏搬到标题上面，但工具栏必须在 MilkdownProvider 内部
             才能拿到编辑器 ctx，搬出去就废了；反过来把标题递进来，顺序自然就对了。 -->
        <MilkdownEditor
          ref="editorRef"
          v-model="editContent"
          :show-toolbar="toolbar"
          :category-tree="categoryTree"
          :ask-enabled="aiEnabled"
          @ready="onEditorReady"
          @outline="onOutline"
          @ask-ai="onAskAi"
          @export-md="onExportMd"
          @export-file="onExportFile"
        >
          <!-- **顶栏右端集群**（2026-11-16 用户：「是顶栏的右上角啊」）：
               文件动作在左、类型在最右。放在 `toolbar-end` 插槽里 ⇒ 它住在
               **编辑器顶栏（色带）的右端**，不是标题行。
               ⚠️ 实情仍在编辑器里：点「导出」调 editorRef.openExportMenu(按钮)
               ⇒ 菜单由编辑器弹（它有原文），按钮位置由页面定。 -->
          <template #toolbar-end>
            <button class="filebtn" type="button" ref="exportBtn" @click="openExport">
              <KbSvgIcon name="download" />
              导出
            </button>
          </template>
          <!-- 标题：无边框 input，点哪都能输，失焦即存。placeholder 是「请输入标题」
               不是「未命名」——标题本来就常是空的（createTopic 传 title: ''），
               所以这里不是"兜底占位"，而是一个**真的可以立刻点进去写**的标题位。
               上一版给它外面套了一层「＋ 起个名字」按钮再点一次才出输入框，
               是多出来的一跳，没必要。列表卡片那边也共用 TopicTitleInput。 -->
          <template #before-content>
            <!-- 返回：**玉签钮**（A 方案，2026-11-09 用户把 v10 的 B 月弧拍板换掉）。
                 骑跨在卡片左边框上、与标题第一行对齐；常态只露一枚 10px 窄玉片
                 （刻意做虚：半透明 + 减淡边框投影，用户反馈常态太显眼），
                 悬停卡片宽度动画展开成完整圆扣、透明度回满。
                 定位基准是 .editor__before（kb-editor.css 给了 position:relative），
                 left 负值 = 白边 + 框内距，正好把圆心钉在边框线上。
                 悬停展开后才是完整点击面，常态那一弯也能点。 -->
            <button class="back-edge" type="button" title="返回上一级" @click="goBack">
              <KbSvgIcon name="arrow-left" />
            </button>
            <!-- 标题独占一行。类型分段开关（学习/笔记）2026-11-09 定位在**标题行右端**
                 = 卡片右上角（用户原话「这个笔记的切换放在卡片的右上角会不会好看一点」；
                 v10 一度收进 meta 行 chip，这版挪上来）。
                 原来标签也在这行（pill + 添加 + 挑已有），2026-11-03 用户拍板：
                 「标签功能没啥用，从页面上去掉，后台逻辑暂时不去」⇒ 整块撤了。
                 ⚠️ **后端那套标签逻辑一行没动**（表 / 接口 / 服务 / store 层），
                 AI 归类和 AI 采纳仍然会写标签（只是界面上看不见）。
                 别在清理时把它当死代码删了 —— 见 brove-skills/corecraft-web-context/SKILL.md
                 「标签：界面已下线，后端有意保留」。 -->
            <div class="title-row">
              <TopicTitleInput
                :title="topic.title"
                class="title"
                @save="onTitleSave"
              />
            </div>
            <!-- meta 行：左边 = 保存状态 · 更新时间 · 字数；
                 右边 = 图标一排（AI / 大纲 / 工具栏 / 全屏 / 删除）。
                 v10 底部 Dock 退役后，这一排是页内所有导航/视图操作的唯一入口。
                 保存状态**常驻**（保存中/已保存），不再"存完 2 秒消失"：
                 它一隐一现会让整行左右跳。字数按去空白字符数算（概览口径）。 -->
            <div class="meta-row">
              <div class="meta">
                <span class="meta-save" :class="{ 'is-saving': saveStatus === 'saving' }">
                  <i class="meta-save__dot" />{{ saveLabel }}
                </span>
                <span v-if="topic.updatedAt" class="meta-dot" />
                <span v-if="topic.updatedAt" class="meta-time">更新于 {{ topic.updatedAt }}</span>
                <span class="meta-dot" />
                <span class="meta-words">{{ wordCount.toLocaleString() }} 字</span>
                <!-- ⚠️ **掌握度与「标记已学」从详情页撤走**（2026-11-12）：
                     掌握度搬到**目录书**（卡片右上角的档位签 + 书页顶部的概览条），
                     「标记已学」这个独立按钮整个取消 —— 用户定的语义是
                     「没有掌握度就是未学，有掌握度就是已学习」⇒
                     考一次（随堂测验/章节考试）就等于"学过这条"，
                     不需要用户再手点一下"标记已学"。
                     ⇒ 这一行恢复成：保存状态 · 更新时间 · 字数。 -->
              </div>
              <div class="hact">
                <!-- 正文检索的入口**不在这里**（2026-11-09 用户：「复用现有的搜索框」）：
                     在详情页时顶栏搜索框就是本文实时检索（框里还带 ↑↓ 与命中计数），
                     原来这个「在本文检索」按钮连同正文里那个独立检索栏整套撤了。 -->
                <KbTooltip v-if="aiEnabled" content="AI 助手">
                  <button class="hact-btn" type="button" @click="askOpen = true">
                    <KbSvgIcon name="sparkle" />
                  </button>
                </KbTooltip>
                <!-- 大纲按钮的 .stop 不能省：文档级「点空白收起浮层」监听在 document 上，
                     不挡冒泡的话刚点开就被它收走（沿用 Dock 时代的教训） -->
                <button
                  v-if="outline.length"
                  class="hact-btn"
                  type="button"
                  title="本页大纲"
                  @click.stop="outlineHidden = !outlineHidden"
                >
                  <KbSvgIcon name="list" />
                </button>
                <span class="hact-sep" />
                <button
                  class="hact-btn"
                  :class="{ 'is-on': toolbar }"
                  type="button"
                  :title="toolbar ? '隐藏编辑工具栏' : '显示编辑工具栏'"
                  @click="toolbar = !toolbar"
                >
                  <!-- 工具栏开关（2026-11-16）：三条格式行 = 「点了会出一排格式工具」。
                       原来是 gear（设置按钮的样子，认不出是格式栏）／T 形，两版都被否。 -->
                  <KbSvgIcon name="format-lines" />
                </button>
                <button class="hact-btn" type="button" :title="immersive.on ? '退出全屏 · Esc' : '全屏阅读'" @click="immersive.toggle">
                  <KbSvgIcon name="fullscreen" />
                </button>
                <span class="hact-sep" />
                <button class="hact-btn is-danger" type="button" title="删除这条知识" @click="handleDelete">
                  <KbSvgIcon name="trash" />
                </button>
              </div>
            </div>
            <!-- 阅读进度线：贴在固定头部的底缘（Dock 退役后从 Dock 身上挪到这里）。
                 左右负边距 = 白边 + 框内距，让线贯通整张卡片宽，而不是只跟文字列一样宽。
                 ⚠️ **只在真有得读的时候画**（判据见 progressShown）：
                 ① 不能滚（没有滚动条）不画（2026-11-10 用户：「如果界面上没有滚动条的话
                    就不要显示这个了」）—— 满格进度线摆在还没开始读的界面上，
                    表达的是"读完了 100%"，那是在说假话；
                 ② 能滚但只多一点点也不画（2026-11-11 用户：数据少没必要展示）——
                    **还要滚的量得超过一整屏**（首屏可见 < 50%）才画，打一两个回车不算。 -->
            <div v-if="progressShown" class="read-progress" aria-hidden="true"><i :style="{ width: progressPct + '%' }" /></div>
          </template>
        </MilkdownEditor>
      </div>

      <!-- 大纲浮层：正文里的标题层级，点一条跳到对应标题。
           **只在正文有标题时才有**——没有标题的知识点（大多数）挂一个空面板
           是纯噪音。语雀那套就是这样。实现见 TopicOutline.vue。
           2026-11-08 阅读 Dock 改版：常驻右栏撤了，改成**按需弹出的浮层**；
           2026-11-09 v10：入口 = meta 行图标排的「大纲」按钮（Dock 已退役）。
           （floating 浮层形态、显隐归这一层的 outlineHidden）——刚进详情页第一眼
           只剩标题和正文。点一条自动收起（TopicOutline 在 floating 形态下自己收）。
           大纲里的折叠**只管大纲自己**（折掉这一支下级条目），不碰正文 ——
           正文那节折不折，用户自己在正文标题的箭头上点。 -->
      <TopicOutline
        ref="outlineRef"
        v-model:hidden="outlineHidden"
        :items="outline"
        :active-id="activeHeadingId"
        floating
        @navigate="goHeading"
      />
      </div>


      </template>

    <!-- 打开的这条已经没了。**只说事实，不解释、不给"回到列表"按钮**——
         有内容时卡片左边框上本来就有「返回」玉签钮（同一个 goBack），这里再摆一个就是重复。 -->
    <!-- ⚠️ **载入失败**（2026-11-10 补）：接口报错时**不是"这条知识不存在"** ——
         它还在，只是这次没拉下来 ⇒ 措辞要分开，且给一个「重试」，
         否则用户看着一句"不存在"会以为知识点被谁删了。
         错误**详情**由 request.js 弹（那里是全站唯一的提示出口）⇒ 这里不重复弹。 -->
    <div v-else-if="loadFailed" class="state">
      <p>加载失败</p>
      <p><el-button size="small" @click="load">重试</el-button></p>
    </div>

    <div v-else class="state">
      <p>这条知识不存在</p>
    </div>

    <TopicAskDrawer
      v-model:open="askOpen"
      v-model:ask-input="askInput"
      :ask-w="askW"
      :asking="asking"
      :messages="messages"
      :can-ask="canAsk"
      :guide-questions="guideQuestions"
      :current-endpoint="currentEndpoint"
      :ai-endpoints="aiEndpoints"
      :quiz-on="quizOn"
      :quiz-phase="quizPhase"
      :quiz-loading="quizLoading"
      :quiz-questions="quizQuestions"
      :quiz-plan="quizPlan"
      :quiz-attempt-id="quizAttemptId"
      :quiz-note="quizNote"
      :quiz-error="quizError"
      :quiz-quiet="quizQuiet"
      :quiz-stale="quizStale"
      :quiz-task="quizTask"
      @body-ref="(el) => { askBodyRef = el }"
      @grip-down="onGripDown"
      @reset-width="resetAskWidth"
      @submit-ask="submitAsk"
      @ask-now="askNow"
      @cancel-ask="cancelAsk"
      @use-endpoint="useEndpoint"
      @open-insert="openInsert"
      @open-adopt="openAdopt"
      @open-quiz="openQuiz"
      @close-quiz="closeQuiz"
      @answer-quiz-task="answerQuizTask()"
      @redo-quiz="redoQuiz"
      @regenerate-quiz="regenerateQuiz"
    />

    <!-- 采纳预览。放在抽屉外面但还在这一层的 template 里：它针对的是抽屉里那一轮问答，
         关掉抽屉不该把一个已经打开的确认框一起带走 -->
    <AiAdoptDialog
      v-model="adoptOpen"
      :turn="adoptTurn"
      :categories="categories"
      :topic="topic"
      @confirm="onAdoptConfirm"
    />

    <!-- 「应用到正文 / 应用为新的知识点」的确认框。同样放在抽屉外面：它针对的是
         **这一条**的正文，关掉抽屉不该把一个已经打开的确认框一起带走。
         **它自己不写库**：只把内容交出来，写回编辑器之后由详情页那条
         1.5s 防抖的自动保存落库（建新知识点那条链例外：那条本来就要新建一条记录）。

         ⚠️ **streamHtml / adjustError 是给"重新生成"用的状态通路**：
         上一版重新生成失败时，错误只出现在抽屉里，而抽屉被这个弹窗挡着 ⇒
         用户只看到"转圈转完了、内容没变"。现在生成中右栏显示流式预览，
         失败时错误原文直接摆在这个弹窗里。 -->
    <AiInsertDialog
      v-model="insertOpen"
      :answer="insertAnswer"
      :base-md="insertBase"
      :spot="insertSpot"
      :blocks-in-range="blocksInRange"
      :doc-of="docOfBody"
      :adjusting="insertAdjusting"
      :stream-html="insertAdjustTurn?.html || ''"
      :adjust-error="insertAdjustError"
      :mode="insertMode"
      :saving="insertSaving"
      @adjust="onInsertAdjust"
      @confirm="onInsertConfirm"
    />

    <!-- ⚠️ 底部悬浮阅读 Dock 已退役（2026-11-09 v10，用户拍板）：
         头部改为「固定不滚」后，返回/操作本来就常驻，Dock 的智能显隐、peek 把手、
         底部热区、固定偏好整套失去存在意义。功能去向：返回 → 卡片左边框玉签钮；
         AI/大纲/工具栏/全屏/删除 → meta 行右侧图标排；进度 → 头部底缘进度线。
         TopicReadingDock.vue 文件已删。 -->
  </div>
</template>

<script setup>
// 让 component.name = 'AppTopicDetail' 对齐 route.name，配合 AppLayout 的 keep-alive 缓存实例。
defineOptions({ name: 'AppTopicDetail' })
import { ref, computed, onMounted, onBeforeUnmount, onActivated, onDeactivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import { exportPdf, exportWord } from '../../utils/exportDoc'
import { htmlOf } from '../../utils/kbDocHtml'
import KbSvgIcon from '../../components/corecraft-web/KbSvgIcon.vue'
import KbTooltip from '../../components/corecraft-web/KbTooltip.vue'
import KbLoading from '../../components/corecraft-web/KbLoading.vue'
import KbSkeletonDoc from '../../components/corecraft-web/KbSkeletonDoc.vue'
import TopicOutline from './TopicOutline.vue'
import TopicTitleInput from './TopicTitleInput.vue'
import TopicAskDrawer from './TopicAskDrawer.vue'
// ⚠️ **MasteryBadge / LearnedToggle 不再在这里**（2026-11-12）：
//     掌握度搬到目录书（卡片档位签 + 书页概览条）；
//     「标记已学」按钮整个取消（考一次就等于学过）。
import AiAdoptDialog from './AiAdoptDialog.vue'
import AiInsertDialog from './AiInsertDialog.vue'
// ⚠️ 出卷那几个函数（paperTaskPayload / consumePaperTask / loadExamSpec / typesBadNote）在
// `useQuiz.js`（随堂测验自成一块）⇒ 这里不再直接引。
import { useQuiz } from './useQuiz'
import { useImmersive } from './useImmersive'
import {
  aiEnabled, useEndpoint, currentEndpoint, aiEndpoints
} from './useAi'
// ⚠️ 流式问答那套（`ask` / 引导问题 / 改写规范 / 答案渲染 / 抽屉宽度）
// 搬进了 `useTopicAsk.js` ⇒ 这里不再直接引。
import { useTopicAsk } from './useTopicAsk'
import { useTopicBodyFind } from './useTopicBodyFind'
import { useTopicInsert } from './useTopicInsert'
import { toCategoryTree } from '../../utils/categoryTree'
import { fetchTopic, updateTopic, confirmDeleteTopic, resolveTags, getCategories, openTopicCategoryId, fetchTopicIndexFresh, getCategorySubtreeIds, getCategoryPath, getTopicIndex, ensureCategoriesLoaded } from './useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

const topic = ref(null)
const loading = ref(true)
/**
 * 载入失败（接口报错 / 这条被删了）。
 *
 * <p>⚠️ **原来没有这个状态**（2026-11-10 补）：`load()` 只有 try/finally、**没有 catch**
 * ⇒ `fetchTopic` 抛错时 `loading` 归 false、`topic` 仍是 null
 * ⇒ 模板的 `v-if="loading"` / `v-else-if="topic"` **两个分支都不成立**
 * ⇒ **整页空白**（既没有"这条知识不存在"，也没有"加载失败"）。
 * 界面上看着就像"点了没反应"—— 而用户点进来是有明确动作的（点了某个知识点），
 * 空白页既不告诉他出了什么事，也不给他下一步。
 *
 * <p>错误**详情**由 request.js 弹（那里是全站唯一的提示出口）⇒ 这里只摆状态，
 * 不重复弹一遍（两处都弹 = 同一件事两条提示，见 request.js 的注释）。
 */
const loadFailed = ref(false)
// 根节点：用来取正文滚动容器 `.doc .ProseMirror`（v10 起本页唯一滚动容器，
// 进度线/归顶都找它）；AI 弹窗里也有编辑器，查询限定根节点里面别挂错。
const detailRef = ref(null)
// 编辑器实例。大纲点击跳转要调它的方法（编辑器在 MilkdownProvider 里面，
// 外层拿不到它的 ctx，只能由 MilkdownEditor 暴露出来的方法代劳）。
const editorRef = ref(null)

/**
 * 自动链接词表：**当前条目所属一级目录（一本书）里的其它知识点**（标题 → id）。
 *
 * <p>为什么不拿全库：误链率会明显上升（"引用类型"这种词在别的主题里可能是另一个意思），
 * 词表越大算得也越慢。同一本书内的跳转才是有意义的。
 * 但**不能只取条目所在的那一层目录**（见 {@link autoLinkRootDir}）。
 */
const autoLinkEntries = ref([])

/**
 * 重算词表并推给编辑器。
 *
 * <p>**在渲染层做，不在生成时做**：正文里不存任何链接标记，每次打开现算 ——
 * 新建的知识点自动能被链上、改标题自动跟随、删了自动退成纯文字。
 */
/**
 * 自动链接词表的范围：**当前条目所属「一级目录」的整棵子树**（一本书）。
 *
 * <p>⚠️ 不能用「条目所在目录的子树」：实测《垃圾收集算法与收集器总览》（2.3）正文里
 * 提到的《引用计数算法》《可达性分析》《GC Roots》《引用类型》都在**兄弟目录 2.2** 下
 * ⇒ 只取自己那层子树时**一条都链不上**（正文明明在讲它们）。
 * 跨章节的引用恰恰是最该链的，所以范围要放到"同一本书"。
 */
function autoLinkRootDir(categoryId) {
  if (!categoryId) return null
  const chain = getCategoryPath(categoryId)   // 根 → 自己
  return chain.length ? chain[0].id : categoryId
}

async function loadAutoLinks() {
  const t = topic.value
  if (!t?.id) {
    autoLinkEntries.value = []
    return
  }
  // ⚠️ **先等目录树就绪**：`getCategoryPath` 找不到节点时返回空链（不报错），
  // 于是"所属一级目录"会退化成"条目自己所在的那层"，`getCategorySubtreeIds` 也只
  // 剩它自己 ⇒ 词表空、正文一处链接都没有（实测踩过：直接新标签打开详情页时，
  // 目录树还在路上，装饰数 0；先在站内点进来反而是好的）。
  try {
    await ensureCategoriesLoaded()
  } catch {
    // 拉不到树就按"未归类"处理（下面那条分支），正文照常看
  }
  const rootId = autoLinkRootDir(t.categoryId)
  // 未归类（没有所属的那本书）：**宁可不链**。拿全库一千多条标题去匹配，
  // "内存泄漏""引用类型"这种词在别的主题里很可能是另一个意思 ⇒ 满屏误链。
  if (!rootId) {
    autoLinkEntries.value = []
    applyAutoLinks()
    return
  }
  const scope = getCategorySubtreeIds(rootId)
  const build = (list) => (list || [])
    .filter((x) => x.id !== t.id && (!scope || scope.has(x.categoryId)))
    .map((x) => ({ term: x.title, id: x.id }))

  // ① 先用**内存里已有的**索引（同步 ⇒ 打开就能画上，不用等接口回来）
  const cached = getTopicIndex()
  if (cached.length) {
    autoLinkEntries.value = build(cached)
    applyAutoLinks()
  }
  // ② 再拉一份全集（不走共享缓存：它可能是"搜索态筛过的"，也可能停在旧快照）
  try {
    autoLinkEntries.value = build(await fetchTopicIndexFresh())
  } catch {
    // 拉不到就用缓存那份（正文照常看，不该因为这件事让整页报错）
  }
  applyAutoLinks()
}

/** 把词表交给编辑器（编辑器还没建好就等着，@ready / onActivated 会补） */
function applyAutoLinks() {
  editorRef.value?.setAutoLinks(autoLinkEntries.value)
}

/**
 * 给对比框和答案渲染用：**markdown → ProseMirror doc**。
 *
 * 对比区要显示"和正文同一套"的渲染 ⇒ 必须用**编辑器自己的解析器**（同一个 schema、
 * 同一套 toDOM），否则预览和正文对不上（标题层级、软换行、字号 mark、图片节点…）。
 * 另找一个 markdown 渲染器就是两套渲染 ⇒ 那种坑已经踩过一次。
 *
 * 编辑器在 MilkdownProvider 里面，外层拿不到它的 ctx ⇒ 这里代为转发。
 *
 * ⚠️ **定义在这里（紧跟 editorRef），不在用到它的段落里** ——
 * 问答那套（`useTopicAsk`）在下面要用它；`const` 不会因为"写在后面"就自动提前。
 */
const docOfBody = (md) => editorRef.value?.docOf(md) ?? null

// 右侧大纲：正文里的标题层级。**由编辑器报上来**（@outline），
// 页面自己不解析 markdown —— 解析一份和编辑器里那份是两份真相，
// 大纲和正文对不上的时候，用户看到的是"大纲指错了地方"。
const outline = ref([])
// 当前所在章节。点大纲条目时高亮它。
const activeHeadingId = ref('')
// 大纲浮层的显隐（true = 藏着）。默认收起、meta 行图标排的「大纲」按钮按需弹出
// （刚进详情页第一眼只剩标题和正文）。
const outlineHidden = ref(true)
// TopicOutline 的实例：浮层"点空白收起"要拿它的根节点判点击位置
const outlineRef = ref(null)

/**
 * 大纲浮层「点空白处收起」。浮层盖在正文上，通用的 dismiss 交互（测试稿也这么做）。
 *
 * ⚠️ meta 行那个「大纲」开合按钮**不会被这条误伤**：那个按钮的点击自带 .stop
 * （不冒泡到 document），开了就开、关了就关，不会"刚点开就被收走"。
 */
function onDocClick(e) {
  if (outlineHidden.value) return
  if (outlineRef.value?.$el?.contains?.(e.target)) return
  outlineHidden.value = true
}

// 监听只在本页活着的时候挂：keep-alive 切走后列表页还在用同一个 document。
onMounted(() => document.addEventListener('click', onDocClick))
onActivated(() => document.addEventListener('click', onDocClick))
onDeactivated(() => document.removeEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

/** v10：本页唯一滚动容器 = 正文 .ProseMirror（壳在详情页已收滚）。
    进度线贴固定头部底缘，监听的是正文滚动。查询限定 `.doc` 里：
    AI 弹窗里也有编辑器（.ProseMirror 不止一个），别监听错。 */
const progressPct = ref(0)
/**
 * **进度线显隐门槛：还要滚的量 > N 屏**（N = `PROGRESS_MIN_SCREENS`）。
 *
 * <p>1 屏 ⇔ **首屏可见 < 50%**（用户给的 50% / 30% 两个候选里取 50%）：
 * 30% 要求内容超过 3.3 屏才画线，知识库里多数笔记会永远看不到这条线；
 * 50% 对应「至少还要滚一整屏」，是阅读进度真正开始有意义的门槛。
 * 想更保守（首屏可见 < 30%）把常量改成 **2.33**（= 0.7 / 0.3）。
 *
 * <p>⚠️ 用「屏」不用绝对 px：窗口高度、正文字号、设备一变，`clientHeight`
 * 跟着变，门槛自动跟着走，不用另调。
 */
const PROGRESS_MIN_SCREENS = 1
/**
 * **现在要不要画进度线**。
 *
 * <p>⚠️ **不能滚就不画**（2026-11-10 用户原话：「如果界面上没有滚动条的话
 * 就不要显示这个了」）：一条 100% 的满进度线摆在**根本没有滚动条**的界面上，
 * 表达的是"读完了 100%"，可用户还没开始读 —— **那是在说假话**。
 *
 * <p>⚠️ **能滚但只多一点点也不画**（2026-11-11 用户：「对于这种数据其实比较少的
 * 是不是就没必要展示这个绿色的进度条了」）：正文刚比一屏多一两个回车，
 * 也要摆一条"读到哪儿"的线，是在说废话。判据 = **可滚动余量 > 一整屏**
 * （`scrollHeight - clientHeight > clientHeight`）—— 与「首屏可见 < 50%」等价。
 */
const progressShown = ref(false)
let bodyScroller = null
/** 正文容器尺寸观察器（窗口缩放 / 侧栏折叠 ⇒ 余量变了）。 */
let bodyRO = null
/**
 * 算一次「够不够长值得画线」+ 当前进度。
 *
 * <p>⚠️ **不画线时进度归 0**（不是 100）：线根本不画（见模板那行 v-if），
 * 但值留着会让「刚退出可画状态」的那一帧残留一个旧百分比 ⇒ 归零最干净。
 */
function onBodyScroll() {
  const el = bodyScroller
  if (!el) return
  const viewport = el.clientHeight
  const max = el.scrollHeight - viewport
  // viewport > 0 不能省：容器还没布局时 clientHeight=0，任意正余量都会满足门槛
  const enough = viewport > 0 && max > viewport * PROGRESS_MIN_SCREENS
  progressShown.value = enough
  progressPct.value = enough ? Math.max(2, (el.scrollTop / max) * 100) : 0
}
/**
 * 正文尺寸变了 ⇒ 「够不够长」可能变了 ⇒ 进度线该出现/消失。
 *
 * <p>⚠️ **为什么不能只靠 ResizeObserver**：`.ProseMirror` 是 `flex:1`，**高度是固定的**
 * （它是滚动容器，高度由 flex 分配）⇒ **内容变多不会改变它自己的盒高**
 * ⇒ ResizeObserver **根本不会触发**（而"正文变多 ⇒ 出现滚动条"恰恰是最主要的那个变化）。
 * ⇒ **真正的信号是内容变了**：编辑器的 `update:modelValue` → 外层 `editContent` 变化。
 *
 * <p>⚠️ **量之前要等一帧**：`markdownUpdated` 是**同步**回调，此时 DOM 还没重排
 * ⇒ `scrollHeight` 拿到的还是旧值 ⇒ 判据会慢一拍（线迟一点出现/消失）。
 * ⇒ `requestAnimationFrame` 推一帧再量。
 */
function refreshScrollState() {
  requestAnimationFrame(() => onBodyScroll())
}
function attachBodyScroll() {
  detachBodyScroll()
  bodyScroller = detailRef.value?.querySelector('.doc .ProseMirror') ?? null
  bodyScroller?.addEventListener('scroll', onBodyScroll, { passive: true })
  // 窗口/容器尺寸变了（缩放窗口、侧栏折叠）⇒ 余量变了 ⇒ 重算
  if (typeof ResizeObserver !== 'undefined' && bodyScroller) {
    bodyRO = new ResizeObserver(() => onBodyScroll())
    bodyRO.observe(bodyScroller)
  }
  onBodyScroll()
}
function detachBodyScroll() {
  bodyScroller?.removeEventListener('scroll', onBodyScroll)
  bodyRO?.disconnect()
  bodyRO = null
  bodyScroller = null
}
/**
 * **正文归顶之后**要做的事（排队，按先后顺序做）。
 *
 * <p>⚠️ **为什么要有这条队列**（2026-11-09 用户报「点进去还是没有自动跳转」）：
 * 归顶是「双 rAF 之后把 `scrollTop` 设成 0」，而定位是「立刻 `scrollIntoView`」——
 * 两个都是**异步**的，谁后落地谁说了算。
 * 原来的顺序是「先调 `resetBodyTop()`（排了个双 rAF）⇒ 再调定位（立刻滚）」，
 * ⇒ **归顶那一帧落在定位之后 ⇒ 把定位滚过去的位置又抹回 0** ⇒ 看着就是"定位没做"。
 * （手点 ↑↓ 之所以正常：那时候已经没有待执行的归顶在排队了。）
 *
 * <p>⇒ 归顶和定位**都走这条队列** ⇒ 顺序就是代码里写的顺序，不靠时序碰运气。
 */
let bodyTopChain = Promise.resolve()

/** 正文归顶后排队执行，避免归顶双 rAF 抹掉随后的搜索定位。 */
function afterBodyTop(fn) {
  bodyTopChain = bodyTopChain
    .then(() => new Promise((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
    }))
    .then(fn)
    .catch(() => {})
  return bodyTopChain
}

/** 换知识 / keep-alive 切回：正文归顶（只在切换条目时调，不挂 watch(topic)）。 */
function resetBodyTop() {
  afterBodyTop(() => {
    const pm = detailRef.value?.querySelector('.doc .ProseMirror')
    if (pm) pm.scrollTop = 0
    onBodyScroll()
  })
}

/** 编辑器就绪：挂载进度监听、应用自动链接装饰并补做首次搜索定位。 */
function onEditorReady() {
  attachBodyScroll()
  applyAutoLinks()
  locateBodySearch()
}

/**
 * **一次详情页会话里，同一个（文档 + 词）只自动定位一次**。
 *
 * <p>⚠️ 2026-11-12 用户：「搜索框有目标的时候，每隔十五秒左右正文就重新刷一下，
 * 滚动条又被拖到搜索目标的定位」。运行时插桩实测：dev server 里改一个被编辑器依赖的
 * 文件 ⇒ `MilkdownEditorBody.vue` 被 HMR 热替换 ⇒ `@ready` 重发 ⇒ 这里又滚一次
 * （15 秒是**文件被保存的节奏**，前端本身没有 15 秒定时器；30 秒那个只拉任务角标）。
 * 另外进场时 `load()` / `onEditorReady()` / `onActivated()` **三条路径各排一次**，
 * 实测连滚 3 次 —— 「自动定位」被绑在**任何就绪信号**上，缺一次判据。
 *
 * <p>⇒ 记账判据 = `文档 id + 词`：**定位成功才记**；之后任何就绪信号只补高亮、
 * 不再滚（编辑器重建后装饰会丢，得补回来）。离开详情页（onDeactivated）清账 ⇒
 * 下次进来照旧自动定位；换知识点 / 换词 ⇒ key 变 ⇒ 也会重新定位。
 *
 * <p>⚠️ 判据放在**本组件 setup 作用域**：HMR 重建的是编辑器子组件，详情页实例还在
 * ⇒ 账不会跟着编辑器一起丢；整页刷新则 setup 重跑、账归零 ⇒ 刷新后仍定位一次。
 */
onDeactivated(() => {
  detachBodyScroll()
  // 离开详情页 = 这次「进场」结束 ⇒ 下次再进来照旧自动定位；HMR 不触发 deactivate ⇒ 账留着。
  clearLocateTrack()
})
onBeforeUnmount(detachBodyScroll)

/** 编辑器报上来大纲 */
function onOutline(list) {
  outline.value = list
  // 换知识点后要清掉高亮：旧 id 在新正文里可能根本不存在
  if (activeHeadingId.value && !list.some((h) => h.id === activeHeadingId.value)) {
    activeHeadingId.value = ''
  }
}

// ============ 正文检索（拆出到 useTopicBodyFind.js） ============
const { applyBodySearch, locateBodySearch, clearLocateTrack } = useTopicBodyFind({
  route,
  editorRef,
  afterBodyTop
})

/** 点大纲条目 → 滚到那个标题 */
function goHeading(id) {
  activeHeadingId.value = id
  editorRef.value?.scrollToHeading(id)
}

// 全屏阅读：收起源的壳，只留这一页。和首页全屏录入、AppLayout 共用同一个判据。
const immersive = useImmersive()
// 编辑器工具栏显不显示。状态在**这一层**而不是 MilkdownEditor 内部，
// 因为"简洁/完整"的开关按钮画在这一层的 meta 行图标排里（v10，原 Dock ⋯ 菜单），
// 按钮和状态必须是同一份，否则切了按钮不动。
const toolbar = ref(false)
const editContent = ref('')
// 字数（meta 行用）：去空白字符数。markdown 符号也算在内 —— 这是"这篇大概多长"的
// 概览口径，不是统计报表，为它做一套 markdown 清洗不值得。
const wordCount = computed(() => (editContent.value || '').replace(/\s/g, '').length)

// 内容自动保存状态机：'idle'（不显示）| 'saving'（debounce 中 / 调用中）| 'saved'（保存成功，2s 后回 idle）
const saveStatus = ref('idle')
let saveTimer = null  // debounce 计时器
let statusTimer = null  // 'saved' 状态自动回 'idle' 计时器

/** meta 行上的保存状态。**常驻两态**（保存中/已保存）：文档已经在库里就是"已保存"，
    编辑期间是"保存中"。不再用"存完 2 秒消失"那一套 —— 状态一隐一现会让 meta 行左右跳。 */
const saveLabel = computed(() => (saveStatus.value === 'saving' ? '保存中' : '已保存'))

// ⚠️ **「关联知识点」那一块从前端删掉了**（2026-10-21）：
// 那块模板从上线到现在**一次都没渲染过** —— `kp_topic_relation` 表 0 条，
// 而 `enrichTopicResps` 里是 `if (!relations.isEmpty())` 才 setRelatedTopicIds，
// 字段永远为 null、`v-if` 从不成立；**而且 `.related` 那条样式压根没写过**
// （注释说"竖排"，CSS 里没有对应规则）。
// 没有建立关联的入口 ⇒ 这是一段"永远不执行的代码"，连同它的样式缺口一起留着，
// 只会让后来的人以为"关联知识点"已经做好了。
//
// **后端一个子都没动**：`kp_topic_relation` 表、`POST/DELETE /kp/topic/{id}/relation/{rid}`
// 两个接口、`enrichTopicResps` 的回填，全在原处（**数据层不动，要回滚随时能接回来**）。
// 真要做这个功能时，**先定设计**（关联类型？在前台哪个位置建、怎么选目标知识点？，
// 现有的统一模型里只有"目录（单选主归属）+ 标签（其余涉及）"两个轴），
// 再从那两个接口接着来 —— 别把这段"从来没跑过"的模板当现成实现捡回去。
//
// （背景：2026-10-13 我把它当成"后端根本不返回字段的死代码"删过一次，**那次判断是错的** ——
// 后端一直会返回，只是没有数据源。**这次删的理由不是"字段没人读"，是"这段 UI 从没存在过"**。）

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const id = Number(route.params.id)
    // URL 里的 id 不合法（手输 /topic/NaN 等）→ 直接走"知识点不存在"分支，不发请求避免后端
    // "Failed to convert 'NaN' to Long"。修传参方即可，fetchTopic / 后端不动。
    if (!Number.isFinite(id) || id <= 0) {
      topic.value = null
      return
    }
    // 只调详情接口（标签列表的懒加载那一支随标签界面一起撤了 —— 界面上
    // 不再有"挑已有标签"的地方，就不必再拉那份清单）
    const t = await fetchTopic(id)
    if (!t) {
      topic.value = null
      return
    }
    topic.value = t
    editContent.value = t.content || ''
    // 换了知识点 ⇒ 自动链接词表跟着重算（"当前条目"要排除，子树也变了）
    loadAutoLinks()
    // **编辑器是复用的**（不再每条知识点重建一个 Milkdown —— 重建一次要好几秒，
    // 而点知识点本该是"看一眼正文"的动作）⇒ 正文得**主动换进去**。
    // 外层改 v-model 不会更新 ProseMirror 的视图（见 MilkdownEditorBody 的注释）。
    // 第一次进来时编辑器还没建出来（editorRef 是 null）⇒ 不用换，它自己会用新值建出来。
    editorRef.value?.switchContent(t.content || '')
    // ⚠️ **换完正文要重算「够不够长值得画线」**（2026-11-10；2026-11-11 门槛收紧为「余量 > 一屏」）：`switchContent` 走 `suppressEmit`
    // ⇒ **不上报 `update:modelValue`** ⇒ `editContent` 不变 ⇒ 上面那条 watch 不会触发
    // ⇒ 不在这里补一次的话，**换知识点后进度线的显隐会停在上一条的状态**。
    refreshScrollState()
    // 换了一条知识：正文归顶（v10 后滚动容器是 .ProseMirror）
    resetBodyTop()
    // **从列表页搜索结果点进来 ⇒ 自动高亮那个词并定位到第一处命中**
    //（2026-11-09 用户：「点进对应的卡片后还是要自己去找」⇒ 拍板「自动带入并定位」）。
    // 词就是列表页那次搜索的词（URL 上的 `?q=` —— AppLearnView 的 searchContext 带着走）。
    // ⚠️ **没词不高亮**：清空顶栏框 = 这次检索结束，URL 的 `?q=` 也一起被壳去掉了
    // ⇒ 换知识点 / 刷新都不会把它复活（「删不掉」的 bug 就出在原来这里无脑带词）。
    // ⚠️ **这一步可能落空**（首次进详情页时编辑器还没建好）⇒ onEditorReady 会补做一次。
    locateBodySearch()
  } catch {
    // ⚠️ **必须有这一支**（2026-11-10 补）：原来只有 try/finally ⇒ 接口一抛错，
    // `loading` 归 false 而 `topic` 仍是 null ⇒ 模板两个分支都不成立 ⇒ **整页空白**
    // （看着就像"点了没反应"）。错误详情由 request.js 弹，这里只摆状态、不重复弹。
    topic.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/**
 * 标题保存。**走 applyPatch**（保存类动作的唯一出口）：改库 + 补本地快照都在里面。
 * 组件自己不再调 updateTopic —— 同一个动作两个出口，早晚有一处漏掉快照。
 * 存好了组件会收到回灌的 title，提示由组件弹（那边只有一处弹「已更新」的地方）。
 */
async function onTitleSave(title) {
  try {
    await applyPatch({ title })
  } catch {
    // 失败提示归 axios 拦截器；这里吞掉只是为了不产生 unhandled rejection
  }
}

async function saveContent() {
  if (!topic.value) return
  if ((topic.value.content || '') === editContent.value) return
  // 取消 pending debounce（避免重复保存）
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  saveStatus.value = 'saving'
  try {
    await applyPatch({ content: editContent.value })
    saveStatus.value = 'saved'
    if (statusTimer) clearTimeout(statusTimer)
    statusTimer = setTimeout(() => { saveStatus.value = 'idle' }, 2000)
  } catch (e) {
    // 提示归 request.js（走 axios 实例，唯一提示出口）；这里只把状态退回未保存。
    // 原来还 `throw e` —— 而唯一的调用点是 setTimeout 里那一句裸调用，
    // 没人接，于是每次保存失败都在控制台多一条 unhandled rejection，纯粹是噪音。
    saveStatus.value = 'idle'
  }
}

// 内容自动保存：用户编辑后 debounce 1.5s 调用 saveContent。
// load() 设置 editContent 时 watch 也会触发，但 (topic.value.content === newVal) 命中 → 直接 return，不保存。
watch(editContent, (newVal) => {
  // ⚠️ **正文变了 ⇒ 「够不够长值得画线」可能变了**（2026-11-10；2026-11-11 门槛收紧为「余量 > 一屏」）：这是进度线显隐的**主要信号源**
  // —— ResizeObserver 观察不到（`.ProseMirror` 高度固定，内容变多不改变它自己的盒高）。
  // 放在最前面：换知识点（switchContent）那一次也要重算（那时下面两个 return 会先命中）。
  refreshScrollState()
  if (!topic.value) return
  if ((topic.value.content || '') === newVal) return
  if (saveTimer) clearTimeout(saveTimer)
  saveStatus.value = 'saving'
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveContent()
  }, 1500)
})


// ============ 对这条知识提问（AI，零写入） ============
// 整套逻辑搬到了 `useTopicAsk.js`（2026-11-11 拆分）：抽屉宽度 / 流式问答 /
// 消息列表 / 渲染节流自成一块，详情页只留"调用它"的几行。
const {
  askOpen, askInput, askW, askWidth, resetAskWidth, asking, messages, askBodyRef, canAsk,
  guideQuestions, submitAsk, askNow, cancelAsk, onGripDown, loadGuideQuestions
} = useTopicAsk({
  topicId: () => (topic.value ? topic.value.id : null),
  baseMdOf: () => editContent.value,
  docOf: docOfBody
})


// ==================== 随堂测验 ====================

/**
 * 随堂测验：**在这个抽屉里就地考当前这条知识**。
 *
 * <p>实现搬到了 `useQuiz.js`（2026-11-11 拆分）：查锚、出卷、状态机、中止自成一块，
 * 详情页只留"调用它"的几行。
 */
const {
  quizOn,
  quizPhase,
  quizLoading,
  quizQuestions,
  quizPlan,
  quizAttemptId,
  quizNote,
  quizError,
  quizQuiet,
  quizStale,
  quizTask,
  openQuiz,
  answerQuizTask,
  redoQuiz,
  regenerateQuiz,
  closeQuiz
} = useQuiz({
  // 判分时把哪些知识当材料：当前这条
  topicIds: () => (topic.value ? [topic.value.id] : []),
  // 卷名带上标题（考试记录里分得出是哪条的测验）
  titleOf: () => topic.value?.title || '',
  // 锚上的卷过没过期（正文在出卷后改过 ⇒ 只提示不拦截）
  topicUpdatedAt: () => topic.value?.updateTime || '',
  // 聊天那一轮如果还在流，先掐掉：一次只干一件事，
  // 两个请求同时打过去只是白烧 token，用户在测验里也看不到那半截回答
  onBeforeStart: () => cancelAsk()
})

// 任务卡「作答」跳回来（?quizTask=<id>）：开抽屉直接消费那个出题任务
// （直接按 id 消费——那一刻任务列表可能还没拉到，quizTask 计算属性还没就位）
watch(
  () => route.query.quizTask,
  (v) => {
    const id = Number(v)
    if (!Number.isFinite(id) || id <= 0) return
    quizOn.value = true
    answerQuizTask(id)
  },
  { immediate: true }
)

const adoptOpen = ref(false)
const adoptTurn = ref(null)
// 包一层 computed 才是响应式的：getCategories() 只是读 categories.value，
// 直接在模板里调拿到的会是一份普通数组快照，别的壳里增删目录后这里不跟着变。
const categories = computed(() => getCategories())

// 正文里「设为目录链接」那个选择器要的就是这棵树。编辑器在 `components/` 下、
// 不碰 store（见 MilkdownEditorBody 里 categoryTree 那段注释），所以建树在这里做。
// 和 AI 归类那边共用 utils/categoryTree.js 的 toCategoryTree —— 两处各建一遍的话，
// 改排序就得改两处，而两处顺序不一样不会报错。
const categoryTree = computed(() => toCategoryTree(categories.value))

function openAdopt(turn) {
  adoptTurn.value = turn
  adoptOpen.value = true
}

/**
 * 把改动同步到本地这份快照。
 *
 * **本组件的 topic 是 load() 拍的一张快照，不跟 store 走** —— 改完库必须自己补上，
 * 否则界面显示的还是旧值。标题不用补：TopicTitleInput 收的是 topic.title，
 * 自己 watch 着 props 同步草稿。
 *
 * **字段值为 undefined = 这个字段不改**（AI 采纳的补丁只带它改过的字段）。
 * 这条不能靠 `{ ...topic, ...patch }` 一把梭：那样一个 undefined 会把原值抹成空。
 */
function syncTopicSnapshot(patch) {
  const next = { ...topic.value }
  if (patch.title !== undefined) next.title = patch.title
  if (patch.content !== undefined) next.content = patch.content
  if (patch.categoryId !== undefined) next.categoryId = patch.categoryId
  // ⚠️ 标签仍然会**跟着 AI 采纳写进去**（后台逻辑保留），只是界面上不再显示/编辑它。
  // `resolveTags` 那个口子在 store 里，也保留着。
  if (patch.tags) next.tags = resolveTags(patch.tags)
  topic.value = next
}

/**
 * 改库 + 同步本地快照。**保存类动作的唯一出口** ——
 * 原来正文 / 标签 / 类型三处各写一遍"调 updateTopic 再补快照"，同一份规则三份实现。
 *
 * 成功提示不在这儿：**词**统一（准则见 brove-shared §8 —— 通用结果词，不按业务场景定制），
 * 但**弹在哪一步**归各自（采纳说「已采纳」，自动保存什么都不说）。
 */
async function applyPatch(patch) {
  const fresh = await updateTopic(topic.value.id, patch)
  syncTopicSnapshot(patch)
  // 「更新于」要跟着刷新（2026-11-09 用户报「时间不会动态变化，必须刷新页面才会变」）：
  // syncTopicSnapshot 只搬 patch 里的字段，而时间戳是**后端填**的、patch 里根本没有 ——
  // updateTopic 刷完缓存后返回的才是库里的新值，从这儿补进快照。
  if (fresh?.updatedAt) topic.value = { ...topic.value, updatedAt: fresh.updatedAt }
}

// 失败提示不用自己写：axios 拦截器已经统一弹了、还做了同一条消息去重。
async function onAdoptConfirm(patch) {
  await applyPatch(patch)
  ElMessage.success('已采纳')
}

// ============ 选区提问 → 写入正文（拆出到 useTopicInsert.js） ============
const {
  blocksInRange,
  onAskAi,
  insertOpen,
  insertAnswer,
  insertSpot,
  insertMode,
  insertBase,
  insertSaving,
  insertAdjusting,
  insertAdjustTurn,
  insertAdjustError,
  openInsert,
  onInsertConfirm,
  onInsertAdjust,
  resetInsertState
} = useTopicInsert({
  topic,
  editContent,
  editorRef,
  askOpen,
  quizOn,
  closeQuiz,
  submitAsk
})

// ⚠️ **详情页不再拉学习投影**（2026-11-12）：掌握度搬到**目录书**
// （卡片右上角的档位签 + 书页顶部的概览条），这里既不显示掌握度、
// 也没有「标记已学」按钮 ⇒ 这一页不再需要学习投影。
// （随堂测验仍在这一页发起 —— 判分后证据照样落库，掌握度照样涨。）

// 换一条知识就换一个答案：上一个问题的记录留着会张冠李戴。
// 抽屉也一并关掉——开着上一条的问答面板看下一条，最容易认错。
watch(() => topic.value?.id, () => {
  askOpen.value = false
  askInput.value = ''
  messages.value = []
  // 采纳框同理：它是拿上一条的回答去改这一条，不关的话点确认就把 A 的建议写进 B
  adoptOpen.value = false
  // 写入正文的确认框同理：装的是上一条正文锚点和流式重建状态，切换知识点时一并复位。
  resetInsertState()
  // 大纲同理：它是**上一条**正文的标题结构。换一条知识就换一份大纲。
  // 不清的话，新编辑器还没就绪的那一瞬间（重建编辑器要等它渲染完），
  // 页面上挂的还是上一条的大纲 —— 用户看到的是"这条知识没有目录，却有大纲"。
  // 编辑器就绪后会再报一次（报空数组），但那已经是几百毫秒之后了。
  outline.value = []
  activeHeadingId.value = ''
  // 大纲浮层也收起来：开着的话下一秒弹的是**上一条**的目录
  outlineHidden.value = true
})

async function handleDelete() {
  // 空态没有可删的东西（头部图标排常驻，删除入口跟着它常在）
  if (!topic.value) return
  // 确认框 + 删除 + 提示都在 store（和列表页卡片同一个入口），
  // 这里只负责"删完之后回上一页"。
  if (!(await confirmDeleteTopic(topic.value))) return
  goBack()
}

// 详情页不是菜单项，没有"上一页"语义：优先原路返回，直接打开的（刷新/外链）才回列表
function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'AppLearn' })
  }
}

// ==================== 命中浮条（搜索上下文） ====================
//
// ⚠️ **这一整段删了**（2026-11-05 用户：「对于卡片栏来说 这个搜索直接做过滤就行了
// 不用那个命中多少个的框 … 我点进卡片后那个命中栏竟然还在
// 点击上下都不知道在往哪里跳」）：
// 卡片栏（正文那档）是**纯过滤** —— 命中什么只剩什么 ⇒ 没有"第几个命中"这回事 ⇒
// 列表页不画浮条、**详情页也不画**（点进来还挂着那个框，正是他说的"乱成一团"）。
//
// ⚠️ **命中浮条现在只在目录树里**（CategoryTree）：目录那档才有"哪一行命中了"，
// 清单 / 游标 / ↑↓ 都在那一处。

/**
 * 顶栏那条目录路径的数据源：**把这条知识的目录归属报给壳**（store 的 `openTopicCategoryId`）。
 * `topic` 是这一页的局部状态，AppLayout 顶栏拿不到 ⇒ 详情页写、顶栏读（只报目录 id 这一个最小值）。
 * watch 的是**结果**：目录变的路径有两条（载入、AI 采纳改归类），watch 是结果不是某条路径 ⇒ 全覆盖。
 */
watch(() => topic.value?.categoryId, (cid) => {
  openTopicCategoryId.value = cid ?? null
}, { immediate: true })


// 浏览器标签页标题 = 这条知识的标题。
// 「在新标签页打开」之后一次看好几条，标签页上全是 "Corecraft Web" 根本分不出谁是谁。
const DEFAULT_TITLE = document.title

function applyTitle() {
  document.title = topic.value?.title || DEFAULT_TITLE
}

// ⚠️ 三处缺一不可，keep-alive 让它们各管一段：
// ① watch title 而不是写进 load()——改 topic.value.title 的路径有三条（load 首屏、
//    onTitleSave 就地改名、onAdoptConfirm AI 采纳改标题），watch 的是**结果**不是
//    **某条路径**，三条入口自动都覆盖。
// ② onActivated——**这条是「只有第一次进有效」那个 bug 的根因**。AppLayout 的
//    <keep-alive :max="6"> 缓存着实例：从详情页切走再点回来走的是 onActivated，
//    **组件不会重新 setup**，所以 ① 里的 { immediate: true } 不会第二次执行，
//    而 topic.value.title 又没变、watch 压根不触发。标题就永远停在 ③ 还原后的站名上，
//    只有整页刷新（真 setup）才恢复。onActivated 重新贴一次就补上了这个缺口。
// ③ onDeactivated 还原——同一条 keep-alive 链的另一头：切走时组件不销毁，
//    onBeforeUnmount 压根不触发，不还原的话列表页会顶着上一条知识的名字。
//    缓存满了被淘汰时才销毁，而那时 ③ 已经先跑过了，所以一条就够，不用两个都写。
watch(() => topic.value?.title, applyTitle, { immediate: true })
onActivated(applyTitle)
onDeactivated(() => {
  document.title = DEFAULT_TITLE
  // v10 后壳在详情页收滚（`.app-content:has(.detail)` overflow:hidden）、内容恰好一屏高，
  // 壳容器的 scrollTop 恒被浏览器夹到 0 ⇒ 切回列表页天然在页顶。旧版这里还有一段
  // "给列表页归顶"的滚动重置（详情页当时会把壳的滚动往下推），随对齐体系一起删了。
})


// 路由 id 变化时检查是否需要重新 load：
// keep-alive 缓存 TopicDetailView 实例后，同 route name 复用实例，topic.value 可能已经是
// 当前 id 的数据——切回同 id 不重新 fetch（保留本地编辑状态）；切到新 id 走 load。
watch(() => route.params.id, () => {
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) return
  if (topic.value && topic.value.id === id) return  // 同 id：复用缓存，不重新加载
  // ⚠️ **换知识点 ⇒ 正文里的高亮要清**（2026-11-10 用户报「这里还是显示中了18处」）：
  // **编辑器是复用的**（`switchContent` 走 `tr.replaceWith`）⇒ 那一下会触发
  // 插件的 `docChanged` 分支 ⇒ **用旧词在新正文里重算命中** ⇒ 上一条的词
  // 在新正文里又命中了一批（实测「28 处」就这么来的，而 URL 上早就没 `?q=` 了）。
  // ⇒ 这里显式用空词清一次（装饰归零、计数归零），**不依赖壳那边的 watch**——
  // 壳的 watch 有"输入框有焦点就早退"那条分支，靠它清不可靠。
  applyBodySearch('', false)
  // ⚠️ **换知识点 ⇒ 抽屉与随堂状态一并收掉**（2026-11-14 用户报「在其他知识点打开
  // 随堂测验怎么打开的是 Java程序结构总览的」）：抽屉（askOpen）与随堂（quizOn）
  // 都是**组件级状态**，keep-alive 复用实例时路由切换不重置 ⇒ 上一条知识的抽屉
  // 原样出现在新知识点上（回看卷、答题现场全是别人的）。
  askOpen.value = false
  if (quizOn.value) closeQuiz()
  load()
})
onMounted(() => {
  load()
  // 引导问题（配置树上那组）。拉不到就退回本地三条 —— 空态不能什么都不剩。
  loadGuideQuestions()
  // ⚠️ **这里原来还要按搜索词拉一次结构索引**（给命中浮条算"第几个命中"）——
  // 浮条删了（2026-11-05：卡片栏是纯过滤，详情页不画浮条）⇒ 这句也不需要了。
  // 索引本来就由壳启动时拉过一份，详情页自己再拉一遍是白拉。
})

// ── 旧「进场对齐」体系已删（2026-11-09 v10 清理）─────────────────────────────
// scrollParentOf / alignPageTop / scheduleAlign / lastAlignedTop 一整套是给
// 「壳的 .app-content 滚动」时代写的（对齐页头上沿 = 回到页顶）。v10 起壳在详情页
// 收滚（:has(.detail) overflow:hidden），滚动容器只剩正文 .ProseMirror ⇒
// scrollParentOf 恒返回 null、全链无操作；归顶由 resetBodyTop 直接作用于 .ProseMirror。
// 要恢复旧模型先把壳的收滚撤了，再来 git 历史里找这套。

// 离开详情页时清理自动保存定时器，避免后台 setTimeout 在组件销毁后还触发 updateTopic
onBeforeUnmount(() => {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
})
</script>

<style scoped>
/* ⚠️ **这里 2026-11-11 重新封顶了**（`max-width: 1200px` + 居中）。
   这条改了两轮，过程别再走回头路 —— 两次动的**不是同一个东西**：

   **第一轮（#134 / 2026-10-09）删掉 920 封顶**，当时的原话是：
   「**在比例相同的情况下 不应该被是否全屏影响啊 为什么会存在长度限制**」。
   那条诉求**是对的**，它针对的是「卡片卡住不动、两侧空白一路增长、占屏比例一路往下掉」
   —— 定值 max-width 让内容占比随窗口宽度掉，**这确实难看**。
   ⇒ 当时 920 封顶删了，列表页/首页的 1440 上限也一起解了。

   **但删掉封顶 ≠ 行长该无限涨**。不封顶之后详情页在 2560 视口下正文一行 **152 个汉字**
   （实测：.ProseMirror 文字区 2122px ÷ 14px），全屏阅读态更到 **170 字**。
   中文正文舒适区是 30–45 字，70 字已接近可接受上限 ⇒ 151 字一行是**排版事故**
   （眼睛从行尾扫回行首要跨 15 厘米），不是"更充分利用屏幕"。

   ⇒ **当年那句「比例恒定比行长重要」被拿去当"不封顶"的依据，是个偷换命题**：
   当时的取舍是「920 封顶 vs 1440 封顶」——**两个都封顶**，只是宽窄不同；
   现在这个取舍变成了「封顶 vs 不封顶」，行长从 65 字一路走到 152 字，
   是**量变到质变**，根本不是同一个命题。
   （而且那句注释里自己算的「920 ÷ 16 ≈ 60 个汉字」就是错的：正文用的是 14px 不是 16px，
   真实值 920÷14 ≈ 65 字 —— **一个用来论证"必须有上限"的注释，它自己的数就是编的**，
   见 base.css 那段。）

   **2026-11-11 用户重新拍板**（原话：「我之前专门让改的…后来看其它系统其实都是固定大小
   之后就有点动摇了」）：**固定大小是对的**，但要**固定在能读的范围里、再居中**。
   ⇒ 封顶 + 居中，两个问题一起解：
     · **行长恒定**（不再随屏宽涨）；
     · **超宽时左右对称留白**（不是卡在左边 —— 那正是首页 A2 踩过的坑）。
   与首页 `.home`（1160 居中）、列表页 `.learn`（1160 居中）**同一个口径**：
   业务壳的三页版心规则必须一致，不能各写一份。

   **1200px 怎么算出来的**（改宽度时按这条链推，别拍脑袋）：
     .ProseMirror 文字区 1100（≈78 字/行）
     + .ProseMirror 左右 gutter 34×2 = 68
     + .doc > .content 左右内距 16×2 = 32
     = .detail 1200
   78 字是中文长文可接受的偏宽值：再宽眼睛回扫就吃力，再窄表格/代码块放不下。

   ⚠️ **width: 100% 这条不能省**（#855 踩过的坑）：
   `.app-content:has(.detail)` 是 **flex 列**，而 **flex 列里子项的 `margin: 0 auto`
   不是"居中"** —— 交叉轴上的 auto 外边距会让子项**按内容宽度收缩**（而不是像 stretch
   那样拉满），再把余量均分成左右两半 ⇒ **正文越短卡片越窄**（用户当时报的"窄卡"）。
   ⇒ 封顶 + 居中必须配 `width: 100%`，让子项先拉满、再被 max-width 截住。
   这也是壳那边 `.app-content:has(.detail) > :where(*) { margin-left/right: 0 }`
   要改成 `margin: 0 auto` 的原因（见 AppLayout.vue 里的注释）。 */
.detail {
  display: flex;
  flex-direction: column;
  /* v10：壳 :has(.detail) 已改 flex 列 + 收滚，这里吃满剩余高度，让「固定头部 + 正文内滚」成立。
     Dock 退役后那份底部让位（46+22）也一起撤了。 */
  flex: 1;
  min-height: 0;
  /* 2026-11-11：版心封顶 1200 + 居中（避免宽屏单行过长影响阅读）。 */
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

/* 正文列。大纲是**浮层**（fixed，不占版位），这里实际只剩正文卡一个孩子；
   两列的 flex 形态留着（大纲的挂载点还在里面）。
   min-width: 0 是必须的：否则长代码块 / 长表格会把这一列撑破。
   v10：flex:1 + min-height:0 把高度传下去，卡片 = 头部固定 + 正文滚。 */
.doc {
  display: flex;
  align-items: stretch;
  gap: var(--sp-16);
  flex: 1;
  min-height: 0;
}
.doc > .content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}



/* ---------- 标题行 + meta（v10：标题独占一行，类型收 meta 的 chip） ---------- */
.title-row {
  display: flex;
  align-items: center;
  gap: var(--sp-12);
}
.title-row .title {
  flex: 1;
  min-width: 0;
  margin: 0;
}
/* meta 行：左信息右操作，一整排不折行 */
.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-12);
}
.meta-row .meta {
  flex: 1;
  min-width: 0;
}
/* 类型 chip（低频操作收起）：标题行右端 = 卡片右上角。
   标题 flex:1 自然把它顶到最右，gap 由 .title-row 给，这里只要不被挤扁 */
.title-type {
  flex-shrink: 0;
}
/* ---------- 玉签返回钮（A 方案，2026-11-09 用户拍板「B 月弧」换「A 玉签」） ----------
   骑跨卡片左边框、与标题第一行对齐。定位基准 .editor__before（position:relative
   在 kb-editor.css），left 负值 = 白边(--kb-gutter) + 框内距(--sp-12)，
   translateX(-50%) 把圆心钉在边框线上。
   常态一枚 10px 窄玉片，**刻意做虚**（2026-11-09 用户反馈常态太显眼，
   「虚一点或者细一点」⇒ 细 14→10px + 整体透明度 55% + 边框投影减淡，三件一起）：
   只留一线若隐若现的玉色提示「这儿有东西」；悬停卡片 width 动画展开成
   完整白玉圆扣、同时透明度回满，箭头淡入。 */
.back-edge {
  position: absolute;
  left: calc(-1 * (var(--kb-gutter, 34px) + var(--sp-12)));
  top: -4px;
  transform: translateX(-50%);
  z-index: 22;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 10px;
  height: 48px;
  padding: 0;
  border-radius: 24px;
  color: var(--cc-primary);
  background: linear-gradient(135deg, var(--cc-primary-soft), var(--cc-surface) 70%);
  border: 1px solid color-mix(in srgb, var(--cc-primary) 18%, var(--cc-border));
  opacity: 0.55;
  box-shadow:
    0 1px 4px color-mix(in srgb, var(--cc-primary) 7%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
  transition: all 0.24s cubic-bezier(0.25, 0.8, 0.3, 1);
  cursor: pointer;
  font-size: 20px;
}
.back-edge :deep(svg) {
  opacity: 0;
  transition: opacity 0.16s 0.06s;
}
.doc:hover .back-edge,
.back-edge:hover,
.back-edge:focus-visible {
  width: 48px;
  opacity: 1;
  background: linear-gradient(135deg, var(--cc-primary-soft), var(--cc-surface));
  box-shadow:
    0 8px 22px color-mix(in srgb, var(--cc-primary) 26%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
}
.doc:hover .back-edge :deep(svg),
.back-edge:hover :deep(svg),
.back-edge:focus-visible :deep(svg) {
  opacity: 1;
}
/* 触屏没有 hover：常显完整圆扣（同 §4.10(j) 的口径，"看不见就没法点"） */
@media (hover: none) {
  .back-edge {
    width: 48px;
    opacity: 1;
    background: linear-gradient(135deg, var(--cc-primary-soft), var(--cc-surface));
  }
  .back-edge :deep(svg) {
    opacity: 0.85;
  }
}
/* ---------- 头部图标排（v10：AI / 大纲 / 工具栏 / 全屏 / 删除） ---------- */
.hact {
  flex: none;
  display: flex;
  align-items: center;
  gap: 2px;
}
.hact-btn {
  height: 28px;
  min-width: 28px;
  padding: 0 6px;
  border: none;
  border-radius: var(--radius-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: none;
  color: var(--cc-text-3);
  /* 16px = 图标家族标准档（.kb-icon-btn 同档、老稿 ghost-btn 也在此档）：
     原来 --icon-sm 14px 比老稿小一圈、高分屏上显糊（2026-11-13 用户反馈后抬档）。 */
  font-size: var(--icon-md);
  cursor: pointer;
  transition: var(--cc-transition);
}
.hact-btn:hover {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
.hact-btn.is-on {
  background: var(--cc-primary-soft);
  color: var(--cc-primary);
}
.hact-btn.is-danger:hover {
  background: none;
  color: var(--cc-danger, #b34d48);
}
.hact-sep {
  width: 1px;
  height: 14px;
  background: var(--cc-border);
  margin: 0 5px;
  flex: none;
}
/* ---------- 阅读进度线：贴固定头部底缘，贯通整张卡片宽 ---------- */
.read-progress {
  position: absolute;
  left: calc(-1 * (var(--kb-gutter, 34px) + var(--sp-12)));
  right: calc(-1 * (var(--kb-gutter, 34px) + var(--sp-12)));
  bottom: 0;
  height: 2px;
  background: var(--cc-surface-2);
  pointer-events: none;
}
.read-progress > i {
  display: block;
  width: 0;
  height: 100%;
  background: var(--cc-primary);
  transition: width 0.15s;
}
/* meta 行：保存状态 · 更新时间 · 字数。保存状态/更新时间原来在页内顶栏（.bar），
   顶栏撤了它们跟着标题走。颜色 --cc-text-2：更新时间是要读的内容，不是装饰
   ⇒ 不能用 --cc-text-4（对白底 ≈1.9:1）；§4.3 的口径是"层级差由字号与字重承担，
   不再靠更灰"。 */
.meta {
  display: flex;
  align-items: center;
  gap: var(--sp-8);
  margin-top: var(--sp-4);
  /* 标题带 ↔ 正文的间距（标准化 token，不再暴露冗余滑块） */
  margin-bottom: var(--sp-13);
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  font-variant-numeric: tabular-nums;
}
.meta-save {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  /* #2aa876 当年收进 --cc-success 的那条口径不变："已保存"这个语义全站一个绿 */
  color: var(--cc-success);
}
.meta-save__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
/* 保存中：回到中性色 + 点闪 —— 动效说"在进行"，颜色说"还没成" */
.meta-save.is-saving {
  color: var(--cc-text-3);
}
.meta-save.is-saving .meta-save__dot {
  animation: kb-meta-blink 1s infinite;
}
@keyframes kb-meta-blink {
  50% { opacity: 0.2; }
}
.meta-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--cc-text-4);
}

/* ---------- 框内标题（语雀那种"第一行就是标题"） ---------- */
/* 这里只剩"这一处的排版"：字号字重颜色内外边距。
   无边框 / 透明底 / 铺满一行 / 占位色那几样在 TopicTitleInput 里，
   两边共用——同一个属性在组件和调用方各写一遍，就会在同权重下抢样式表
   前后顺序，那是最难查的一类问题。
   .content 的 padding-top 是留给工具栏的（完整模式那排按钮贴到框顶会顶着边框）。
   简洁模式虽然没有工具栏，但那一档不能砍——两个模式共用这一处，砍了完整模式就贴边。
   所以标题上方的空道只能从**标题自己**身上收：padding-top 14px → 4px。
   **只动上边距**：下边距 3px 和 margin-bottom 2px 是标题↔正文之间的间距，不碰——
   这次是让标题贴近框顶，不是把标题和正文挤在一起。 */
.title {
  font-size: var(--text-h);
  font-family: var(--font-display);
  /* 700 = 与正文里的 H1 同档。
     ⚠️ 这一段原先写的理由（"prosemirror.css 没给 h1 任何规则，粗细全是浏览器默认的
     bold=700"）**已经过期**：kb-editor.css 早就显式给了 h1，而且给的是 **800**
     ⇒ 页面级标题 700、正文 H1 800，同一句话并排出现时**下面那句反而更重**。
     本轮把编辑器那边的 h1 也收到 700，两边才是真的一档（注释与代码必须说同一件事）。
     字号仍不跟着 h1 的 2em 走 —— 那是排版尺寸，这里只要粗细和颜色一致
     （颜色继承 .ProseMirror 的 --cc-text-1，本来就是同一个色）。 */
  font-weight: 700;
  line-height: 1.45;
  /* var() 不能直接取负（负号后面直接接 var()，整条声明会被浏览器丢掉 ⇒
     字距根本没生效），负值要包一层 calc */
  letter-spacing: calc(-1 * var(--ls-tight));
  color: var(--cc-text-1);
  padding: var(--sp-4) 0 var(--sp-3);
  margin: 0 0 var(--sp-2);
}

/* ---------- 正文框 ---------- */
/* 和知识点卡片同一张脸：白底 + #eef0f3 细边 + 12px 圆角。
   之前这里是 6px 圆角 + #ebeef5，比壳里所有卡片都锐一档，一眼就是"外来件"。
   顶部 14px：这一行现在归**工具栏**了（标题递进 MilkdownEditor 的 #before-content 插槽），
   工具栏那排按钮贴到框顶会顶着边框，所以留白改由卡片自己带。
   底部 18px 收口。 */
.content {
  background: var(--cc-surface);
  border: 1px solid var(--cc-border-card);
  border-radius: var(--radius-lg);
  /* 顶部内距 = 工具条下沿到正文第一行的呼吸位（编辑器读这个变量，
     见 kb-editor.css 的 .md-toolbar）—— 简洁/完整两种模式下
     正文第一行的位置一致，不会一开工具条就贴着分割线。
     ⚠️ **上下左右全部写死**（2026-10-21 用户定：「顶栏下 / 正文上方 / 正文左右」
     没必要配置）：左右这一档同时也是首页录入框的内距（AppHomeView 的 .quick
     读同一个值）—— 两页共用同一个内距，文字离框边的距离才两边一样。
     底那一段和左右同值、没单独开——要开得先定死框底该是几，那是设计决定不是调参。 */
  --kb-frame-pad-y: var(--sp-12);
  padding: var(--kb-frame-pad-y) var(--sp-12) var(--sp-14);
}
/* 正文的字号 / 行高 / 代码排版不在这里写：.ProseMirror 是 Milkdown 这个第三方函数式
   组件渲染出来的，节点上不带 scoped 的 data-v 属性，从本页 scoped 样式里 :deep 过去
   匹配不可靠（实测没生效）。统一放在 MilkdownEditorBody 的全局样式块里，
   和它的高度规则（min-height 135 / max-height 80vh）写在一起——编辑器长什么样只有那一处说了算。 */

/* ---------- 空 / 加载 ---------- */
.state {
  /* §4.10(h) 原来是裸写 `77px`——阶梯外的数，从何而来无从考证。
     它的意图是"上下各留两格最大间距"，写成 calc 让来源可追（--sp-16 = 40px）。
     ⚠️ 这里不改 --sp-* 阶梯本身：80px 只有这一处用，为它加一支 token 反而多一个要维护的名字。 */
  padding: calc(var(--sp-16) * 2) 0;
  text-align: center;
  color: var(--cc-text-4);
  font-size: var(--text-base);
}
.state p {
  margin: 0;
}
</style>
