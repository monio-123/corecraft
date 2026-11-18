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

    <!-- ⚠️ §5.1 #10：判定三档并到全站的 .kb-verdict 一族。
         原来这里是 `.lab__check` + `is-ok` / `is-bad`，**自己写了一套颜色**
         （EP 的 #67c23a 绿 / #f56c6c 红）⇒ 与考试页那三档是两套。
         ⇒ 挂上 .kb-verdict 标记类，档位名用那一族的 `is-good` / `is-bad`
         （注意 `is-bad` 这个名字别处也在用 —— 拖拽落点、目录名校验 ——
         它们**没有** .kb-verdict 这道标记类，所以不会被这一族染色）。
         ⇒ 局部那两条颜色规则删掉，颜色只从 kb.css 的 .kb-verdict 来。 -->
    <section
      v-if="check"
      class="lab__check kb-verdict"
      :class="check.ok ? 'is-good' : 'is-bad'"
    >
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
    id: 'file',
    name: '附件',
    // **附件节点**（kbFileNode，2026-11-16）：指向 /api/files/ 的链接在解析时转成
    // 原子节点（图标 + 文件名、光标进不去）。这个预设测三件事：
    //   ① 三条不同扩展名（xlsx / md / pdf）各渲染成带图标的 chip；
    //   ② 选中附件按 Delete 整块删（光标进不去文件名中间）；
    //   ③ 往返自检后仍是 `[文件名](/api/files/…)` —— 正文格式不变，DB 零迁移。
    //   ⚠️ 链接是假的（没有真文件），点 chip 下载会 404 —— 测的是渲染与往返，不是下载。
    md: '带三个附件：\n\n[天源迪科-电渠资源台账.xlsx](/api/files/u/6/contents/abc123/天源迪科-电渠资源台账.xlsx)\n\n[说明文档.md](/api/files/u/6/contents/abc123/说明文档.md)\n\n[报告.pdf](/api/files/u/6/contents/abc123/报告.pdf)\n\n普通链接不受影响：[MDN](https://developer.mozilla.org)'
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
  },
  {
    id: 'kbfig-all',
    name: 'kbfig v2 全图型 (10 种)',
    md: [
      '### 1. 层级树（tree）',
      '```kbfig\n{"type":"tree","title":"集合框架接口层次","root":{"t":"集合框架接口","tag":"两大体系","children":[{"t":"Collection","d":"单列数据根接口","children":[{"t":"List","d":"有序、可重复"},{"t":"Set","d":"无序、不可重复","tag":"SortedSet"},{"t":"Queue","d":"先进先出队列","tag":"Deque"}]},{"t":"Map","d":"键值对根接口","children":[{"t":"SortedMap","d":"按键排序","tag":"NavigableMap"}]}]}}\n```',
      '### 2. 自由拓扑（graph）',
      '```kbfig\n{"type":"graph","title":"ConcurrentHashMap putVal 写入分支","dir":"LR","nodes":[{"id":"a","label":"putVal 写入","kind":"step"},{"id":"b","label":"table 已初始化?","kind":"decision"},{"id":"c","label":"initTable CAS 立表","kind":"step"},{"id":"d","label":"目标桶为空?","kind":"decision"},{"id":"e","label":"casTabAt 无锁写入","kind":"step","hl":"ok"},{"id":"f","label":"桶头 hash 态","kind":"decision"},{"id":"g","label":"helpTransfer 协助扩容","kind":"step"},{"id":"h","label":"synchronized 锁头","sub":"链表 / 红黑树插入","kind":"step","hl":"warn"}],"edges":[{"from":"a","to":"b"},{"from":"b","to":"c","label":"否"},{"from":"b","to":"d","label":"是","hl":"ok"},{"from":"d","to":"e","label":"是","hl":"ok"},{"from":"d","to":"f","label":"否"},{"from":"f","to":"g","label":"MOVED","style":"dashed"},{"from":"f","to":"h","label":"正常","hl":"primary"}]}\n```',
      '### 3. 短步横排（stages）',
      '```kbfig\n{"type":"stages","title":"HashMap 扩容与 rehash 流程","steps":[{"t":"触发判定","d":"size > threshold"},{"t":"申请新数组","d":"容量翻倍","hl":true},{"t":"逐桶拆分","d":"hash & oldCap"},{"t":"回填新表","d":"低位原下标"},{"t":"更新阈值","d":"newCap × factor"}],"loop":"注：Java 8 尾插保持原链顺序"}\n```',
      '### 4. 内存槽位（memory）',
      '```kbfig\n{"type":"memory","title":"HashMap 桶数组 + 链表 + 红黑树挂载图","stack":[{"name":"map 引用","ref":"0x7f3a → 堆实例"}],"container":{"name":"堆内存 · Node<K,V>[] table","slots":[{"index":0,"val":"null"},{"index":1,"val":"桶位链表头","hl":"warn","chain":[{"t":"Node K1","d":"next → 下一节点"},{"t":"Node K2","d":"next → null"}]},{"index":2,"val":"红黑树根","hl":"ok","tree":{"root":"Root","left":"T1","right":"T2","note":"链表 ≥ 8 且容量 ≥ 64 时树化"}}]}} \n```',
      '### 5. 时序交互（sequence）',
      '```kbfig\n{"type":"sequence","title":"BlockingQueue put / take 阻塞与唤醒","participants":[{"id":"p","name":"生产者"},{"id":"q","name":"阻塞队列","hl":true},{"id":"c","name":"消费者"}],"messages":[{"from":"p","to":"q","text":"1. put(e) 队列已满","kind":"sync"},{"from":"q","to":"p","text":"2. 挂起等待 notFull","kind":"wait"},{"from":"c","to":"q","text":"3. take() 腾出空位","kind":"sync"},{"from":"q","to":"p","text":"4. signal() 唤醒","kind":"ok"}]}\n```',
      '### 6. 演进里程碑（timeline）',
      '```kbfig\n{"type":"timeline","title":"Java 演进（8 → 11 → 17 → 21）","events":[{"tag":"JDK 8","t":"Lambda 与 Stream","d":"函数式编程落地"},{"tag":"JDK 11 LTS","t":"var 与 HttpClient","d":"ZGC 实验"},{"tag":"JDK 17 LTS","t":"Sealed 与 Record","d":"代数数据类型"},{"tag":"JDK 21 LTS","t":"虚拟线程转正","d":"分代 ZGC","hl":true}]}\n```',
      '### 7. 核心拆解（anatomy）',
      '```kbfig\n{"type":"anatomy","title":"Java 数组对象构成","center":{"t":"数组对象实例","tag":"堆内存连续区","d":"int[] b = {5, 2, 8}"},"parts":[{"t":"栈上引用 b","d":"只存堆首地址"},{"t":"对象头 Header","d":"MarkWord + 类型指针"},{"t":"只读字段 length","d":"固定不变 = 3","hl":true},{"t":"连续槽位 b[0..2]","d":"O(1) 寻址读取"}]}\n```',
      '### 8. 多维对比（matrix）',
      '```kbfig\n{"type":"matrix","title":"Java 方法参数传递三种情形","cols":["形参重赋值","改对象属性","实参影响"],"rows":[{"name":"基本类型","cells":["改值副本","无对象","不受影响"]},{"name":"可变对象","cells":["改引用副本","改同一实例","变化可见"]},{"name":"String","cells":["改引用副本","final 不可变","不受影响"]}]}\n```',
      '### 9. 分层包含（layers）',
      '```kbfig\n{"type":"layers","title":"JVM 运行时数据区","groups":[{"name":"线程私有","note":"随线程生灭","items":[{"t":"程序计数器","d":"字节码行号"}]},{"name":"线程共享","note":"全进程一份","items":[{"t":"Java 堆","d":"GC 主战场","tags":["Eden 8","S0 1","S1 1","Old"]}]}]}\n```',
      '### 10. 一一对应（pairs）',
      '```kbfig\n{"type":"pairs","title":"SOLID 五原则","left":"原则","right":"解决的问题","rows":[{"l":"单一职责","r":"一个类只有一个变化理由"},{"l":"开闭原则","r":"加需求靠新增，不改已有代码"}]}\n```'
    ].join('\n\n')
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
  /* §5.1 #10：#909399（旧 EP 灰）→ --cc-text-3 */
  color: var(--cc-text-3);
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
  /* §5.1 #10：#606266（旧 EP 灰）→ --cc-text-2 */
  color: var(--cc-text-2);
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
  /* §5.1 #9：mono 字体栈提成 --font-mono（base.css）——
     原来这里、kb-editor.css 的 code、Config/EditorLab 各写一份。 */
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  /* §5.1 #10：#dcdfe6（旧 EP 灰）→ --cc-border-strong */
  border: 1px solid var(--cc-border-strong);
  border-radius: 4px;
  resize: vertical;
}
.lab__check {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  /* ⚠️ 原来的 `.lab__check.is-ok` / `.is-bad` 两条（EP 的 #f0f9eb/#67c23a 与
     #fef0f0/#f56c6c）已删（§5.1 #10）：颜色改从全站的 .kb-verdict 一族来
     （kb.css）—— 判定三档只该有一处定义。
     底色用那一族的 --kb-verdict-tint 混出来（与 ExamRunner 的 chip 同一手法）。 */
  background: color-mix(in srgb, var(--kb-verdict-color, transparent) var(--kb-verdict-tint, 12%), transparent);
}
.lab__checkTitle {
  margin: 0 0 6px;
  font-weight: 600;
}
.lab__check pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  /* §5.1 #10：#303133（旧 EP 文字色）→ --cc-text-1 */
  color: var(--cc-text-1);
  font-size: 12px;
}
</style>
