// 用 logo-mark.svg 生成网站图标（favicon、PWA 图标、iOS 主屏图标）：node make-icons.mjs
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const require = createRequire(path.join(here, '../../../e2e/package.json'))
const { chromium } = require('@playwright/test')
const pub = path.join(here, '../../../frontend/public')

const mark = readFileSync(path.join(here, 'logo-mark.svg'), 'utf8')
// 满版：去掉圆角（iOS / 安卓会自己裁成圆角或圆形）
const fullBleed = mark.replaceAll('rx="116"', 'rx="0"')
// maskable：图案缩到安全区（中心 80% 圆）内
const maskable = fullBleed
  .replace(/(<rect width="512" height="512" rx="0" fill="url\(#shine\)"\/>)/, '$1<g transform="translate(256 256) scale(0.74) translate(-256 -256)">')
  .replace('</svg>', '</g></svg>')

writeFileSync(path.join(pub, 'favicon.svg'), mark)

const jobs = [
  { svg: mark, size: 192, out: 'icons/icon-192.png', transparent: true },
  { svg: mark, size: 512, out: 'icons/icon-512.png', transparent: true },
  { svg: maskable, size: 512, out: 'icons/maskable-512.png' },
  { svg: fullBleed, size: 180, out: 'icons/apple-touch-icon.png' },
]

const browser = await chromium.launch({ channel: 'msedge' })
for (const j of jobs) {
  const page = await browser.newPage({ viewport: { width: j.size, height: j.size } })
  const html = `<html><body style="margin:0;background:transparent">${j.svg.replace('<svg ', `<svg width="${j.size}" height="${j.size}" `)}</body></html>`
  await page.setContent(html)
  await page.screenshot({ path: path.join(pub, j.out), omitBackground: !!j.transparent })
  console.log('✓', j.out)
  await page.close()
}
await browser.close()
