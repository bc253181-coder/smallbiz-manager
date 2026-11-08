import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试：针对本地运行中的前端（5173）+ 后端（8080）。
 * 先启动后端（backend: mvnw spring-boot:run）和前端（frontend: npm run dev），再执行 npm test。
 * 默认使用系统自带的 Microsoft Edge（无需另外下载浏览器）；可用 E2E_BROWSER_CHANNEL=chrome 改用 Chrome。
 */
export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge' } },
    {
      name: 'mobile',
      use: { ...devices['Pixel 7'], channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge' },
    },
  ],
})
