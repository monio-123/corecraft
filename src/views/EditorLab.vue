<template>
  <div class="lab">
    <header class="lab__bar">
      <h1>编辑器实验台</h1>
      <span class="lab__note">编辑器行为的自测页：改完编辑器相关功能来这里跑一遍。免登录、不落库。</span>
      <el-button size="small" @click="toolbar = !toolbar">{{ toolbar ? '简洁模式' : '完整模式' }}</el-button>
    </header>

    <section class="lab__presets">
      <span class="lab__label">预设内容</span>
      <el-button v-for="p in PRESETS" :key="p.id" size="small" text @click="load(p)">{{ p.name }}</el-button>
    </section>

    <MilkdownEditor
      :key="epoch"
      ref="editorRef"
      v-model="md"
      :show-toolbar="toolbar"
      :category-tree="categoryTree"
      @outline="outline = $event"
    />

    <section class="lab__out">
      <div class="lab__outHead">
        <span class="lab__label">存下来的 markdown</span>
        <el-button size="small" text @click="checkRoundTrip">往返自检</el-button>
        <el-button size="small" text @click="copyMd">复制</el-button>
      </div>
      <textarea v-model="md" class="lab__ta" spellcheck="false" />
    </section>

    <!-- 大纲 + 折叠自测：**用详情页那个真组件**（TopicOutline.vue）——
         之前这里自己写了一份列表，那是两份真相：改了一份另一份不动，
         而且测不到工具条和隐藏。现在实验台就是这块 UI 的自测面。 -->
    <section v-if="outline.length" class="lab__outline">
      <div class="lab__label">大纲（点一条跳过去；鼠标移上去有工具条；标题左边的箭头折的是大纲自己那一支）</div>
      <TopicOutline
        :items="outline"
        @navigate="goHeading"
      />
    </section>

    <section v-if="check" class="lab__check" :class="check.ok ? 'is-ok' : 'is-bad'">
      <p class="lab__checkTitle">{{ check.ok ? '往返稳定' : '往返不稳定 —— 这就是刷新后走样的那种内容' }}</p>
      <pre v-if="!check.ok">{{ check.detail }}</pre>
    </section>
  </div>
</template>

<script setup>
/**
 * 编辑器实验台。
 *
 * ## 为什么要有这个页面
 *
 * 编辑器的问题**几乎都是"当场看着对、存下来再打开就不对"**：
 * markdown 往返、粘贴的块格式、空行、标记的转义……症状全在
 * 「序列化 → 重新解析」这一段，肉眼在编辑器里看不出来。
 *
 * 所以这个页面把那一段**摆到明面上**：左边编辑，右边就是存进库里的
 * markdown，再加一个「往返自检」——把当前内容重新解析一遍再序列化，
 * 两次结果不一致就说明存下来会走样。
 *
 * ## 约定
 *
 * - **免登录、不落库**：所有内容只在这一个页面的内存里，不请求后端。
 *   要的是"能快点跑一遍"，不是"存下来"。
 * - **目录树是假数据**：编辑器那个「设为目录链接」的选择器要一棵目录树，
 *   这里给一份固定的示例（结构照抄真实的 `语言/java/基础/集合/List`），
 *   因为真目录要登录才拉得到，而这一页就是不登录的。
 * - **别删**：它是编辑器功能的自测入口，不是临时代码。
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MilkdownEditor from '../components/MilkdownEditor.vue'
import TopicOutline from './corecraft-web/TopicOutline.vue'
import { toCategoryTree } from '../utils/categoryTree'
import { copyText } from '../utils/clipboard'

defineOptions({ name: 'EditorLab' })

/** 示例目录：结构和真实目录一致（`语言/java/基础/集合/List`），但 id 是编的 */
const SAMPLE_CATEGORIES = [
  { id: 48, name: '语言', parentId: 0 },
  { id: 1, name: 'java', parentId: 48 },
  { id: 53, name: '基础', parentId: 1 },
  { id: 6, name: '集合', parentId: 53 },
  { id: 58, name: 'List', parentId: 6 },
  { id: 59, name: 'Set', parentId: 6 },
  { id: 60, name: 'Map', parentId: 6 },
  { id: 2, name: '计算机网络', parentId: 0 }
]
const categoryTree = toCategoryTree(SAMPLE_CATEGORIES)

const PRESETS = [
  {
    id: 'plain',
    name: '纯文本',
    md: '集合分为两大分支：Collection 与 Map。\n\nCollection 又分为 List 和 Set。'
  },
  {
    id: 'heading',
    name: '标题层级',
    md: '# 一级标题\n\n## 二级标题\n\n正文一段。\n\n### 三级标题\n\n再一段。\n\n## 第二个二级\n\n结尾。'
  },
  {
    id: 'bold-list',
    name: '列表 + 粗体',
    // **两个版本差一个空格，差的正是「粗体保不保得住」**：
    //   `**List:** List代表` → 闭合的 `**` 后面是空格，CommonMark 闭得上 → 真粗体；
    //   `**List：**List代表`   → 闭合的 `**` 后面紧跟字母，闭不上 → 解析成两个字面 `**`。
    // 后者就是「刷新后多出一堆 **」的来源（见 utils/kbMarkdown.js）。
    md: 'Collection派生了三个子接口：\n\n- **List:** List代表了有序可重复集合，可直接根据元素的索引来访问\n- **Set:** Set代表无序不可重复集合，只能根据元素本身来访问\n- **Queue:** Queue是队列集合'
  },
  {
    id: 'bold-list-tight',
    name: '粗体紧跟文字（无空格）',
    // **这个才是坏的那一版**：闭合的 `**` 后面紧跟字母。
    // 从 markdown 侧灌进来时就已经是字面 `**` 了（解析器闭不上），
    // 所以它测的是"坏内容长什么样"，不是"修复"——修复要靠编辑器里造出真粗体再删空格。
    md: '- **List：**List代表了有序可重复集合\n- **Set：**Set代表无序不可重复集合'
  },
  {
    id: 'link',
    name: '链接',
    md: '正文里带一个普通链接：\n\n[MDN](https://developer.mozilla.org)\n\n带一个目录引用：\n\n见 [List](/app/learn?category=58)'
  },
  {
    id: 'table',
    name: '表格',
    md: '| 集合 | 有序 | 可重复 |\n| --- | --- | --- |\n| List | 是 | 是 |\n| Set | 否 | 否 |'
  },
  {
    id: 'code',
    name: '代码块',
    md: '```java\nList<String> list = new ArrayList<>();\n```'
  }
]

/**
 * 初始预设。**URL 里可以带 `?preset=heading` 直接打开某个预设**。
 *
 * 为什么需要：折叠箭头是"鼠标移上去才出现"的效果，**只能靠眼睛看**，
 * 而点预设按钮会重建编辑器、把状态清一遍 —— 反复点来点去很别扭。
 * 带个参数直接落到要测的那个预设上，测完刷新一下就是干净的初始态。
 */
const startPreset = PRESETS.find((p) => p.id === new URLSearchParams(window.location.search).get('preset')) || PRESETS[0]
const md = ref(startPreset.md)

const toolbar = ref(true)
/** 改这个值 = 把编辑器整个重建（用来强制重新解析 markdown） */
const epoch = ref(0)
const check = ref(null)
/** 编辑器报上来的标题大纲（和详情页右侧那栏是同一份数据） */
const outline = ref([])
/** 编辑器实例：跳到某个标题、折叠某个标题都调它的方法 */
const editorRef = ref(null)

function goHeading(id) {
  editorRef.value?.scrollToHeading(id)
}

function load(p) {
  md.value = p.md
  epoch.value++
  check.value = null
}

/** 等编辑器重建完、正文渲染完、v-model 回灌完 */
async function settle() {
  for (let i = 0; i < 40; i++) {
    await nextTick()
    await new Promise((r) => setTimeout(r, 50))
  }
}

/**
 * 往返自检：**重新解析 → 再序列化**，两次结果一致才算稳。
 *
 * 为什么是"两次"而不是"一次"：一次只能看出"存下来的和看到的不一样"，
 * 两次才能看出"存下来的再存一遍还会变" —— 后者才是真正会累积走样的那种。
 * 和 @milkdown 预设自己的 mark-roundtrip 测试用的是同一个判据。
 */
async function checkRoundTrip() {
  const first = md.value
  epoch.value++
  await settle()
  const second = md.value
  epoch.value++
  await settle()
  const third = md.value

  if (second === third) {
    check.value = { ok: true, detail: '' }
    return
  }
  check.value = {
    ok: false,
    detail: [
      `【第一次存下来的】\n${first}`,
      `【重新打开后再存】\n${second}`,
      `【再打开一次】\n${third}`
    ].join('\n\n')
  }
}

async function copyMd() {
  if (await copyText(md.value)) ElMessage.success('已复制')
}
</script>

<style scoped>
.lab {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 16px 60px;
}
.lab__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.lab__bar h1 {
  margin: 0;
  font-size: 18px;
}
.lab__note {
  color: #909399;
  font-size: 12px;
}
.lab__presets {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.lab__label {
  color: #606266;
  font-size: 13px;
  margin-right: 6px;
}
.lab__out {  margin-top: 20px;
}
.lab__outline {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.lab__outHead {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}
.lab__ta {
  width: 100%;
  min-height: 180px;
  padding: 10px;
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  resize: vertical;
}
.lab__check {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.lab__check.is-ok {
  background: #f0f9eb;
  color: #67c23a;
}
.lab__check.is-bad {
  background: #fef0f0;
  color: #f56c6c;
}
.lab__checkTitle {
  margin: 0 0 6px;
  font-weight: 600;
}
.lab__check pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: #303133;
  font-size: 12px;
}
</style>
