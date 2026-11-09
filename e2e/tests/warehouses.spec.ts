import { expect, test } from '@playwright/test'
import { createActivationCode, go, pickProduct } from './helpers'

/**
 * 多仓库（专业版）。商品主仓库初始库存 50：
 *   新建「门店」→ 主仓库调 20 到门店（主 30、门店 20）
 *   → 门店开单 3（门店 17、总数 47）→ 门店再卖 18 被拒（只有 17）
 *   → 商品详情各仓库库存：主仓库 30、门店 17；库存页切到门店显示 17
 */
test('专业版：新建仓库 → 调拨 → 按仓库开单 → 仓库库存不足提示', async ({ page, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '多仓库流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()
  const product = `E2E 仓库商品 ${stamp}`
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `wh-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 仓库', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 多仓店 ${stamp}`, businessType: 'RETAIL' },
  })
  expect(store.status()).toBe(201)
  const auth = { Authorization: `Bearer ${(await store.json()).accessToken}` }
  expect((await page.request.post('/api/v1/billing/redeem', { headers: auth, data: { code: await createActivationCode(baseURL!, 30, 'PRO') } })).status()).toBe(200)
  const p = await page.request.post('/api/v1/products', {
    headers: auth,
    data: { name: product, unit: '个', costPrice: '5', retailPrice: '10', lowStockThreshold: 0, initialStock: 50 },
  })
  expect(p.status()).toBe(201)
  const productId = (await p.json()).id

  // 只有一个仓库时，开单页没有仓库选择
  await go(page, '/sales/new')
  await expect(page.getByLabel('出货仓库')).toHaveCount(0)

  // 新建「门店」
  await go(page, '/settings/warehouses')
  await expect(page.getByText('已启用 1 / 5')).toBeVisible()
  await page.getByRole('button', { name: '新增仓库' }).click()
  await page.getByLabel('仓库名称').fill('门店')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('已新增仓库「门店」')).toBeVisible()
  await expect(page.getByText('已启用 2 / 5')).toBeVisible()

  // 主仓库 → 门店 20
  await go(page, '/inventory')
  await page.getByRole('link', { name: '调拨' }).click()
  await page.getByRole('link', { name: '新建调拨' }).click()
  await expect(page.getByLabel('调出仓库')).toHaveValue(/\d+/)
  await expect(page.getByLabel('调入仓库').locator('option:checked')).toHaveText('门店')
  await pickProduct(page, product)
  await expect(page.getByText('调出仓库现有 50 个')).toBeVisible()
  await page.getByLabel(`${product} 调拨数量`).fill('20')
  await page.getByRole('button', { name: '确认调拨' }).click()
  await expect(page.getByRole('heading', { name: /TF-\d{8}-\d{4}/ })).toBeVisible()
  await expect(page.getByText('共 20 件')).toBeVisible()

  // 门店开单 3
  await go(page, '/sales/new')
  await page.getByLabel('出货仓库').selectOption({ label: '门店' })
  await pickProduct(page, product)
  await expect(page.getByText('该仓库库存 20 个')).toBeVisible()
  await page.getByLabel(`${product} 数量`).fill('3')
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(page.getByText('出货仓库')).toBeVisible()

  // 门店只剩 17：卖 18 被拒（上次用的仓库被记住）
  await go(page, '/sales/new')
  await expect(page.getByLabel('出货仓库').locator('option:checked')).toHaveText('门店')
  await pickProduct(page, product)
  await page.getByLabel(`${product} 数量`).fill('18')
  await expect(page.getByText('（库存不足）')).toBeVisible()
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByText(/该仓库库存不足，当前只有 17 个/)).toBeVisible()

  // 商品详情：各仓库库存
  await go(page, `/products/${productId}`)
  const card = page.locator('div.rounded-xl').filter({ has: page.getByRole('heading', { name: '各仓库库存' }) })
  await expect(card.getByRole('listitem').filter({ hasText: '主仓库' })).toContainText('30')
  await expect(card.getByRole('listitem').filter({ hasText: '门店' })).toContainText('17')

  // 库存页切到门店
  await go(page, '/inventory')
  await page.getByRole('tab', { name: '门店' }).click()
  await page.getByLabel('搜索商品').fill(product)
  await expect(page.getByRole('listitem').filter({ hasText: product })).toContainText('（共 47）')
  await page.screenshot({ path: testInfo.outputPath('inventory-store.png'), fullPage: true })
})
