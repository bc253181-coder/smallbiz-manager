// 从品牌图形生成 PWA 图标 PNG（用 e2e 里的 Playwright 渲染 SVG）。图形改了以后重新运行：
//   node scripts/render-icons.mjs
import { createRequire } from 'node:module'
import fs from 'node:fs'

const require = createRequire(new URL('../../e2e/package.json', import.meta.url))
const { chromium } = require('@playwright/test')

const GLYPH = `
  <path d="M8 13.5 10 8h12l2 5.5" fill="none" stroke="#fff" stroke-width="2" stroke-linejoin="round"/>
  <path d="M8 13.5h16v10.5H8z" fill="none" stroke="#fff" stroke-width="2" stroke-linejoin="round"/>
  <path d="M14 24v-5h4v5" fill="none" stroke="#fff" stroke-width="2" stroke-linejoin="round"/>`

// any：圆角方块；full：铺满（iPhone 和安卓自适应图标会自己裁圆角，图形缩小到安全区内）
const svg = (kind) =>
  kind === 'any'
    ? `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="7" fill="#2563eb"/>${GLYPH}</svg>`
    : `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" fill="#2563eb"/><g transform="translate(16 16) scale(${kind === 'maskable' ? 0.62 : 0.8}) translate(-16 -16)">${GLYPH}</g></svg>`

const OUT = [
  ['public/icons/icon-192.png', 'any', 192],
  ['public/icons/icon-512.png', 'any', 512],
  ['public/icons/maskable-512.png', 'maskable', 512],
  ['public/icons/apple-touch-icon.png', 'apple', 180],
]

fs.mkdirSync('public/icons', { recursive: true })
const browser = await chromium.launch({ channel: process.env.E2E_BROWSER_CHANNEL ?? 'msedge' })
const page = await browser.newPage()
for (const [file, kind, size] of OUT) {
  await page.setViewportSize({ width: size, height: size })
  await page.setContent(
    `<html><body style="margin:0;background:transparent">${svg(kind).replace('<svg ', `<svg width="${size}" height="${size}" `)}</body></html>`,
  )
  await page.locator('svg').screenshot({ path: file, omitBackground: true })
  console.log(file, size)
}
await browser.close()
