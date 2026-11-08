import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
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
