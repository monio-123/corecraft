// 样式加载顺序即覆盖优先级：组件库 → 设计 tokens → 页面骨架。
// 自己的样式必须排在 element-plus 之后 —— 同优先级时后加载的赢，
// 排在前面的话 EP 的规则会盖掉我们的覆盖（固定列表头透明就是这么来的）。
import 'element-plus/dist/index.css'
import './assets/main.css'
import './assets/admin-page.css'
// Milkdown / ProseMirror 接管 contenteditable div 后，div 上有 .ProseMirror class
// 但这个类不带任何基础样式，必须手动加载，否则编辑器光标 / 文本无样式
import '@milkdown/prose/view/style/prosemirror.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
// 不接 locale 的话 EP 内置文案全是英文：分页是 "Total 5 / 10/page"、
// 空表是 "No Data"、消息框按钮是 "OK / Cancel"，夹在一片中文界面里很扎眼。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
