/**
 * 生产入口。
 *
 * <p>启动骨架（CSS 加载顺序 / 主题 / 图标注册 / Element Plus / mount）在
 * `bootstrap.js` 一份里，demo 入口共用同一份 ⇒ 两边不会走偏
 * （以前各抄一遍，改一边忘了另一边就出现"demo 能跑、生产白屏"）。
 *
 * <p>本文件只负责：**装生产自己的 router**。
 */
import router from './router'
import { bootstrapApp } from './bootstrap'

bootstrapApp(router)
