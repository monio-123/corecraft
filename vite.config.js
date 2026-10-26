import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
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
