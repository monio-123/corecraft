import { fileURLToPath, URL } from 'node:url'
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { join, extname, resolve } from 'node:path'
import { gzipSync } from 'node:zlib'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 构建产物的两件收尾事（2026-11-12 冷缓存首屏诊断后加）。
 *
 * ① **首屏路由 chunk 提前并行下载**：Vite 只给入口 chunk 生成 modulepreload；
 *    `/app` 的壳与首页是**路由级动态 import**，要等入口 JS 执行 → 路由守卫 await
 *    会话接口 → 才发起请求。把这两个 chunk 也写进 index.html 的 modulepreload，
 *    浏览器解析 HTML 时就开始下，和 bootstrap 并行 ⇒ 少一整段串行等待。
 *
 * ② **静态资源预压缩**：nginx 原来没开 gzip，1.16MB 的 bootstrap chunk 原样下发。
 *    构建时给每个可压缩文件生成同名 .gz（level 9，比 nginx 动态压缩的 level 6 更小），
 *    服务器配 `gzip_static on` 直接发预压缩文件，不用每次请求现压。
 *    ⚠️ 同时仍要开 `gzip on` 兜底 —— 部署脚本若没把 .gz 拷进镜像，动态压缩还得顶上。
 *
 * 两件事都只碰产物、不碰运行时代码 ⇒ 收在 build 插件里，不新增 npm 依赖。
 */
const PRELOAD_CHUNKS = [/^AppLayout-[\w-]+\.js$/, /^AppHomeView-[\w-]+\.js$/]
const COMPRESSIBLE = new Set(['.js', '.css', '.html', '.svg', '.json'])

function releaseAssets() {
  let outDir = null
  return {
    name: 'brove-release-assets',
    apply: 'build',
    writeBundle(options) {
      outDir = options.dir ? resolve(options.dir) : null
      if (!outDir) return
      const htmlPath = join(outDir, 'index.html')
      let html
      try { html = readFileSync(htmlPath, 'utf8') } catch { return }
      // 同一个输出目录重复构建时不插第二遍
      if (html.includes('data-brove-preload')) return
      let names
      try { names = readdirSync(join(outDir, 'assets')) } catch { return }
      const preloads = names.filter((n) => PRELOAD_CHUNKS.some((re) => re.test(n)))
      if (!preloads.length) return
      const links = preloads
        .map((n) => `<link rel="modulepreload" crossorigin href="/assets/${n}">`)
        .join('\n    ')
      writeFileSync(htmlPath, html.replace('</head>', `    <!-- data-brove-preload -->\n    ${links}\n  </head>`))
    },
    closeBundle() {
      if (!outDir) return
      const walk = (dir) => {
        for (const entry of readdirSync(dir, { withFileTypes: true })) {
          const file = join(dir, entry.name)
          if (entry.isDirectory()) { walk(file); continue }
          if (!COMPRESSIBLE.has(extname(entry.name).toLowerCase())) continue
          const buf = readFileSync(file)
          if (buf.length < 1024) continue
          writeFileSync(`${file}.gz`, gzipSync(buf, { level: 9 }))
        }
      }
      walk(outDir)
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), releaseAssets()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    // demo 入口（2026-11-08）：真实组件 + 假数据，与主应用同一套组件/CSS（见 src/mockup/）
    rollupOptions: {
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        mockup: fileURLToPath(new URL('./mockup/index.html', import.meta.url))
      },
      // **稳定的第三方库单独成块**：改一行业务代码不该让 377KB 的 Vue / Element Plus
      // 换 hash 重新下载（配好强缓存后，发版只该重下入口 + 业务 chunk）。
      // ⚠️ 只收首屏必然加载的库：编辑器 / mermaid / 图布局那些大块仍交给 Vite
      // 按动态 import 自己切，合进来会把 2.6MB 图库拖进首屏。
      // ⚠️ CSS 不参与：element-plus 的样式表必须排在最前（见 bootstrap.js 的顺序注释），
      // 归到 vendor chunk 会打乱覆盖次序。
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules') || /\.(css|scss|sass|less)$/.test(id)) return
          if (/[\\/]node_modules[\\/](?:vue|@vue|vue-router|pinia|element-plus|@element-plus|@floating-ui|async-validator|dayjs|lodash-unified|normalize-wheel-es|memoize-one)[\\/]/.test(id)) {
            return 'vendor-core'
          }
        }
      }
    }
  },
  server: {
    // 监听所有网卡（0.0.0.0），局域网内的设备才能用本机 IP + 端口打开 dev server。
    //
    // **写在配置里而不是靠命令行 `--host`**：npm 会把 `--host` 当成自己的配置项吃掉
    // （`npm run dev --host` 里那个 `--host` 根本不会转发给 vite，要转发得写成
    // `npm run dev -- --host`，中间那个 `--` 少一个就静默失效）。规则放在这一处，
    // `npm run dev`、`npm run preview` 都自动生效，不用记那条命令行写法。
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
        // rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})
