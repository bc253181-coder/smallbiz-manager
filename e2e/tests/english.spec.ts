import { expect, test } from '@playwright/test'
import { pickProduct } from './helpers'

/**
 * 英文界面（浏览器语言英文、时区吉隆坡）：
 *   注册 → 开店默认马币 → 金额显示 RM → 建商品、开单 → 后端错误提示是英文
 *   → 各页面在英文下布局不溢出、页面上没有遗漏的中文 → 切换回中文
 */
test.use({ locale: 'en-US', timezoneId: 'Asia/Kuala_Lumpur' })

const PAGES = [
  '/', '/products', '/products/new', '/products/categories', '/products/labels', '/inventory', '/inventory/movements',
  '/sales', '/sales/new', '/customers', '/suppliers', '/purchases', '/reports', '/reports?tab=inventory', '/receivables',
  '/settings', '/settings/billing', '/settings/staff', '/settings/warehouses', '/inventory/transfers', '/terms', '/privacy',
]
const HAN = /[㐀-鿿]/

test('English UI: sign up, MYR store, sale, translated errors, layout, switch back to Chinese', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'English flow runs in the desktop project; widths are switched inside the test')
  const stamp = Date.now()
  const product = `Cable ${stamp}`

  // 后端错误提示按错误代码翻译
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Email').fill(`nobody-${stamp}@e2e.test`)
  await page.getByLabel('Password').fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText('Incorrect email or password')).toBeVisible()

  // 注册（表单校验也是英文）
  await page.getByRole('link', { name: 'Sign up free' }).click()
  await page.getByLabel('Name').fill('Mei')
  await page.getByLabel('Email').fill(`en-${stamp}@e2e.test`)
  await page.getByLabel('Password', { exact: true }).fill('E2ePassw0rd!')
  await page.getByLabel('Confirm password').fill('E2ePassw0rd!')
  await page.getByRole('button', { name: 'Sign up' }).click()
  await expect(page.getByText('Please read and accept the Terms of Service and Privacy Policy')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: 'Sign up' }).click()

  // 开店：吉隆坡时区默认马币
  await expect(page.getByRole('heading', { name: /Let's create your store/ })).toBeVisible()
  await expect(page.getByLabel('Currency')).toHaveValue('MYR')
  await page.getByLabel('Store name').fill(`Mei Accessories ${stamp}`)
  await page.getByRole('button', { name: 'Create store' }).click()
  await expect(page.getByText("Today's sales")).toBeVisible()
  await expect(page.getByText('RM 0.00').first()).toBeVisible()

  // 商品、开单
  await page.goto('/products/new')
  await page.getByLabel('Product name').fill(product)
  await page.getByLabel('Unit').fill('pc')
  await page.getByLabel('Cost price').fill('4')
  await page.getByLabel('Retail price').fill('9.90')
  await page.getByLabel('Opening stock').fill('10')
  await page.getByRole('button', { name: 'Create product' }).click()
  await expect(page.getByRole('heading', { name: product })).toBeVisible()
  await page.goto('/sales/new')
  await pickProduct(page, product)
  await page.getByLabel(`${product} quantity`).fill('2')
  await page.getByRole('button', { name: 'Complete sale' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(page.getByText('RM 19.80').first()).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('sale-en.png'), fullPage: true })

  // 各页面：不溢出、没有遗漏的中文
  const problems: string[] = []
  for (const width of [375, 1280]) {
    await page.setViewportSize({ width, height: 900 })
    for (const path of PAGES) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await expect(page.locator('h1').first()).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      if (overflow > 0) {
        problems.push(`${width}px ${path}: overflow ${overflow}px`)
        await page.screenshot({ path: testInfo.outputPath(`overflow-en-${width}-${path.replace(/[/?=]/g, '_')}.png`), fullPage: true })
      }
      const text = await page.locator('body').innerText()
      const han = text.split('\n').filter((l) => HAN.test(l) && l.trim() !== '简体中文')
      if (han.length) problems.push(`${width}px ${path}: Chinese text: ${han.slice(0, 3).join(' | ')}`)
    }
  }
  expect(problems, problems.join('\n')).toEqual([])

  // 切换回中文（保存到账号，刷新后仍是中文）
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await page.getByRole('button', { name: 'User menu' }).click()
  await page.getByRole('menuitem', { name: '简体中文' }).click()
  await expect(page.getByText('今日销售额')).toBeVisible()
  await page.reload()
  await expect(page.getByText('今日销售额')).toBeVisible()
})
