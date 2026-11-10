// 把 src 下的 HTML 渲染成 PNG：node render.mjs [文件名...]
// 依赖 e2e 目录里安装的 Playwright（使用本机 Edge）
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const require = createRequire(path.join(here, '../../../e2e/package.json'))
const { chromium } = require('@playwright/test')

const jobs = {
  '1-cover.html': { out: '1-封面.png', w: 1242, h: 1660 },
  '2-features.html': { out: '2-功能.png', w: 1242, h: 1660 },
  '3-price.html': { out: '3-价格与开通.png', w: 1242, h: 1660 },
  'logo-avatar.html': { out: 'logo-头像.png', w: 1080, h: 1080 },
  'logo-wide.html': { out: 'logo-横版.png', w: 1600, h: 560, transparent: true },
  'logo-mark.html': { out: 'logo-图标.png', w: 1024, h: 1024, transparent: true },
}

const pick = process.argv.slice(2)
const browser = await chromium.launch({ channel: 'msedge' })
for (const [file, job] of Object.entries(jobs)) {
  if (pick.length && !pick.includes(file)) continue
  const page = await browser.newPage({ viewport: { width: job.w, height: job.h }, deviceScaleFactor: 1 })
  await page.goto(pathToFileURL(path.join(here, file)).href)
  await page.evaluate(() => document.fonts.ready)
  await page.waitForTimeout(300)
  const out = path.join(here, '..', job.out)
  await page.screenshot({ path: out, omitBackground: !!job.transparent })
  console.log('✓', job.out)
  await page.close()
}
await browser.close()
