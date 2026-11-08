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
