import './assets/main.css'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Milkdown / ProseMirror 接管 contenteditable div 后，div 上有 .ProseMirror class
// 但这个类不带任何基础样式，必须手动加载，否则编辑器光标 / 文本无样式
import '@milkdown/prose/view/style/prosemirror.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.use(router)
app.mount('#app')
