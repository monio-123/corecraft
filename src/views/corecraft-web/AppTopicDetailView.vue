<template>
  <div ref="detailRef" class="detail">
    <!-- 顶栏：返回 · 目录面包屑 · 保存状态/时间 · 删除。
         面包屑是"这条知识在知识库里处于什么位置"的唯一线索——原来详情页完全没有，
         进来之后不知道自己站在哪棵树下。归类仍然在列表页拖拽完成，这里只读不改。
         未归类（categoryId 为 null）时什么都不显示：CategoryCrumbs 对空值本来就返回空数组，
         没有位置信息可呈现时，一个"未归类"的标签只是噪音（曾经是个虚线框，视觉上比旁边的
         面包屑还重）。 -->
    <header class="bar">
      <button type="button" class="ghost kb-text-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回
      </button>

      <CategoryCrumbs v-if="topic" :category-id="topic.categoryId" @pick="goCategory" />

      <div class="bar__right">
        <span v-if="saveStatusText" class="save-state" :class="`is-${saveStatus}`">
          <i class="save-state__dot" />{{ saveStatusText }}
        </span>
        <span v-if="topic?.updatedAt" class="bar__time">更新于 {{ topic.updatedAt }}</span>
        <!-- 「简洁 / 完整」放在页面顶栏而不是工具栏里：它是**整个编辑器**的模式开关，
             不是格式动作，工具栏那一排是"格式动作区"，混进去是降一级。
             而且简洁模式下工具栏整行不渲染（原来那个只有切换按钮的空行就是这么来的），
             开关挪到顶栏后两种模式位置不变，来回切它不会跳。 -->
        <!-- 两个开关（工具栏显隐 / 全屏）跟首页共用同一个组件：文案只存在一份，
             差异只有提示挂在哪边和"全屏"叫什么。
             全屏开关放在这里是有理由的：沉浸模式下 AppLayout 的顶栏被收起来了，
             但这一条 .bar 是页面自己的头（返回 / 面包屑 / 保存状态都在里面），
             留着正好当退出路径，Esc 也是一条。 -->
        <ViewToggles
          v-model:show-toolbar="toolbar"
          :full="immersive.on"
          placement="bottom"
          full-label="全屏阅读"
          @toggle-full="immersive.toggle"
        />

        <!-- AI 问答的入口。v-if 用 aiEnabled（想不想用）而不是"配没配全"——
             没开启的人不该看见 AI 的影子；配没配全在 ask() 里拦。 -->
        <el-tooltip v-if="topic && aiEnabled" content="AI 问答" placement="bottom">
          <button type="button" class="kb-icon-btn" aria-label="AI 问答" @click="askOpen = true">
            <KbAiIcon />
          </button>
        </el-tooltip>
        <!-- AI 调整正文：拿一句自然语言指令让 AI 改写这篇正文
             （「新增一节讲 xxx」「在某个知识点里举个例子」）。
             **入口跟「AI 问答」分开**：问答是**零写入**的，这一个是**要改正文**的，
             混在同一个抽屉里两种形态会互相牵扯。 -->
        <el-tooltip v-if="topic && aiEnabled" content="AI 调整正文" placement="bottom">
          <button type="button" class="kb-icon-btn" aria-label="AI 调整正文" @click="editOpen = true">
            <el-icon><MagicStick /></el-icon>
          </button>
        </el-tooltip>
        <!-- 考试：入口在**目录行**上（侧边栏 / 拖拽浮树里的目录「⋯」菜单 → 考这个目录）。
             这里原来有个单条「考试」按钮：一条知识凑不出一份卷子，范围只有目录一种。 -->
        <el-tooltip v-if="topic" content="删除这条知识" placement="bottom">
          <button
            type="button"
            class="kb-icon-btn kb-icon-btn--danger"
            aria-label="删除"
            @click="handleDelete"
          >
            <el-icon><Delete /></el-icon>
          </button>
        </el-tooltip>
      </div>
    </header>

    <div v-if="loading" class="state">加载中…</div>

    <template v-else-if="topic">
      <!-- 标题现在在下面 .content 框内第一行（语雀那种），这里不再单独占一行 -->
      <!-- 标签：pill + 一个入口。
           收起态：无标签时是「添加标签」，有标签时收成一个 + 号（同一个动作，只是没地方写
           那四个字了）。点开 → 变成一个**有边框的输入框** + 右侧向下箭头：
             框里打字回车 = 建新标签；点箭头 = 挑一个已有的。两个动作分开，
             不做成"一个框既搜又建"——短框塞不下「新建「xxx」」那一行。
           **箭头只在展开态出现**：收起态它没有可点的东西，一直挂着只是个装饰。
           入口常驻（不管这条知识有没有标签），所以有标签之后照样能建新标签
           ——原来的入口文字挂在 editTags.length === 0 上，一有标签就消失。
           文案用「添加」而不是「新建」：它挂的是**这条知识**的标签，而"新建"
           会被读成"要造一个全局的新标签"——实际两件事都在（打回车建全局标签、
           点箭头挂已有的），「添加」只描述"往这条知识上加"这层意思，不误导。 -->
      <div class="tags">
        <!-- 类型在标签左边：它是"这条知识是什么性质"（单选，分类意味），
             标签是"它涉及什么"（多选，中性信息）。形态也不同——类型是主色底，
             标签是灰底 chip，同一行里能一眼分开。 -->
        <TopicTypeSelect v-model="editTypeId" @change="saveType" />
        <span v-for="t in editTags" :key="t" class="kb-chip">
          <span class="kb-chip__text">{{ t }}</span>
          <button
            type="button"
            class="kb-chip__x"
            :aria-label="`移除标签 ${t}`"
            @click="removeTag(t)"
          ><el-icon><Close /></el-icon></button>
        </span>

        <button
          v-if="!tagInputOpen"
          type="button"
          class="tags__add"
          aria-label="添加标签"
          @click="openTagInput"
        >
          <template v-if="!editTags.length">添加标签</template>
          <el-icon v-else><Plus /></el-icon>
        </button>

        <div v-else class="tags__picker">
          <el-input
            ref="tagInputRef"
            v-model="tagQuery"
            size="small"
            placeholder="标签名"
            @keyup.enter="commitTypedTag"
            @keydown.esc="closeTagInput"
            @blur="closeTagInput"
          />
          <button
            type="button"
            class="tags__caret"
            :class="{ 'is-open': tagListOpen }"
            :aria-label="tagListOpen ? '收起已有标签' : '选择已有标签'"
            @mousedown.prevent
            @click="tagListOpen = !tagListOpen"
          ><el-icon><ArrowDown /></el-icon></button>

          <!-- @mousedown.prevent 是必需的，不是保险：不阻止默认行为的话 mousedown 会把焦点
               从输入框抢走 → blur → 列表当场收起，列表项永远点不中。阻止之后焦点留在
               输入框，列表能正常点。 -->
          <div v-if="tagListOpen" class="tags__list">
            <button
              v-for="t in filteredTags"
              :key="t.name"
              type="button"
              class="tags__list-item"
              @mousedown.prevent
              @click="pickExisting(t)"
            >
              <span class="tags__list-name">{{ t.name }}</span>
              <span class="tags__list-count">{{ t.count }}</span>
            </button>
            <p v-if="!filteredTags.length" class="tags__list-empty">没有可加的</p>
          </div>
        </div>
      </div>
      <!-- 内容：Milkdown WYSIWYG（ProseMirror 内核 + 可选工具栏，封装在 MilkdownEditor 内）
           key=topic.id：切 topic 时强制重建编辑器。
           keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
           不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
           不会更新到 ProseMirror 视图。 -->
      <!-- 内容框：白底卡片。**标题是框内第一行**（语雀那种"第一行就是标题"），
           不再是框外一个孤立的表单字段——框外那行 23px 粗体压在正文上方，
           整页看起来像一张详情表单，而不是一份文档。
           title 字段本身没变，只是换了个渲染位置：卡片、搜索、删除确认、首页
           照旧读 kp_topic.title，零后端改动。 -->
      <div class="doc">
      <div class="content">
        <!-- 内容：Milkdown WYSIWYG（ProseMirror 内核 + 可选工具栏，封装在 MilkdownEditor 内）
             key=topic.id：切 topic 时强制重建编辑器。
             keep-alive 同 route 名复用组件实例，MilkdownEditorBody 的 useEditor 只在 setup 时跑一次；
             不重建 → 旧 editor 仍渲染旧 doc，新 markdown 传进来也只在 MilkdownEditor.vue 的 markdown ref 里，
             不会更新到 ProseMirror 视图。

             标题走 #before-content 插槽递进去，于是框内顺序是
             「工具栏 → 标题 → 正文」——工具栏在框顶、标题是文档第一行，和语雀一致。
             不用插槽就得把工具栏搬到标题上面，但工具栏必须在 MilkdownProvider 内部
             才能拿到编辑器 ctx，搬出去就废了；反过来把标题递进来，顺序自然就对了。 -->
        <MilkdownEditor
          ref="editorRef"
          :key="topic.id"
          v-model="editContent"
          :show-toolbar="toolbar"
          :category-tree="categoryTree"
          @ready="scheduleAlign"
          @outline="onOutline"
        >
          <!-- 标题：无边框 input，点哪都能输，失焦即存。placeholder 是「请输入标题」
               不是「未命名」——标题本来就常是空的（createTopic 传 title: ''），
               所以这里不是"兜底占位"，而是一个**真的可以立刻点进去写**的标题位。
               上一版给它外面套了一层「＋ 起个名字」按钮再点一次才出输入框，
               是多出来的一跳，没必要。列表卡片那边也共用 TopicTitleInput。 -->
          <template #before-content>
            <TopicTitleInput
              :title="topic.title"
              class="title"
              @save="onTitleSave"
            />
          </template>
        </MilkdownEditor>
      </div>

      <!-- 右侧大纲：正文里的标题层级，点一条跳到对应标题。
           **只在正文有标题时才有**——没有标题的知识点（大多数）右边挂一个空栏
           是纯噪音。语雀那套就是这样。实现见 TopicOutline.vue。
           大纲里的折叠**只管大纲自己**（折掉这一支下级条目），不碰正文 ——
           正文那节折不折，用户自己在正文标题的箭头上点。 -->
      <TopicOutline
        :items="outline"
        :active-id="activeHeadingId"
        @navigate="goHeading"
      />
      </div>


      </template>

    <!-- 打开的这条已经没了。**只说事实，不解释、不给"回到列表"按钮**——
         顶栏本来就有一个「返回」（同一个 goBack），这里再摆一个就是重复。 -->
    <div v-else class="state">
      <p>这条知识不存在</p>
    </div>

    <!-- 对这条知识提问。**零写入**：答完一个字都不回写 kp_topic。
         放右侧抽屉而不是正文下方：核心场景是「对着正文提问」，常驻在正文下面时
         笔记一长就被推到很远，抽屉则正文还在视野里、对话自己滚。
         覆盖式（modal）不压缩正文宽度，正文不会跟着重排跳动。 -->
    <!-- :size 绑 JS 值而不是写死 420px —— 见 askWidth 的注释。EP 的 size 会拼 px
         后缀，所以它读不到 CSS 变量，只能吃一个真数字。 -->
    <el-drawer v-model="askOpen" direction="rtl" :size="`${askW}px`" :with-header="false" modal>
      <div class="ask">
        <!-- 拖动把手。**hover 才现形**：常驻一条竖线既占地方又说不清是干什么的，
             换成箭头让形状自己说话——往左 = 往宽里拖。
             左边贴抽屉边是直的、只圆右边，像一个嵌在边缘的拉手而不是浮在上面的一块。
             平时 opacity 0 但仍吃指针事件（否则 hover 永远触发不了），
             所以对话区左边距要给够 20px，见 .ask__body -->
        <div
          class="ask__grip"
          role="separator"
          aria-orientation="vertical"
          aria-label="拖动调整问答框宽度，双击恢复默认"
          @pointerdown="onGripDown"
          @dblclick="askWidth = ASK_W.def"
        >
          <el-icon class="ask__grip__icon"><ArrowLeft /></el-icon>
        </div>
        <div class="kb-drawer__head">
          <span class="kb-drawer__title">AI 问答</span>
          <button type="button" class="kb-icon-btn" aria-label="关闭" @click="askOpen = false">
            <el-icon><Close /></el-icon>
          </button>
        </div>

        <div class="ask__body" ref="askBodyRef">
          <!-- 引导问题常驻在对话上方，**不是空态才出现**。它们是一键动作（一按就问），
               不是"没话说时才给的选择题"——挂在 v-else-if 上意味着问一轮就没了，
               想再用只能刷新页面重开。跟着对话一起滚，不占固定高度：
               对话短的时候一直看得见，长了往上滚一截就有。

               问题来自配置树上的「引导问题」那组，改问题在系统配置里改，不用发版。
               拉不到就什么都不显示（后端返回空列表），不该为一个可有可无的列表报错。 -->
          <div v-if="guideQuestions.length" class="ask__guide">
            <button
              v-for="q in guideQuestions"
              :key="q.key || q.label"
              type="button"
              class="ask__chip"
              @click="askNow(q)"
            >
              {{ q.label }}
            </button>
          </div>

          <!-- 一轮问答 = 一个 .turn：提问气泡 + 思考过程 + 回答 + 复制。
               组内紧、组间松，一眼能看出哪句答哪句。 -->
          <template v-if="messages.length">
            <div v-for="(m, i) in messages" :key="i" class="turn">
              <div class="bubble is-user">{{ m.question }}</div>

              <!-- 提交给模型的东西。默认折着——大部分时候没人想看，但 AI 答得不对时
                   这是唯一能核对"它到底看见了什么"的地方（附的目录清单对不对、
                   任务描述是不是被人改歪了） -->
              <details v-if="m.prompt" class="sent">
                <summary>发送给 AI 的内容</summary>
                <pre class="sent__body">{{ m.prompt }}</pre>
              </details>

              <!-- 思考过程。**只要这个框存在，它就是展开的**——`open` 是静态属性，
                   不是绑定。之前试过 `:open="m.thinking"`（正文一到就收起），踩过一次：
                   正文往往比思考短得多，思考刚展开就被合上，模型明明给了 reasoning_content、
                   DOM 里也确实有内容，用户看到的却是"没有思考过程"——框一直在，内容也一直在，
                   只有开合骗人。绑定 `:open="m.thinking || !!m.reasoning"` 也一样是废写法：
                   那个条件和 v-if 完全相同，等于恒真，直接写成静态 open 就行。
                   要看的人自己点 summary 合；不想看的人一开始就没点开过它。

                   ⚠️ v-if 也不能写成 `m.reasoning` —— 第一帧 reasoning 到达之前它是空串，
                   那样框根本不在 DOM 里，流式过程一个字都看不到。所以条件是 `thinking || reasoning`：
                   流式期间（thinking=true）框就在、内容往里长。
                   非思考模型（deepseek-flash 这类）结束时 reasoning 为空 → 框自动消失。 -->
              <details v-if="m.thinking || m.reasoning" class="think" open>
                <summary>思考过程</summary>
                <div v-if="m.reasoning" class="think__body">{{ m.reasoning }}</div>
                <div v-else class="think__waiting">正在思考…</div>
              </details>

              <!-- 流式活着的信号。**必须独立于上面的思考框**：非思考模型正文一到
                   那个框就整个没了，计数器跟着一起没——恰好在最需要它的时候消失。
                   数字往上爬 = 真在流；从 0 跳到全长 = 上游整段缓冲住了才给我们。 -->
              <div v-if="m.received && !m.done" class="think__count">
                正在生成… 已接收 {{ m.received }} 字
              </div>

              <div v-if="m.answer" class="bubble is-ai" :class="{ 'is-error': m.error }">{{ m.answer }}</div>
              <div class="ask__ops">
                <button
                  v-if="m.answer && !m.error"
                  type="button"
                  class="kb-text-btn"
                  @click="copyText(m.answer)"
                >
                  复制
                </button>
                <!-- 采纳：只挂在引导问题那一轮（adoptFieldOf 对自己敲的问题返回 null）。
                     自己敲的答案没有"该写进哪个字段"的语义，要把它写进正文是另一件事 -->
                <button
                  v-if="m.answer && !m.error && adoptFieldOf(m.questionKey)"
                  type="button"
                  class="kb-text-btn"
                  @click="openAdopt(m)"
                >
                  用这个
                </button>
              </div>
            </div>
          </template>
        </div>

        <!-- 底部一个胶囊：左边是输入框，右下角依次是「用哪个 API」和发送/停止。
             发送和停止是**同一个动作的两种状态**（不在流 = 问，在流 = 中断），
             所以合成一个圆钮：两个并排的按钮会把"能按哪个"这个问题推给用户。 -->
        <div class="ask__foot">
          <div class="ask__composer">
            <!-- ⚠️ 必须写 submitAsk()，不能写 submitAsk。裸传函数名时 Vue 会把 KeyboardEvent
                 当第一个实参送进 submitAsk(questionKey = '') —— questionKey 就成了一个事件对象，
                 JSON.stringify 出来是 {}，后端 Jackson 直接报
                 "Cannot deserialize java.lang.String from Object value"，请求 46ms 就被拒。
                 而引导问题那条路是 askNow(q) → submitAsk(q.key)，传的是真字符串，所以同一个页面
                 上「点引导问题能出答案、手敲回车就报错」。看起来像两回事，其实是同一个函数。 -->
            <!-- 无 placeholder：用户明确要的就是一个空白输入框。上面那四个引导问题
                 常驻在对话顶部，已经是这块区域的主要入口，输入框只是个补充 -->
            <input
              v-model="askInput"
              class="ask__input"
              :disabled="asking"
              @keyup.enter="submitAsk()"
            />

            <!-- 用哪个 API 调。存 localStorage（见 useAi），下拉里只列配全了的能用的 -->
            <el-dropdown trigger="click" @command="useEndpoint">
              <span class="ask__model">
                {{ currentEndpoint ? currentEndpoint.name : '选 API' }}
                <i class="ask__caret" />
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="e in aiEndpoints"
                    :key="e.id"
                    :command="e.id"
                    :disabled="!e.ready"
                  >
                    <!-- 只显示名字。原来是「名字 · 模型」两段，而名字本来就是用户自己起的、
                         通常就照着模型名写的（"deepseek-flash · deepseek-flash"），
                         同一句话在一行里出现两遍。模型名在设置页那行副标题里已经有了 -->
                    {{ e.name }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <button
              type="button"
              class="ask__send"
              :class="{ 'is-stop': asking }"
              :disabled="!asking && !canAsk"
              :aria-label="asking ? '停止生成' : '提问'"
              @click="asking ? cancelAsk() : submitAsk()"
            >
              <el-icon>
                <VideoPause v-if="asking" />
                <Top v-else />
              </el-icon>
            </button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 采纳预览。放在抽屉外面但还在这一层的 template 里：它针对的是抽屉里那一轮问答，
         关掉抽屉不该把一个已经打开的确认框一起带走 -->
    <AiAdoptDialog
      v-model="adoptOpen"
      :turn="adoptTurn"
      :categories="categories"
      :topic="topic"
      @confirm="onAdoptConfirm"
    />

    <!-- AI 调整正文。同样放在抽屉外面：它针对的是**这一条**的正文，
         关掉抽屉不该把一个已经打开的编辑面板一起带走。
         **它自己不写库**：只把新正文交出来，写回编辑器之后由详情页那条
         1.5s 防抖的自动保存落库。 -->
    <AiEditDrawer
      v-model="editOpen"
      :content="editContent"
      :doc-of="docOfBody"
      @apply="applyEdited"
    />
  </div>
</template>

<script setup>
// 让 component.name = 'AppTopicDetail' 对齐 route.name，配合 AppLayout 的 keep-alive 缓存实例。
defineOptions({ name: 'AppTopicDetail' })
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, onActivated, onDeactivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowLeft, Close, Delete, MagicStick, Plus, Top, VideoPause } from '@element-plus/icons-vue'
import KbAiIcon from '../../components/KbAiIcon.vue'
import MilkdownEditor from '../../components/MilkdownEditor.vue'
import ViewToggles from '../../components/corecraft-web/ViewToggles.vue'
import CategoryCrumbs from './CategoryCrumbs.vue'
import TopicOutline from './TopicOutline.vue'
import TopicTitleInput from './TopicTitleInput.vue'
import AiAdoptDialog from './AiAdoptDialog.vue'
import AiEditDrawer from './AiEditDrawer.vue'
import { useImmersive } from './useImmersive'
import TopicTypeSelect from './TopicTypeSelect.vue'
import {
  aiEnabled, ask as askAi, useEndpoint, currentEndpoint, aiEndpoints,
  aiQuestions, ensureAiQuestionsLoaded
} from './useAi'
import { adoptFieldOf } from './aiAdopt'
import { getPrefSpec, getUiPrefs, saveUiPrefs } from '../../utils/uiPrefs'
import { copyText } from '../../utils/clipboard'
import { toCategoryTree } from '../../utils/categoryTree'
import { categoryLink } from '../../utils/categoryNav'
import {
  fetchTopic, updateTopic, confirmDeleteTopic, getAllTags, resolveTags,
  ensureAllTagsLoaded, getCategories,
  resolveTopicTypeId
} from './useCorecraftWebStore'

const route = useRoute()
const router = useRouter()

const topic = ref(null)
const loading = ref(true)
// 根节点只为了拿它的父元素（= 壳的 .app-content，那才是滚动容器）。**用 parentElement
// 而不是 querySelector('.app-content')**：这条依赖的是 DOM 结构（RouterView 把视图直接
// 挂在 main 下），不是某个类名——壳改类名不会让这里静默失灵，而是结构变了本来就该跟着改。
const detailRef = ref(null)
// 编辑器实例。大纲点击跳转要调它的方法（编辑器在 MilkdownProvider 里面，
// 外层拿不到它的 ctx，只能由 MilkdownEditor 暴露出来的方法代劳）。
const editorRef = ref(null)
// 右侧大纲：正文里的标题层级。**由编辑器报上来**（@outline），
// 页面自己不解析 markdown —— 解析一份和编辑器里那份是两份真相，
// 大纲和正文对不上的时候，用户看到的是"大纲指错了地方"。
const outline = ref([])
// 当前所在章节。点大纲条目时高亮它。
const activeHeadingId = ref('')

/** 编辑器报上来大纲 */
function onOutline(list) {
  outline.value = list
  // 换知识点后要清掉高亮：旧 id 在新正文里可能根本不存在
  if (activeHeadingId.value && !list.some((h) => h.id === activeHeadingId.value)) {
    activeHeadingId.value = ''
  }
}

/** 点大纲条目 → 滚到那个标题 */
function goHeading(id) {
  activeHeadingId.value = id
  editorRef.value?.scrollToHeading(id)
}

// 全屏阅读：收起源的壳，只留这一页。和首页全屏录入、AppLayout 共用同一个判据。
const immersive = useImmersive()
// 编辑器工具栏显不显示。状态在**这一层**而不是 MilkdownEditor 内部，
// 因为"简洁/完整"的开关按钮画在这一层的顶栏里（.bar），
// 按钮和状态必须是同一份，否则切了按钮不动。
const toolbar = ref(false)
const editContent = ref('')
const editTags = ref([])
// 标签输入分两层：tagInputOpen 控制"入口 ⇄ 输入框"这次切换，
// tagListOpen 控制"箭头下拉"这一次。箭头只在 tagInputOpen 时才存在。
// 原来是两个入口（inline 输入框 + "选择已有标签" popover），功能重叠；
// 而且 inline 输入框挂在 `editTags.length === 0` 上，有标签之后就再也建不了新标签。
// 类型：本地草稿，saveType 落库。可能是悬空的旧 id，落库前用 resolveTopicTypeId 收口
const editTypeId = ref(null)
const tagInputOpen = ref(false)
const tagQuery = ref('')
const tagListOpen = ref(false)
const tagInputRef = ref(null)

// 内容自动保存状态机：'idle'（不显示）| 'saving'（debounce 中 / 调用中）| 'saved'（保存成功，2s 后回 idle）
const saveStatus = ref('idle')
let saveTimer = null  // debounce 计时器
let statusTimer = null  // 'saved' 状态自动回 'idle' 计时器

const saveStatusText = computed(() => {
  if (saveStatus.value === 'saving') return '保存中'
  if (saveStatus.value === 'saved') return '已保存'
  return ''
})

const existingTags = computed(() => getAllTags())

// 还没挂在这条知识上的标签
const availableTags = computed(() =>
  existingTags.value
    .filter((t) => !editTags.value.includes(t.name))
)

// 箭头展开的列表：输入框里有字就按字收窄，没字就按频次倒序扫一眼。
const filteredTags = computed(() => {
  const q = tagQuery.value.trim().toLowerCase()
  if (!q) return [...availableTags.value].sort((a, b) => b.count - a.count).slice(0, 50)
  return availableTags.value.filter((t) => t.name.toLowerCase().includes(q))
})

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
  try {
    const id = Number(route.params.id)
    // URL 里的 id 不合法（手输 /topic/NaN 等）→ 直接走"知识点不存在"分支，不发请求避免后端
    // "Failed to convert 'NaN' to Long"。修传参方即可，fetchTopic / 后端不动。
    if (!Number.isFinite(id) || id <= 0) {
      topic.value = null
      return
    }
    // 只调详情接口；已有标签列表懒加载（用户点开箭头下拉时才调 ensureAllTagsLoaded）
    const t = await fetchTopic(id)
    if (!t) {
      topic.value = null
      return
    }
    topic.value = t
    editContent.value = t.content || ''
    editTags.value = (t.tags || []).map((tg) => tg.name)
    editTypeId.value = t.typeId ?? null
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
  if (!topic.value) return
  if ((topic.value.content || '') === newVal) return
  if (saveTimer) clearTimeout(saveTimer)
  saveStatus.value = 'saving'
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveContent()
  }, 1500)
})

async function saveTags() {
  if (!topic.value) return
  // 防御性去重：editTags 理论上 addTag 已 dedup，但避免极端路径把重复字符串传给后端
  const tags = resolveTags([...new Set(editTags.value)])
  await applyPatch({ tags })
  ElMessage.success('已更新')
}

// 类型切换后立即落库。typeId 可能悬空（字典项被软删），先收口成清单里真实存在的 id
async function saveType(id) {
  if (!topic.value) return
  await applyPatch({ typeId: resolveTopicTypeId(id) })
}

function addTag(name) {
  const value = name.trim()
  if (!value) return
  if (editTags.value.includes(value)) return  // dedup：避免重复
  editTags.value = [...editTags.value, value]
  saveTags()
}

// 展开输入框 + 聚焦。ElInput 实例 expose 了 focus()（EP 官方 API），
// 不用再绕 $el.querySelector('input')。
async function openTagInput() {
  tagInputOpen.value = true
  await nextTick()
  tagInputRef.value?.focus?.()
}

// 收起：输入框和下拉一起收，回到入口
function closeTagInput() {
  tagInputOpen.value = false
  tagListOpen.value = false
  tagQuery.value = ''
}

// 输入框回车：精确命中已有标签就选它（不然会造出一个同名重复，散在各处），
// 否则当新标签建。加完收起，回到入口。
function commitTypedTag() {
  const v = tagQuery.value.trim()
  if (!v) { closeTagInput(); return }
  const hit = availableTags.value.find((t) => t.name === v)
  addTag(hit ? hit.name : v)
  closeTagInput()
}

function pickExisting(t) {
  addTag(t.name)
  closeTagInput()
}

function removeTag(t) {
  editTags.value = editTags.value.filter((x) => x !== t)
  saveTags()
}

// ============ 对这条知识提问（AI，零写入） ============
// 问是右对齐的浅色小气泡（一眼分清哪句是我问的），答是左对齐的无底色阅读区——
// 刻意没做成聊天气泡窗：它不是聊天，是附在这条知识旁边的问答区，
// 回答里常带代码和路径，要能按一整段的宽度去读。
const ASK_GUIDE_FALLBACK = ['这条知识讲的是什么？', '关键点有哪些？', '我应该怎么用它？']

const askOpen = ref(false)
const askInput = ref('')

// 抽屉宽度。**面板滑块、拖动把手、双击归位三处必须同源**，所以上下限和默认值
// 都从配置树上那一项读，不在本文件另写一份 420。
const ASK_KEY = 'ui.detail.detailAskW'
const ASK_W = computed(() => getPrefSpec(ASK_KEY))
const askWidth = ref(getUiPrefs()[ASK_KEY] ?? ASK_W.value?.defaultValue)

/** 上限跟着视口走：抽屉盖过七成，正文就没法看了——那还不如关掉抽屉 */
function clampAskW(w) {
  // 规格取不到（配置树还没到）就不夹：宁可宽一点，也别因为 min/max 是 undefined 变成 NaN
  if (!ASK_W.value) return Math.round(w)
  const cap = Math.min(ASK_W.value.max, Math.round(window.innerWidth * 0.7))
  return Math.min(Math.max(Math.round(w), ASK_W.value.min), cap)
}

/**
 * 真正给 `:size` 的值。
 *
 * **必须在这里 clamp，不能只在拖动时 clamp**：上限跟着视口走，用户把抽屉拖到
 * 883px 视口下的 618px，换到 706px 的窄视口就该显示 494。存的值不变、显示的值
 * 要变——那 `askWidth` 就不能直接绑到 `:size`，否则窄视口下画出来的是 618
 * 被容器裁掉的状态；更要命的是拖动基准会取到那个"看不见的 618"，拖一下就跳。
 */
const askW = computed(() => clampAskW(askWidth.value))

function onGripDown(e) {
  // 抓住把手本身，而不是往 window 上挂监听：把手只有 22px 宽，
  // 指针一移出去事件就落到别的元素身上，拖到一半会断。
  const el = e.currentTarget
  const startX = e.clientX
  // 基准取**显示值**不是存档值：所见才能所拖
  const startW = askW.value
  el.setPointerCapture(e.pointerId)
  // 往左拖 = 变宽（抽屉贴右边），所以位移取反
  const move = (ev) => {
    const w = clampAskW(startW - (ev.clientX - startX))
    // **拖动过程中就落盘**，而不是等 pointerup：指针在窗口外松开、或被系统
    // 打断时那个事件根本不来，宽度就只停在内存里，刷新一下白拖。
    // 阈值 8px 是为了别每帧都 setItem——那是同步 IO，会卡主线程。
    if (Math.abs(w - askWidth.value) < 8) return
    askWidth.value = w
    saveUiPrefs({ [ASK_KEY]: w })
  }
  // pointercancel 也得收尾：指针被系统抢走时不会补一个 pointerup
  const up = () => {
    el.removeEventListener('pointermove', move)
    el.removeEventListener('pointerup', up)
    el.removeEventListener('pointercancel', up)
    saveUiPrefs({ [ASK_KEY]: askWidth.value }) // 最后一次精确值
  }
  el.addEventListener('pointermove', move)
  el.addEventListener('pointerup', up)
  el.addEventListener('pointercancel', up)
}
const asking = ref(false)
const messages = ref([])
const askBodyRef = ref(null)
let askAbort = null

const canAsk = computed(() => !asking.value && askInput.value.trim().length > 0)

/**
 * 流式期间跟着往下滚。
 *
 * 不跟着滚的话，长回答是**在视野下方一截一截长出来的**——用户盯着屏幕只看到"正在思考…"，
 * 内容全在折叠线以下，看起来就像卡住了。
 *
 * 但不能无条件滚：用户往回翻想重读上一轮时，每来一帧就把他拽回底部，比不滚还烦。
 * 所以只在"本来就贴着底部"时跟随——想读的人已经自己滚上去了，那就不打扰。
 * 阈值给 80px：流式时 DOM 每帧都在长，差值天然大于 0，卡太死会一帧都不跟。
 */
function followStream() {
  const el = askBodyRef.value
  if (!el) return
  if (el.scrollHeight - el.scrollTop - el.clientHeight > 80) return
  el.scrollTop = el.scrollHeight
}

// 挂在 received 上而不是 answer 上：思考型模型的正文可能短到只有几个字，
// 但思考过程照样在逐帧长，只盯正文会漏掉"思考还在流"的那一大段。
//
// ⚠️ flush: 'post' 是必须的：默认的 pre-flush 在 DOM 更新**之前**跑，那时
// scrollHeight 还是上一帧的高度，滚了个寂寞。加了 post 才量得到新内容。
watch(() => messages.value[messages.value.length - 1]?.received, followStream, { flush: 'post' })

/**
 * 引导问题优先用配置树上那组的（带 key，后端据此附目录/标签清单、套对应的描述规则）；
 * 配置树拉不到时退回这三条纯读取的本地问题——它们不需要任何附加料，缺了配置树也能用。
 * 配置树不是锦上添花，不该因为它没拉到就让空态什么都不剩。
 */
const guideQuestions = computed(() =>
  aiQuestions.value.length ? aiQuestions.value : ASK_GUIDE_FALLBACK.map((label) => ({ key: '', label }))
)

onMounted(() => {
  ensureAiQuestionsLoaded()
})

function askNow(q) {
  // 点一下直接问，不先填进输入框：它是"一键动作"。填进去的话用户一改字，
  // questionKey 和实际问的内容就对不上了（key 还在、描述还是那条，用户的原话被丢掉）
  askInput.value = q.label
  submitAsk(q.key)
}

// 「复制」直接用 utils/clipboard 的统一出口，这里不再包一层。
// 原来包出来只为了挂一句 ElMessage.success('已复制')——而反馈现在归 copyText 管，
// 链接工具条那个复制按钮也是同一个出口，两边行为一致了。

function submitAsk(questionKey = '') {
  if (!canAsk.value) return
  const question = askInput.value.trim()
  askInput.value = ''
  // **一轮 = 一条消息**（question + prompt + reasoning + answer + 复制），不是几条平铺的消息。
  // 平铺的话提问气泡和纯文字回答形态差太远、间距又一样，看不出哪句答哪句
  //
  // ⚠️ 必须是 reactive(...)，不能先造裸对象再 push 进 ref([])。
  // push 进 ref 的是**裸对象**，而模板 v-for 读到的是它的响应式代理；回调里往裸对象写
  // （turn.answer += delta）Vue 收不到任何通知 → 渲染不重跑 → 流式期间界面一个字都不动，
  // 全部内容堆到 .finally 改 asking 那一次渲染里一次性蹦出来。
  // 用户看到的是"点完卡住不动，然后唰一下全出"——和"后端把整段缓冲住了才给我们"
  // **完全同一种现象**，但根因在前端，跟 SSE 一点关系没有（这个坑真的踩过一轮，
  // 一度以为是 Tomcat 缓冲，方向整个反了）。
  // 历史：/chat/completions 是**无状态**协议，模型这一轮知道的全部就是这一次请求里的
  // messages 数组——之前问过的不一起发过去，它就真的不记得（曾经就是这样：同一个抽屉里
  // 接着追问，AI 却答"我没有上下文记忆"）。后端不落库、也拿不到，所以只能在这儿交上去。
  //
  // 只带**已经问完**的（done）：还在流的那一轮还没有答案，发过去等于让模型接着自己续写。
  // 出错的也不带——那轮的 answer 是报错文案，不是模型说过的话。
  const history = messages.value
    .filter((m) => m.done && !m.error && m.answer)
    .map((m) => ({ question: m.question, answer: m.answer }))

  const turn = reactive({
    question,
    prompt: '',
    reasoning: '',
    answer: '',
    // 本轮已收到的总字数（思考 + 正文都算）。**不是调试输出**：流式到底通没通，
    // 从界面上一眼就能看出来——数字一路往上爬 = 真的在流；从 0 直接跳到全长 =
    // 上游把整段缓冲住了才给我们。没有它，"卡住了"这件事就只能靠猜。
    //
    // ⚠️ 必须把两个通道都算进来：只数 reasoning 的话，非思考模型（deepseek-flash 这类）
    // 一次都不会触发 onReasoning，计数器恒为 0 → 永远不渲染，恰好在最需要它的模型上是瞎的。
    received: 0,
    // 本轮收完了没（决定字数计数器还显不显示）。不能用全局 asking 顶替：
    // 它是抽屉级的，多轮之后会把早已结束的那一轮也点亮
    done: false,
    // 思考进行中 = 展开。正文一开始到达就转 false。**只增不回头**：
    // 有内容之后框就不再自动合上了，看原因见模板里 <details class="think"> 的注释
    thinking: true,
    error: false,
    questionKey
  })
  messages.value.push(turn)

  asking.value = true
  askAbort = new AbortController()
  askAi(question, {
    topicId: topic.value.id,
    questionKey,
    history,
    signal: askAbort.signal,
    onPrompt: (text) => { turn.prompt = text },
    onDelta: (delta) => { turn.answer += delta; turn.received += delta.length; turn.thinking = false },
    onReasoning: (text) => { turn.reasoning += text; turn.received += text.length }
  })
    .catch((e) => {
      // 用户点"停止"不是错误，别把 AbortError 当失败显示出来
      if (e?.name === 'AbortError') {
        if (!turn.answer) messages.value.pop()
        return
      }
      turn.error = true
      turn.answer = e?.message || 'AI 调用失败'
    })
    .finally(() => {
      turn.thinking = false
      turn.done = true
      asking.value = false
      askAbort = null
    })
}

function cancelAsk() {
  askAbort?.abort()
}

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
  if (patch.typeId !== undefined) { next.typeId = patch.typeId; editTypeId.value = patch.typeId }
  if (patch.tags) { next.tags = resolveTags(patch.tags); editTags.value = patch.tags }
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
  await updateTopic(topic.value.id, patch)
  syncTopicSnapshot(patch)
}

// 失败提示不用自己写：axios 拦截器已经统一弹了、还做了同一条消息去重。
async function onAdoptConfirm(patch) {
  await applyPatch(patch)
  ElMessage.success('已采纳')
}

// ============ AI 调整正文（改写整篇） ============
// 抽屉（AiEditDrawer）自己管发请求和左右对比；这里只管两件事：开它、把结果写进编辑器。
//
// **写库不在这一步**：写回编辑器后，编辑器照常上报 markdownUpdated
// → v-model 变了 → 上面那条 1.5s 防抖的自动保存自己会跟上。
// 走那一条链而不是这里直接 updateTopic，是为了**只有一条保存路径**
// （正文本来就有自动保存，再单开一条就成了两处各写一遍）。
const editOpen = ref(false)

/**
 * 给抽屉的对比区用：**markdown → ProseMirror doc**。
 *
 * 抽屉要在左边画"原文渲染后的样子" ⇒ 必须用**编辑器自己的解析器**（同一个 schema、
 * 同一套 toDOM），否则预览和正文对不上（标题层级、软换行、字号 mark、图片节点…）。
 * 另找一个 markdown 渲染器就是两套渲染 ⇒ 那种坑已经踩过一次。
 *
 * 编辑器在 MilkdownProvider 里面，外层拿不到它的 ctx ⇒ 这里代为转发。
 */
const docOfBody = (md) => editorRef.value?.docOf(md) ?? null

function applyEdited(body) {
  // 编辑器没就绪时 replaceContent 返回 false —— 那时候写进去等于没写
  if (!editorRef.value?.replaceContent(body)) {
    ElMessage.error('没写进去：编辑器还没准备好')
    return
  }
  editOpen.value = false
  ElMessage.success('已应用')
}

// 换一条知识就换一个答案：上一个问题的记录留着会张冠李戴。
// 抽屉也一并关掉——开着上一条的问答面板看下一条，最容易认错。
watch(() => topic.value?.id, () => {
  askOpen.value = false
  askInput.value = ''
  messages.value = []
  // 采纳框同理：它是拿上一条的回答去改这一条，不关的话点确认就把 A 的建议写进 B
  adoptOpen.value = false
  // 调整正文的抽屉同理：它装着上一条正文的原文和改写结果。
  // 不关的话，「应用到正文」会把 A 的改写写进 B。
  editOpen.value = false
  // 大纲同理：它是**上一条**正文的标题结构。换一条知识就换一份大纲。
  // 不清的话，新编辑器还没就绪的那一瞬间（重建编辑器要等它渲染完），
  // 页面上挂的还是上一条的大纲 —— 用户看到的是"这条知识没有目录，却有大纲"。
  // 编辑器就绪后会再报一次（报空数组），但那已经是几百毫秒之后了。
  outline.value = []
  activeHeadingId.value = ''
})

async function handleDelete() {
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

function goCategory(id) {
  router.push(categoryLink(id))
}


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
  // ⚠️ **滚动容器是壳的 `.app-content`，列表页和详情页共用同一个**。详情页进来时把位置往下推了，
  // 切走时不归零的话，列表页会顶着"上一条知识的滚动位置"进场（尤其列表页顶部那几行
  // 标题/筛选条正好被吃掉一截）。归零放在这里而不是 onBeforeUnmount：keep-alive 切走
  // 不销毁，onBeforeUnmount 压根不触发。
  const host = detailRef.value?.parentElement
  if (host) host.scrollTop = 0
  // 容器归零了，"上次对齐到哪儿"也得一起归零 —— 否则切回来时
  // alignContentTop 会以为用户滚过（scrollTop 0 ≠ lastAlignedTop）而跳过对齐。
  lastAlignedTop = 0
})


// 路由 id 变化时检查是否需要重新 load：
// keep-alive 缓存 TopicDetailView 实例后，同 route name 复用实例，topic.value 可能已经是
// 当前 id 的数据——切回同 id 不重新 fetch（保留本地编辑状态）；切到新 id 走 load。
watch(() => route.params.id, () => {
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) return
  if (topic.value && topic.value.id === id) return  // 同 id：复用缓存，不重新加载
  load()
})
onMounted(() => {
  load()
})

// 懒加载 tag 列表：用户点开箭头下拉时才调 /kp/tag/list。
// 详情页打开不请求；首次点开触发请求；后续复用 store 缓存。
// （没点开过箭头就直接打字回车是纯新建路径，此时 availableTags 是空的，
//   commitTypedTag 里的"精确命中就选中"自然匹配不到，不会误判。）
watch(tagListOpen, (open) => {
  if (open) ensureAllTagsLoaded()
})

/**
 * 往上找真正在滚的那一层。**故意不写死 `.app-content`，但也不去猜"哪一层在滚"**：
 * 判据只有"overflow-y 是滚动值"这一条，**不查 scrollHeight**。
 *
 * ⚠️ **查 scrollHeight 是我上一版写错的地方**：正文是 Milkdown 异步渲染的，`watch(topic)`
 * 触发那一刻编辑器还是空的（只有 min-height 135px），内容"装得下"→ 代码直接跳过，
 * 等正文长出来没人再触发。**能不能滚上取决于编辑器渲染快慢，所以时灵时不灵。**
 * 现在只认 overflow：装得下时 `scrollTop` 会被浏览器夹到 0，本来就是正确的"不用滚"。
 */
function scrollParentOf(el) {
  for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
    if (/(auto|scroll|overlay)/.test(getComputedStyle(p).overflowY)) return p
  }
  return null
}

/**
 * 进来时把滚动位置对到「标签行那条分隔线 = 可视区顶部」：返回栏和标签行滚出视野，
 * 第一眼直接落在正文上（用户原话："滚到这条线就行"，图二就是那个效果）。
 *
 * ⚠️ **锚点是 `.tags` 的下沿，不是 `.content` 的上沿**。差的是 `.tags` 那个
 * `margin-bottom`（20px）：对齐下沿 = 那段间距留在分隔线和卡片之间，和用户给的图二一致；
 * 对齐卡片上沿会把间距也滚掉、卡片直接贴死顶栏，不一样。
 *
 * 用**双 rAF** 而不是 nextTick：nextTick 只保证 Vue 的 DOM 更新完了，**不保证浏览器已经
 * 算完布局**，Milkdown 还在异步起。这一条就是上一版"时灵时不灵"的另一半原因。
 *
 * ⚠️ **用序号合并而不是直接调**：一次进场会连着触发好几条（onMounted + watch(topic)，
 * 或 onActivated + 一次重渲染）。位移是相对的（`+=`），跑两遍就滚过头 —— 这里只认最后一次
 * 请求，把前面的丢掉。
 */
let alignSeq = 0
function scheduleAlign() {
  const seq = ++alignSeq
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (seq !== alignSeq) return
    alignContentTop()
  }))
}

/**
 * 上次对齐之后，滚动容器停在哪儿。
 *
 * ⚠️ **这是「往下翻着翻着突然被刷回最上面」的根因所在**：
 * `alignContentTop()` 做的是**相对位移**（`scrollTop += 标签行下沿 - 容器上沿`），
 * 这个式子只有在"刚进场、正文还在顶上"时才对。用户自己往下翻了以后再触发它，
 * 那个 delta 是**负的** —— 等于把人从当前位置拽回顶部。
 *
 * 而**自动保存每次都会换一份 `topic` 快照**（`topic.value = { ...topic.value, content }`），
 * `watch(topic)` 就会调 `scheduleAlign` —— 于是"改一个字、1.5 秒后自动保存"，
 * 正好把正在往下读的人刷回顶部。
 *
 * 判据：容器现在的 scrollTop 跟上次对齐到的位置差得超过几个像素 ⇒
 * 说明用户（或别处）动过它 ⇒ **别再按"进场对齐"去动它**。
 * scrollTop 为 0 单独放行：那是"刚进来 / 刚从别处切回来"的状态。
 */
let lastAlignedTop = 0

function alignContentTop() {
  const root = detailRef.value
  const line = root?.querySelector('.tags')   // 目标就是它下沿那条 border-bottom
  if (!line) return
  const host = scrollParentOf(line)
  if (!host) return   // 壳是 overflow:hidden + height:100%，整页滚不动，没有"第二选择"
  // 用户已经自己滚走了 → 他的滚动位置优先，别动（详见 lastAlignedTop 的说明）
  if (host.scrollTop > 4 && Math.abs(host.scrollTop - lastAlignedTop) > 4) return
  host.scrollTop += line.getBoundingClientRect().bottom - host.getBoundingClientRect().top
  lastAlignedTop = host.scrollTop
}

// 四个触发点缺一不可：keep-alive（onActivated）+ 异步加载（watch topic）+ 全屏（watch immersive）
onMounted(scheduleAlign)
onActivated(scheduleAlign)
watch(topic, scheduleAlign)
watch(() => immersive.on, scheduleAlign)

// 离开详情页时清理自动保存定时器，避免后台 setTimeout 在组件销毁后还触发 updateTopic
onBeforeUnmount(() => {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null }
  if (statusTimer) { clearTimeout(statusTimer); statusTimer = null }
})
</script>

<style scoped>
/* ⚠️ **这里刻意没有 max-width**（默认档，也是唯一的档）。原来写的是 `max-width: 920px`，而
   `max-width` 是个定值：窗口一宽过 920，卡片就卡住不动、两侧空白一路增长 —— **卡片占屏
   比例随窗口宽度一路往下掉，正是用户反复投诉的那个问题**（#134 当初给列表页/首页解除了
   1440 上限，**只把详情页留作例外**，例外就是问题的来源）。#115/#116 当初定 920 的理由
   是"长文一行别太长"，但那条理由后来用户自己推翻了：**比例恒定比行长重要**。

   2026-10-21 顺手把「阅读宽度」那根可选封顶的滑块也删了 —— 它默认 2400 ≈ 不封顶，
   在比它窄的窗口上根本不生效，**调它像调了个摆设**（用户原话："我试了调整好像一点用都没有"）。
   现在详情页在所有窗口下都是 100% 铺满内容区。

   壳那边的 `.app-content:has(.home, .learn, .detail) > :where(*) { max-width: none }`
   已经把内容区的 1440 定值解除了 ⇒ 这里不写 max-width 就是"不封顶"。 */
.detail {
  display: flex;
  flex-direction: column;
}

/* 正文 + 右侧大纲：两列。**只在正文有标题时**大纲那一列才存在（见模板 v-if），
   所以这里不需要为"没有大纲"准备一个空位——flex 少一个子项就少一列。 */
.doc {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-16);
}
/* 正文列吃掉剩下的宽度。min-width: 0 是必须的：否则长代码块 / 长表格
   会把这一列撑破，把大纲栏挤到屏幕外面去。 */
.doc > .content {
  flex: 1;
  min-width: 0;
}



/* ---------- 顶栏 ---------- */
.bar {
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  min-height: 29px;
  /* 顶栏到下面那行的距离：**写死**（原来是「顶栏下」那根滑块，2026-10-21 去掉）。 */
  margin-bottom: var(--sp-11);
}
.bar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-10);
  flex-shrink: 0;
}
.bar__time {
  font-size: var(--text-md);
  color: var(--cc-text-4);
  font-variant-numeric: tabular-nums;
}
/* 返回键：和工具栏那些图标按钮不同，它是页面级的导航动作，用幽灵样式（无底无框） */
.ghost {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  font-size: var(--text-base);
  /* 负左边距把图标拉回来：幽灵按钮的 padding 让"返回"两字比旁边的面包屑缩进一格 */
  margin-left: calc(-1 * var(--sp-8));
}
.ghost:hover {
  color: var(--cc-text-1);
  background: transparent;
}

/* 保存状态：只在自动保存期间出现（2 秒后自己消失），所以**不占常驻位置** */
.save-state {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
  font-size: var(--text-sm);
  color: var(--cc-text-3);
}
.save-state__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.save-state.is-saved {
  color: #2aa876;
}
.save-state.is-saving {
  color: var(--cc-text-3);
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
  /* 700 是跟正文里的 H1 对齐的：prosemirror.css 没给 h1 任何规则，
     粗细全是浏览器默认的 bold（=700），颜色则继承 .ProseMirror 的 --cc-text-1，
     跟这里本来就是同一个色。之前写 600，比 H1 轻一档，同一句话并排出现时
     上面的标题看着"不是标题"。字号没跟着改成 28px（h1 的 2em）——
     那是排版尺寸，用户只要了粗细和颜色。 */
  font-weight: 700;
  line-height: 1.45;
  /* var() 不能直接取负（负号后面直接接 var()，整条声明会被浏览器丢掉 ⇒
     字距根本没生效），负值要包一层 calc */
  letter-spacing: calc(-1 * var(--ls-tight));
  color: var(--cc-text-1);
  padding: var(--sp-4) 0 var(--sp-3);
  margin: 0 0 var(--sp-2);
}

/* ---------- 标签行 ---------- */
.tags {
  display: flex;
  align-items: center;
  gap: var(--sp-7);
  flex-wrap: wrap;
  padding-bottom: var(--sp-11);
  margin-bottom: var(--detail-tags-gap, var(--sp-13));
  border-bottom: 1px solid var(--cc-border);
}

/* ---------- 标签入口 / 输入框 ---------- */
/* 两者都是"空 chip"：高 20px、4px 圆角、--cc-surface-2 底色、**没有边框**——
   三样全部照抄全局 .kb-chip 的尺寸，所以它跟在真 chip 旁边是同一种东西。
   之前给它画了一圈 1px 边框、还做到 24px 高，结果在 chip 后面杵着一个
   又高又带框的方块，一眼就是个控件而不是"再加一个标签"。 */
.tags__add,
.tags__picker {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 var(--sp-7);
  border-radius: 4px;
  background: var(--cc-surface-2);
  border: none;
  color: var(--cc-text-4);
  font-size: var(--text-sm);
  cursor: pointer;
  font-family: inherit;
  transition: var(--cc-transition);
}
.tags__add {
  gap: var(--sp-4);
}
.tags__add:hover {
  background: var(--cc-surface-3);
  color: var(--cc-text-2);
}
.tags__picker {
  cursor: text;
  padding-right: 0;
  gap: var(--sp-4);
}
.tags__picker :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0;
}
.tags__picker :deep(.el-input__inner) {
  height: 20px;
  line-height: 20px;
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  padding: 0 var(--sp-7);
}
.tags__picker:focus-within {
  background: var(--cc-surface);
  box-shadow: inset 0 0 0 1px var(--cc-border-strong);
}
.tags__caret {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--cc-text-4);
  cursor: pointer;
  padding: 0;
  border-radius: 4px;
}
.tags__caret:hover {
  color: var(--cc-text-2);
  background: var(--cc-surface-3);
}
.tags__caret.is-open {
  transform: rotate(180deg);
}
.tags__list {
  position: absolute;
  /* 贴着入口下方弹，和侧边栏的目录树一个层级 */
  z-index: 30;
  margin-top: var(--sp-3);
  min-width: 200px;
  max-height: 260px;
  overflow-y: auto;
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 12px rgb(0 0 0 / 8%);
  padding: var(--sp-3) 0;
}
.tags__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-8);
  width: 100%;
  border: none;
  background: transparent;
  padding: var(--sp-5) var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  cursor: pointer;
  text-align: left;
  font-family: inherit;
}
.tags__list-item:hover {
  background: var(--cc-surface-2);
}
.tags__list-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tags__list-count {
  font-size: var(--text-xs);
  color: var(--cc-text-4);
  flex-shrink: 0;
}
.tags__list-empty {
  padding: var(--sp-5) var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-text-4);
  margin: 0;
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

/* ---------- 对这条知识提问（右侧抽屉） ---------- */
.ask {
  display: flex;
  flex-direction: column;
  height: 100%;
  /* 把手是 absolute 的定位基准。它自己往左探出一格 padding 去贴抽屉边界 */
  position: relative;
}
/* 拖动把手。**占满抽屉内容区左边那一格空 padding**，所以贴在抽屉真正的左边界上，
   而不是缩在内容左边 22px 的地方（原先 `left: 0` 是相对内容区起点，看着像"贴内容框"）。
   left 和 width 都接 EP 自己的 token —— 它改 padding，这条自动跟着挪，不会脱节。
   22px × 96px 的竖条：22px 宽正好是那一格的宽度，96px 长是为了好找，
   命中区比之前（20×44）大了一倍多 */
.ask__grip {
  position: absolute;
  left: calc(-1 * var(--el-drawer-padding-primary, 20px));
  width: var(--el-drawer-padding-primary, 20px);
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96px;
  cursor: col-resize;
  /* 不加这句，移动端/触控板拖动会被当成页面滚动 */
  touch-action: none;
  color: var(--cc-text-3);
  background: var(--cc-surface-2);
  border: 1px solid var(--cc-border);
  /* 左边贴抽屉边是直的，只圆右边 */
  border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  opacity: 0;
  transition: opacity 0.15s;
}
.ask__grip__icon {
  font-size: var(--text-base);
}
.ask__grip:hover,
.ask__grip:active {
  opacity: 1;
  color: var(--cc-primary);
  border-color: var(--cc-primary);
}
.ask__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* **左右不再额外给 padding**：抽屉内容区自己已经留了 --el-drawer-padding-primary（22px），
     再叠一层就会比上方标题「AI 问答」和下方输入框都往右缩一截，看着是歪的。
     左边那一格已经被拖动把手占了 */
  padding: var(--sp-11) 0;
}
.turn {
  margin-bottom: var(--sp-14);
}
/* 组内（提问气泡 ↔ 思考 ↔ 回答）紧，组间松——这个疏密差就是"一轮问答"的视觉分组 */
.turn > * + * {
  margin-top: var(--sp-8);
}
/* 「发送给 AI 的内容」和「思考过程」是同一个控件的两种：都是折起来的一行标题 + 展开的正文，
   共用一套 summary 样式（▸ / ▾），差别只在标题文案和正文的排版。 */
.think,
.sent {
  font-size: var(--text-xs);
  /* 比正文暗一档。这两块是"核对用的旁证"，不是对话内容——原先和正文同色，
     折起来的那一行反而比回答还抢眼 */
  color: var(--cc-text-4);
}
.think summary,
.sent summary {
  cursor: pointer;
  list-style: none;
  user-select: none;
}
.think summary::-webkit-details-marker,
.sent summary::-webkit-details-marker {
  display: none;
}
.think summary::before,
.sent summary::before {
  content: '▸ ';
}
.think[open] summary::before,
.sent[open] summary::before {
  content: '▾ ';
}
.think__body,
.sent__body {
  margin-top: var(--sp-6);
  padding-left: var(--sp-10);
  border-left: 2px solid var(--cc-border);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
/* 「正在思考…」是折叠块的正文，跟着折叠块一起弱。
   「正在生成…已接收 N 字」是独立的实时信号——流式期间用户就盯着它，
   弱到和折叠标题一样就等于没有，所以单独提一档。三层：正文 > 生成计数 > 折叠标题 */
.think__waiting {
  margin-top: var(--sp-6);
  padding-left: var(--sp-10);
  color: var(--cc-text-4);
  font-size: var(--text-2xs);
}
.think__count {
  margin-top: var(--sp-6);
  padding-left: var(--sp-10);
  color: var(--cc-text-3);
  font-size: var(--text-2xs);
}
/* 提交内容是**整段 messages**，比思考过程长得多，字号再小一号不然面板撑不住 */
.sent__body {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: var(--text-2xs);
  color: var(--cc-text-2);
}
.bubble {
  max-width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
}
.bubble.is-user {
  max-width: 78%;
  margin-left: auto;
  padding: var(--sp-8) var(--sp-10);
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--cc-text-1);
  background: var(--cc-surface-2);
  border-radius: var(--radius-md) var(--radius-md) var(--sp-2)
    var(--radius-md);
}
/* AI 回答不加任何容器。**试过加左侧竖线，撤掉了**：回答下面本来就跟着
   「思考过程 / 发送给 AI 的内容」两块旁证（它们各有一条 border-left），
   再来一条就变成三条竖线并排——用户那一侧是有底的灰气泡，AI 这侧用
   「字号比正文大一点」就足够区分，加竖线是过度设计 */
.bubble.is-ai {
  font-size: var(--text-md);
  color: var(--cc-text-1);
}
.bubble.is-error {
  color: var(--cc-danger, #f56c6c);
  font-size: var(--text-sm);
}
/* 「复制」「用这个」并排一行。两者形态完全一样（文字型次要按钮），差别只在含义，
   所以只在这里给一组尺寸，不给它们各写一套变体 */
.ask__ops {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}
.ask__ops .kb-text-btn {
  padding: 0 var(--sp-6);
  font-size: var(--text-xs);
}
.ask__guide {
  display: flex;
  /* 横着排。原来是竖排一列六个，一列就是 300 多 px——抽屉才 800 出头，
     光这一块就吃掉三分之一，正文没地方站 */
  flex-wrap: wrap;
  gap: var(--sp-4) var(--sp-6);
  padding-top: var(--sp-8);
  /* 常驻在对话上方之后，下面得有一道间距把它和第一轮问答分开。
     `.turn > * + *` 管的是一轮内部和轮与轮之间，不含这个块 */
  margin-bottom: var(--sp-6);
}
.ask__chip {
  padding: var(--sp-6) var(--sp-10);
  font-size: var(--text-sm);
  color: var(--cc-text-2);
  background: var(--cc-surface);
  border: 1px solid var(--cc-border);
  border-radius: var(--radius-xl);
}
.ask__chip:hover {
  color: var(--cc-primary);
  border-color: var(--cc-primary);
}
.ask__foot {
  flex: none;
  padding-top: var(--sp-10);
  border-top: 1px solid var(--cc-border);
}
/* 一个胶囊：输入框 + 「用哪个 API」+ 发送/停止 全在右下角一行里。
   焦点态挂在容器上（:focus-within）而不是输入框自己——输入框现在没有边框，
   边框是胶囊的，边框跟着输入框走会出现"框里还套个框"。 */
.ask__composer {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  padding: var(--sp-4) var(--sp-4) var(--sp-4)
    var(--sp-10);
  background: var(--cc-surface-2);
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  transition: var(--cc-transition);
}
.ask__composer:focus-within {
  background: var(--cc-surface);
  border-color: var(--cc-border);
}
.ask__model {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex: none;
  max-width: 140px;
  height: var(--ctl-sm);
  padding: 0 var(--sp-8);
  font-size: var(--text-xs);
  color: var(--cc-text-2);
  border-radius: var(--radius-lg);
  cursor: pointer;
  outline: none;
  white-space: nowrap;
  overflow: hidden;
}
.ask__model:hover {
  color: var(--cc-text-1);
}
.ask__caret {
  display: inline-block;
  width: 0;
  height: 0;
  margin-top: var(--sp-3);
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid currentColor;
}
.ask__input {
  flex: 1;
  min-width: 0;
  height: var(--ctl-sm);
  /* 左边距由胶囊的 padding 给，这里必须是 0，否则文字和胶囊边缘之间多一层 */
  padding: 0;
  font-size: var(--text-sm);
  color: var(--cc-text-1);
  background: transparent;
  border: none;
  outline: none;
}
/* 发送 / 停止 是同一个钮的两种状态，不并排两个按钮：
   并排等于把"现在该按哪个"这个问题推给用户，停用的那一个还要白占地方。 */
.ask__send {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--ctl-sm);
  height: var(--ctl-sm);
  padding: 0;
  font-size: var(--text-base);
  color: var(--cc-surface);
  background: var(--cc-primary);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: var(--cc-transition);
}
.ask__send:hover:not(:disabled) {
  opacity: 0.86;
}
.ask__send:disabled {
  color: var(--cc-text-4);
  background: #e2e6ec;
  cursor: not-allowed;
}
/* 在流中时这个钮**不是"停用的发送"，是"正在生成、点这里中断"**，
   所以图标用深色而不是跟着 disabled 一起变浅——一变浅就和上面那个空输入态撞脸了 */
.ask__send.is-stop {
  color: var(--cc-text-2);
  background: #e2e6ec;
}

/* ---------- 空 / 加载 ---------- */
.state {
  padding: 77px 0;
  text-align: center;
  color: var(--cc-text-4);
  font-size: var(--text-base);
}
.state p {
  margin: 0;
}
</style>
