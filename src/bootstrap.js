/**
 * 应用启动的**共用部分**（2026-11-11 收敛）。
 *
 * ## 为什么要有这个文件
 *
 * 生产入口 `main.js` 与 demo 入口 `mockup/main-demo.js` 的差别只有三处：
 * ① 先装假数据拦截（只有 demo 有）② hash 路由（只有 demo）③ 挂不挂登录守卫。
 *
 * 但**其余部分是整段复制的**：7 行 CSS import（**顺序有意义**）、
 * Element Plus + 中文 locale、全量图标注册、`initTheme()`、`createApp → use → mount`。
 *
 * ⇒ 抄两份的代价：改主题初始化顺序、漏注册一个图标、locale 忘了接 ——
 * **demo 能跑、生产白屏**（或反之），而两边都不报错。
 * ⇒ 现在启动骨架只有**这一份**，两个入口各只负责装自己的 router。
 */

// 样式加载顺序即覆盖优先级：组件库 → 设计 tokens → 页面骨架。
// 自己的样式必须排在 element-plus 之后 —— 同优先级时后加载的赢，
// 排在前面的话 EP 的规则会盖掉我们的覆盖（固定列表头透明就是这么来的）。
import 'element-plus/dist/index.css'
import './assets/main.css'
import './assets/admin-page.css'
import './assets/kb.css'
// 统一加载层（2026-11-13）：加载图形 / 骨架 / 忙碌态 + 动效 token。
// **排在 kb.css 之后、themes.css 之前** —— 它定义的 --kb-load-* / --kb-skel-* 基准值
// （本色）要被 themes.css 的主题覆盖块压过；反过来的话主题的加载签名永远不生效。
import './assets/kb-loading.css'
// Milkdown / ProseMirror 接管 contenteditable div 后，div 上有 .ProseMirror class
// 但这个类不带任何基础样式，必须手动加载，否则编辑器光标 / 文本无样式
import '@milkdown/prose/view/style/prosemirror.css'
// 编辑器自己的全局样式（原先是 MilkdownEditorBody.vue 里那个 531 行的全局 style 块）。
// **排在 prosemirror.css 之后**：原来它随组件 chunk 加载，相对次序就在 prosemirror.css 之后。
import './assets/kb-editor.css'
// 主题层最后加载：覆盖块要压过 base.css 的同名 token（同优先级后加载的赢）。
import './assets/themes.css'

import { createApp } from 'vue'
import ElementPlus from 'element-plus'
// 不接 locale 的话 EP 内置文案全是英文：分页是 "Total 5 / 10/page"、
// 空表是 "No Data"、消息框按钮是 "OK / Cancel"，夹在一片中文界面里很扎眼。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import { initTheme } from './utils/theme'
import { initTitleGuard } from './utils/titleGuard'

/**
 * 装应用并挂载。**router 由调用方给**（生产用 history 路由，demo 用 hash 路由）。
 *
 * @param {object} router vue-router 的实例
 */
export function bootstrapApp(router) {
  // 主题在挂载前落到 html[data-theme] 上，首屏就不闪默认色
  initTheme()
  // 触屏点按时摘掉全站原生 title 提示（hover tooltip），鼠标点一下就恢复（utils/titleGuard.js）
  initTitleGuard()

  const app = createApp(App)

  // 注册所有图标
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  app.use(ElementPlus, { locale: zhCn })
  app.use(router)
  app.mount('#app')
}
