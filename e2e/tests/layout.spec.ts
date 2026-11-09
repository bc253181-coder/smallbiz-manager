import { expect, test } from '@playwright/test'

/**
 * 全站布局检查：新店铺（各页面都是空状态）在 375 / 768 / 1280 宽度下
 * - 页面不出现横向滚动条
 * - 没有未捕获的脚本错误
 * - 没有出现「加载失败」错误状态
 */
const PAGES = [
  '/',
  '/products',
  '/products/new',
  '/products/categories',
  '/inventory',
  '/inventory/movements',
  '/sales',
  '/sales/new',
  '/customers',
  '/suppliers',
  '/purchases',
  '/reports',
  '/reports?tab=inventory',
  '/receivables',
  '/settings',
  '/settings/billing',
  '/terms',
  '/privacy',
]
const WIDTHS = [375, 768, 1280]

test.describe.configure({ mode: 'serial' })

test('各页面在手机、平板、桌面宽度下布局正常', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '宽度在用例内切换，只需在一个项目中运行')

  // 通过接口注册并开店（与浏览器共享 Cookie，页面刷新后自动登录）
  const stamp = Date.now()
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `layout-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: '布局检查', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const { accessToken } = await reg.json()
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { name: `布局检查店 ${stamp}`, businessType: 'RETAIL' },
  })
  expect(store.status()).toBe(201)

  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(`${page.url()}: ${e.message}`))

  const problems: string[] = []
  for (const width of WIDTHS) {
    await page.setViewportSize({ width, height: 900 })
    for (const path of PAGES) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await expect(page.locator('h1').first()).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      if (overflow > 0) {
        problems.push(`${width}px ${path}: 横向溢出 ${overflow}px`)
        await page.screenshot({ path: testInfo.outputPath(`overflow-${width}-${path.replace(/[/?=]/g, '_')}.png`), fullPage: true })
      }
      if (await page.getByText('加载失败').count()) problems.push(`${width}px ${path}: 出现「加载失败」`)
    }
  }
  expect(problems, problems.join('\n')).toEqual([])
  expect(errors, errors.join('\n')).toEqual([])
})
