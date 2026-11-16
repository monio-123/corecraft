/**
 * Demo 假数据拦截（2026-11-08 架构定稿）：
 *
 * demo 环境 = 真实 Vue 组件（AppLayout / AppHomeView / …）+ 假数据。
 * 本文件在 demo 入口（main-demo.js）最顶部拦截 XMLHttpRequest：
 * 命中白名单的 /api/ 请求直接回真实抓取的库数据（mock-data.json），**不走网络**；
 * 未命中的回 {code:200, data:null}（对应组件各自的空态/吞错路径）。
 *
 * 与生产的唯一差异 = 数据来源。组件、CSS、交互逻辑与生产完全同源。
 * axios(v1) 的 XHR adapter 依赖 onloadend（在 send 之前挂好），这里直接回调它。
 */
import mockData from './mock-data.json'

const types = [
  { id: 1, label: '笔记' },
  { id: 2, label: '学习' }
]

/**
 * 考试记录的假数据（2026-11-13）。
 *
 * <p>为什么补这个：考试记录页此前在 demo 里是**空列表**（`/kp/exam/papers` 没 mock，
 * 未命中白名单的请求回 `data: null`）⇒ 空态下量不到真实行盒模型，
 * 同构行骨架（KbSkeletonRow）的高度就只能靠推算、没法跟真实行对齐。
 *
 * <p>数据形态对着 `KpPaperSummaryResp`（前端只读这些字段）：
 * id / title / kind / categoryId / questionCount / createTime /
 * lastAttemptId / lastSubmittedAt / lastScore / lastMaxScore。
 */
const mockPapers = [
  { id: 7001, title: '计算机网络体系结构阶段测', kind: 'exam', categoryId: 9006, questionCount: 12, createTime: '2026-11-08 10:24:00', lastAttemptId: 8001, lastSubmittedAt: '2026-11-08 10:41:00', lastScore: 86.5, lastMaxScore: 100 },
  { id: 7002, title: '数据链路层随堂测验', kind: 'quiz', categoryId: 9012, questionCount: 5, createTime: '2026-11-09 14:02:00', lastAttemptId: 8002, lastSubmittedAt: '2026-11-09 14:09:00', lastScore: 4, lastMaxScore: 5 },
  { id: 7003, title: '传输层知识点综合卷', kind: 'exam', categoryId: 9021, questionCount: 20, createTime: '2026-11-10 20:15:00', lastAttemptId: 8003, lastSubmittedAt: null, lastScore: null, lastMaxScore: null },
  { id: 7004, title: '每日复习（未作答）', kind: 'review', categoryId: null, questionCount: 8, createTime: '2026-11-11 07:30:00', lastAttemptId: null, lastSubmittedAt: null, lastScore: null, lastMaxScore: null },
  { id: 7005, title: '网络层路由机制阶段测', kind: 'exam', categoryId: 9030, questionCount: 15, createTime: '2026-11-12 09:48:00', lastAttemptId: 8005, lastSubmittedAt: '2026-11-12 10:05:00', lastScore: 132, lastMaxScore: 150 },
  { id: 7006, title: '会话层与表示层速测', kind: 'quiz', categoryId: 9044, questionCount: 6, createTime: '2026-11-12 16:20:00', lastAttemptId: 8006, lastSubmittedAt: null, lastScore: null, lastMaxScore: null }
]

/**
 * 生成任务的假数据（2026-11-13）。
 *
 * <p>同样是为了让 demo 的任务抽屉不是空态：任务抽屉首屏会拉
 * `GET /kp/gen-task/list`，此前没 mock ⇒ 抽屉里永远是「没有任务」，
 * 加载骨架（.tw__skel）与卡片忙碌态都没机会被看到。
 *
 * <p>形态对着 `GenTaskResp`：id / type / title / status /
 * doneCount / total / failedCount / nodeKey / nodes[]。
 */
const mockGenTasks = [
  {
    id: 6001,
    type: 'theme',
    title: '生成主题：计算机网络',
    status: 'running',
    doneCount: 7,
    total: 12,
    failedCount: 0,
    stage: 'write',
    nodeKey: 'body',
    startTime: '2026-11-12 09:10:00',
    createTime: '2026-11-12 09:10:00',
    nodes: [
      {
        key: 'outline',
        name: '生成目录',
        status: 'ok',
        done: 1,
        total: 1,
        groups: [{ key: 'skeleton', name: '骨架', done: 1, total: 1, running: 0, failed: 0 }]
      },
      { key: 'confirm', name: '确认', status: 'ok', done: 0, total: 0, groups: [] },
      {
        key: 'body',
        name: '生成正文',
        status: 'running',
        done: 6,
        total: 12,
        groups: [{ key: 'write', name: '写正文', done: 6, total: 12, running: 1, failed: 0 }]
      }
    ]
  },
  {
    id: 6002,
    type: 'classify',
    title: '待归类知识点归类',
    status: 'awaiting',
    doneCount: 18,
    total: 18,
    failedCount: 0,
    nodeKey: 'adopt',
    createTime: '2026-11-12 11:30:00',
    nodes: [
      {
        key: 'classify',
        name: '归类',
        status: 'ok',
        done: 18,
        total: 18,
        groups: [{ key: 'classify', name: '归类', done: 18, total: 18, running: 0, failed: 0 }]
      },
      // ⚠️ 节点 key 必须是 **adopt**（后端约定的那个）：GenTaskPanel 的 onConfirm
      // 靠 nodeKey === 'adopt' 判定"开采纳框"，写成别的就点不出采纳弹窗。
      // 人工节点**没有 groups** ⇒ TaskCard 按 `isManualNode` 只显示产物摘要那一行。
      {
        key: 'adopt',
        name: '确认',
        status: 'awaiting',
        done: 0,
        total: 0,
        summary: '18 条待采纳',
        groups: []
      }
    ]
  },
  {
    // ⚠️ **已暂停**（2026-11-12）：验证「暂停后按钮变成恢复」「暂停了就能删除」
    // 以及**暂停态的配色/动效**（琥珀、节点不呼吸）。
    // status 必须写 `canceled`（后端沿用这个字面量，界面上叫「已暂停」）；
    // 节点的 `paused` 是后端新下发的状态，**不能是 running** ——
    // 写 running 就会一直呼吸，看着像还在跑（这正是线上被用户抓到的那个 bug）。
    id: 6004,
    type: 'content',
    title: '知识 · 12 条',
    status: 'canceled',
    doneCount: 7,
    total: 12,
    failedCount: 0,
    stage: '',
    nodeKey: 'body',
    startTime: '2026-11-12 08:20:00',
    finishTime: '2026-11-12 08:41:23',
    createTime: '2026-11-12 08:20:00',
    nodes: [
      {
        key: 'body',
        name: '生成正文',
        status: 'paused',
        done: 7,
        total: 12,
        groups: [
          { key: 'build', name: '建结构', done: 1, total: 1, running: 0, failed: 0 },
          { key: 'write', name: '写正文', done: 6, total: 11, running: 0, failed: 0 }
        ]
      }
    ]
  },
  {
    // 「正在暂停」：执行器还在写当前这一条（不能掐断，会落半截正文）。
    // 卡片上该看到的是：琥珀的「正在暂停」+ 忙碌态按钮「正在暂停…」+「当前这条写完就停」。
    id: 6005,
    type: 'content',
    title: '知识 · 8 条',
    status: 'canceling',
    doneCount: 3,
    total: 8,
    failedCount: 0,
    stage: 'write',
    nodeKey: 'body',
    startTime: '2026-11-12 09:05:00',
    createTime: '2026-11-12 09:05:00',
    nodes: [
      {
        key: 'body',
        name: '生成正文',
        status: 'running',
        done: 3,
        total: 8,
        groups: [{ key: 'write', name: '写正文', done: 3, total: 8, running: 1, failed: 0 }]
      }
    ]
  },
  {
    id: 6003,
    type: 'exam',
    title: '生成考试：数据链路层',
    status: 'failed',
    doneCount: 10,
    total: 10,
    failedCount: 3,
    nodeKey: 'exam',
    createTime: '2026-11-11 20:05:00',
    error: '3 条失败：知识点ID不能为空',
    nodes: [
      {
        key: 'exam',
        name: '出题',
        status: 'failed',
        done: 7,
        total: 10,
        groups: [{ key: 'exam', name: '出题', done: 7, total: 10, running: 0, failed: 3 }]
      }
    ]
  }
]

/**
 * 配置树的假数据（2026-11-13）。
 *
 * <p>设置弹窗（SettingsDialog）读 `GET /sys/config/node/list` 拿"有哪些项、属于哪一组"，
 * 此前没 mock ⇒ 面板打开是空的，**加载中 / 加载失败两个状态都看不到**。
 *
 * <p>这里只给界面偏好（ui 命名空间）的四组 + 叶子项，
 * 叶子 configKey 必须与 utils/uiPrefs.js 的 PREF_SPECS 对得上
 * （对不上的键会被 toItem 过滤掉 ⇒ 那一行不显示）。
 */
const mockConfigNodes = [
  { id: 1, parentId: 0, configKey: 'ui', code: 'ui', name: '界面偏好' },
  { id: 11, parentId: 1, configKey: 'ui.side', code: 'side', name: '侧边栏' },
  { id: 12, parentId: 1, configKey: 'ui.home', code: 'home', name: '首页' },
  { id: 13, parentId: 1, configKey: 'ui.learn', code: 'learn', name: '知识点' },
  { id: 14, parentId: 1, configKey: 'ui.detail', code: 'detail', name: '详情' },
  { id: 111, parentId: 11, configKey: 'ui.side.sideWidth', code: 'sideWidth', name: '侧边栏宽度' },
  { id: 121, parentId: 12, configKey: 'ui.home.homePadX', code: 'homePadX', name: '首页左右内距' },
  { id: 131, parentId: 13, configKey: 'ui.learn.learnGridGap', code: 'learnGridGap', name: '卡片间距' },
  { id: 141, parentId: 14, configKey: 'ui.detail.editorFs', code: 'editorFs', name: '正文字号' }
]

/**
 * AI 能力的假数据（2026-11-13）。
 *
 * <p>设置弹窗的「AI 能力」右区（AiSettingsPanel）要 `GET /ai/config` + `GET /ai/endpoint`，
 * 没 mock 时开关永远是关、列表永远空 —— 选中行 /「未配全」角标 / 行操作全看不到。
 *
 * <p>端点形态对着 `AiEndpoint`：id / name / vendor / protocol / baseUrl /
 * model / apiKey / custom / ready。三条正好覆盖三种脸：当前选中 / 普通 / 未配全。
 */
const mockAiConfig = { enabled: true, currentEndpointId: 3001 }
const mockAiEndpoints = [
  { id: 3001, name: 'DeepSeek', vendor: 'deepseek', protocol: 'openai', baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: 'sk-demo', custom: '', ready: true },
  { id: 3002, name: '小米 MiMo', vendor: 'xiaomi', protocol: 'openai', baseUrl: 'https://api.xiaomi.com', model: 'mimo-v2.5', apiKey: 'sk-demo', custom: '', ready: true },
  { id: 3003, name: '其他中转', vendor: 'other', protocol: 'openai', baseUrl: '', model: '', apiKey: '', custom: '', ready: false }
]
const mockAiVendors = [
  {
    code: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com',
    models: [{ id: 'deepseek-chat', label: 'DeepSeek Chat' }, { id: 'deepseek-reasoner', label: 'DeepSeek Reasoner' }]
  },
  {
    code: 'xiaomi', name: '小米 MiMo', baseUrl: 'https://api.xiaomi.com',
    models: [{ id: 'mimo-v2.5', label: 'MiMo v2.5' }]
  }
]

/**
 * 随堂测验锚与回看的假数据（2026-11-13，TODO-exam-lifecycle 落地配套）。
 *
 * <p>demo 里点「随堂测验」走的是**恢复/回看**分支（真实出卷要打模型，demo 没有）：
 * 锚上有一张考完的卷（attempt 8007 = completed），能看回看 + 重做/重新生成的按钮。
 * 重做（replay）拿到的 8008+ 是 answering ⇒ 从头作答，也一并能看到。
 */
const QUIZ_PAPER_ID = 7007
const QUIZ_ATTEMPT_ID = 8007

function mockQuizItems() {
  return [
    {
      questionNo: 1,
      questionId: 70071,
      type: 'single',
      maxScore: 3,
      questionPayload: {
        stem: '数据链路层最基本的功能是什么？',
        options: [{ label: 'A', text: '封装成帧、透明传输、差错检测' }, { label: 'B', text: '端到端的可靠传输' }, { label: 'C', text: '路由选择' }, { label: 'D', text: '数据加密与压缩' }],
        answer: 'A',
        answerPoints: [],
        isOpen: false
      },
      answerPayload: { picked: ['A'], typed: '', judge: '' },
      verdict: '对',
      explanation: '成帧、透明传输、差错检测是数据链路层的三大基本功能；可靠传输是传输层的事，路由是网络层的事。'
    },
    {
      questionNo: 2,
      questionId: 70072,
      type: 'multiple',
      maxScore: 3,
      questionPayload: {
        stem: '下列哪些属于流量控制的手段？',
        options: [{ label: 'A', text: '停等协议' }, { label: 'B', text: '滑动窗口' }, { label: 'C', text: 'TLS 握手' }, { label: 'D', text: '背压（backpressure）' }],
        answer: 'ABD',
        answerPoints: [],
        isOpen: false
      },
      answerPayload: { picked: ['A', 'B'], typed: '', judge: '' },
      verdict: '部分对',
      explanation: '停等与滑动窗口是链路层经典手段，背压是通用流控思想；TLS 握手是建立安全会话，不是流量控制。'
    },
    {
      questionNo: 3,
      questionId: 70073,
      type: 'concept',
      maxScore: 3,
      questionPayload: {
        stem: '用自己的话说说：为什么需要 MAC 地址？',
        options: [],
        answer: '',
        answerPoints: ['标识同一链路上的网络接口', '与 IP 地址分工：IP 管跨网寻址，MAC 管本链路交付'],
        isOpen: true
      },
      answerPayload: { picked: [], typed: '用来标识网卡，让同一根线上的帧知道发给谁。', judge: '' },
      verdict: '部分对',
      explanation: '答到了"标识接口、本链路交付"这一点；没提到与 IP 的分工（IP 管跨网、MAC 管本段）。'
    }
  ]
}

function hit(url) {
  const u = String(url || '')
  if (u.includes('/sys/user/me/menu-tree')) {
    return [{
      id: 1,
      type: 'MENU',
      path: '/app',
      code: 'app:home',
      meta: JSON.stringify({ title: '首页', shell: 'app', icon: 'HomeFilled' }),
      children: []
    }]
  }
  if (u.includes('/sys/user/me/profile')) {
    // 字段名照生产的形状（nickname/username 小写）——原来写成 camelCase，
    // demo 里用户名一直是空的（头像兜底字恒为 'U' 也是它害的）
    return { id: 1, username: 'mo', nickname: 'mo', avatar: null, roles: [] }
  }
  if (u.includes('/sys/user/me/permissions')) return { permissions: [] }
  if (u.includes('/kp/category/list')) return mockData.categories
  if (u.includes('/kp/topic/index')) return mockData.topics
  if (u.includes('/kp/topic/list')) {
    // 与生产 KpTopicQuery 同形的筛选：categoryIds（逗号串）/ uncategorized / typeId / keyword+scope / 服务端分页
    function q(k) {
      var m = u.match(new RegExp('[?&]' + k + '=([^&]*)'))
      return m ? decodeURIComponent(m[1]) : null
    }
    var kw = q('keyword') || ''
    var scope = q('scope') || 'both'
    var ids = q('categoryIds')
    var uncategorized = q('uncategorized') === 'true'
    var typeId = q('typeId')
    var list = mockData.topics.filter(function (t) {
      if (ids && ids.split(',').indexOf(String(t.categoryId)) < 0) return false
      if (uncategorized && t.categoryId != null) return false
      if (typeId && String(t.typeId) !== typeId) return false
      if (kw) {
        var inTitle = t.title.indexOf(kw) >= 0
        var inContent = (t.content || '').indexOf(kw) >= 0
        if (scope === 'title' ? !inTitle : scope === 'content' ? !inContent : (!inTitle && !inContent)) return false
      }
      return true
    })
    var page = Number(q('page')) || 1
    var size = Number(q('size')) || 20
    return { records: list.slice((page - 1) * size, page * size), total: list.length }
  }
  if (u.includes('/sys/config/node/list')) return mockConfigNodes
  if (u.includes('/sys/config/mine')) return { values: {}, defaults: {} }
  // AI 能力（设置弹窗右区）。⚠️ 顺序要紧：test / refresh 都含前缀，得排在裸前缀前面。
  if (u.includes('/ai/endpoint/test')) return { ok: true, latencyMs: 820, model: 'deepseek-chat', message: '' }
  if (u.includes('/ai/vendor/models/refresh')) return {}
  if (u.includes('/ai/config/current')) return {}
  if (u.includes('/ai/config')) return mockAiConfig
  if (u.includes('/ai/endpoint')) return mockAiEndpoints
  if (u.includes('/ai/vendor')) return mockAiVendors
  // 单条掌握度档位（目录树知识点行的圆点染色 + 进度条数据源）
  if (u.includes('/kp/learning/levels')) {
    return [
      { topicId: 101, level: '熟练', mastery: 91 },
      { topicId: 103, level: '基本掌握', mastery: 72 },
      { topicId: 105, level: '待巩固', mastery: 42 },
      { topicId: 106, level: '稳定掌握', mastery: 98 }
    ]
  }
  // 随堂测验的锚（demo 走恢复/回看分支，见 mockQuizItems 注释）
  if (u.includes('/kp/exam/quiz-current')) {
    return {
      paper: {
        id: QUIZ_PAPER_ID, kind: 'quiz', categoryId: null,
        title: '随堂测验 · 数据链路层基础', status: 'ready',
        questionCount: 3,
        lastAttemptId: QUIZ_ATTEMPT_ID,
        lastScore: 7.5, lastMaxScore: 9,
        lastSubmittedAt: '2026-11-13 09:41:00',
        createTime: '2026-11-12 09:00:00'
      },
      attempt: { id: QUIZ_ATTEMPT_ID, status: 'completed' }
    }
  }
  // 重做 = 同卷新 attempt（demo 发一个新 id，回看按 answering 给空作答）
  var replay = u.match(/\/kp\/exam\/attempts\/(\d+)\/replay/)
  if (replay) return Number(replay[1]) + 1
  // 删除（软删）。⚠️ /kp/exam/papers/{id} 的 GET 详情在 demo 里没有调用者
  // （不出真卷），这个模式只会被 DELETE 命中；demo 静态列表不会真少一条
  var delPaper = u.match(/\/kp\/exam\/papers\/(\d+)$/)
  if (delPaper) return {}
  // 给「有卷没作答过」的兜底分支补建 attempt 用
  var newAttempt = u.match(/\/kp\/exam\/papers\/(\d+)\/attempts/)
  if (newAttempt) return QUIZ_ATTEMPT_ID + 100
  // 逐题回看：8007 = 考完那张；其它（重做出来的）= answering 空作答
  var review = u.match(/\/kp\/exam\/attempts\/(\d+)(\/answers)?/)
  if (review) {
    var quizDone = review[1] === String(QUIZ_ATTEMPT_ID)
    return {
      status: quizDone ? 'completed' : 'answering',
      items: mockQuizItems().map(function (it) {
        return quizDone ? it : Object.assign({}, it, { answerPayload: { picked: [], typed: '', judge: '' }, verdict: null, explanation: '' })
      })
    }
  }
  if (/\/kp\/gen-task\/list(\?|$)/.test(u)) {
    return { records: mockGenTasks, total: mockGenTasks.length }
  }
  var taskDetail = u.match(/\/kp\/gen-task\/(\d+)$/)
  if (taskDetail) {
    var g = mockGenTasks.find(function (t) { return String(t.id) === taskDetail[1] })
    return g || null
  }
  if (/\/kp\/exam\/papers(\?|$)/.test(u)) {
    // 考试记录列表（摘要）。筛选口径照前端 buildParams：kind / categoryIds / 时间区间 / 分页。
    function q(k) {
      var m = u.match(new RegExp('[?&]' + k + '=([^&]*)'))
      return m ? decodeURIComponent(m[1]) : null
    }
    var kind = q('kind')
    var ids = q('categoryIds')
    var from = q('createdFrom')
    var to = q('createdTo')
    var unfinished = q('unfinished') === 'true'
    var list = mockPapers.filter(function (p) {
      if (kind && p.kind !== kind) return false
      if (ids && ids.split(',').indexOf(String(p.categoryId)) < 0) return false
      if (from && p.createTime < from) return false
      if (to && p.createTime > to) return false
      // 「有作答还没交」：最新一次 attempt 建了、但没交（lastSubmittedAt 空）
      if (unfinished && !(p.lastAttemptId && !p.lastSubmittedAt)) return false
      return true
    })
    var page = Number(q('page')) || 1
    var size = Number(q('size')) || 20
    return { records: list.slice((page - 1) * size, page * size), total: list.length }
  }
  var single = u.match(/\/kp\/topic\/(\d+)$/);
  if (single) {
    var t = mockData.topics.find(function (x) { return String(x.id) === single[1]; });
    return t || null;
  }
  if (u.includes('/dict/item/list')) return types
  if (u.includes('/kp/tag/list')) return []
  return null
}

export function installDemoApi() {
  const rawOpen = XMLHttpRequest.prototype.open
  const rawSend = XMLHttpRequest.prototype.send
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__demoUrl = String(url || '')
    return rawOpen.apply(this, arguments)
  }
  XMLHttpRequest.prototype.send = function () {
    const url = this.__demoUrl || ''
    if (!url.includes('/api/')) return rawSend.apply(this, arguments)
    const data = hit(url)
    const body = JSON.stringify({ code: 200, message: 'ok', data })
    Object.defineProperty(this, 'readyState', { value: 4 })
    Object.defineProperty(this, 'status', { value: 200 })
    Object.defineProperty(this, 'responseText', { value: body })
    Object.defineProperty(this, 'response', { value: body })
    Object.defineProperty(this, 'getAllResponseHeaders', { value: () => 'content-type: application/json' })
    const self = this
    setTimeout(function () {
      if (typeof self.onreadystatechange === 'function') self.onreadystatechange()
      if (typeof self.onloadend === 'function') self.onloadend()
    }, 5)
  }
  console.info('[DEMO] 假数据拦截已安装 —— 界面为真实组件 + 真实库快照，不经过后台')
}
