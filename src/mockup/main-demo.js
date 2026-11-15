/**
 * Demo 入口（2026-11-08 架构定稿）：
 * 与生产 main.js 的唯一差异 = ① 先装假数据拦截（mock-api）② hash 路由（不依赖服务器路径）
 * ③ 不挂登录守卫（demo 无鉴权）。组件、CSS、主题、交互逻辑与生产完全同源。
 *
 * <p>⚠️ **启动骨架共用生产的那一份**（`bootstrap.js`）：CSS 加载顺序、主题初始化、
 * 图标注册、Element Plus locale、mount 全走同一段 ⇒ demo 与生产不会走偏
 * （以前各抄一遍：改一边忘了另一边 ⇒ "demo 能跑、生产白屏"，两边都不报错）。
 *
 * 访问：/mockup/index.html#/app（首页）—— 其它页面（树/卡片/详情）后续逐页接进同一架构。
 */
import { installDemoApi } from './mock-api.js'

// 最先装假数据拦截：组件链路里任何接口请求都不出网
installDemoApi()

import { createRouter, createWebHashHistory } from 'vue-router'
import { routes } from '../router'
import { bootstrapApp } from '../bootstrap'

// demo 的 router：hash 路由（不依赖服务器路径；#后面的部分才是路由）+ 不挂登录守卫
const router = createRouter({ history: createWebHashHistory(), routes })

bootstrapApp(router)

// 进首页
window.location.hash = '#/app'
