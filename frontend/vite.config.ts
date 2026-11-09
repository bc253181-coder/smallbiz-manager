import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    // 可安装到手机主屏幕（PWA）。只缓存页面程序和图标，接口数据一律不缓存（多人共用手机、切换店铺时不会残留数据）
    VitePWA({
      registerType: 'prompt', // 新版本下载好后提示用户点击更新，不自动刷新（避免打断正在开的单）
      injectRegister: false, // 在 PwaLayer 里注册
      includeAssets: ['favicon.svg', 'icons/apple-touch-icon.png'],
      manifest: {
        name: 'SmallBiz Manager',
        short_name: 'SmallBiz',
        description: '中小商户在线经营管理：商品、库存、采购、销售 · Inventory and sales for small businesses',
        lang: 'zh-CN',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        orientation: 'portrait',
        theme_color: '#2563eb',
        background_color: '#f8fafc',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        // 扫码识别库较大且只在 iPhone 扫码时才用：不预先下载
        globIgnores: ['**/zxing-*.js'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//, /^\/uploads\//, /^\/actuator\//],
        cleanupOutdatedCaches: true,
        // 不配置 runtimeCaching：/api 请求直接走网络，断网时失败而不是返回旧数据
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rolldownOptions: {
      output: {
        // 第三方库单独打包：业务代码更新时，浏览器仍可使用缓存的库文件
        codeSplitting: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/ },
            // 摄像头扫码识别库：单独命名，便于不放进 PWA 预缓存
            { name: 'zxing', test: /node_modules[\\/]@zxing[\\/]/ },
            // 只列首屏就需要的库；图表库（recharts 及其依赖）留给用到它的页面按需加载
            {
              name: 'vendor',
              test: /node_modules[\\/](@tanstack|axios|radix-ui|@radix-ui|@floating-ui|react-hook-form|@hookform|zod|sonner|lucide-react|tailwind-merge|clsx|class-variance-authority)[\\/]/,
            },
          ],
        },
      },
    },
  },
  server: {
    port: 5173,
    // 开发时把 /api 转发到后端，浏览器看到的是同一个域名（Cookie 正常工作）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
