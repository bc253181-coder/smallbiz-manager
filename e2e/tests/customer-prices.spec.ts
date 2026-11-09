import { expect, test } from '@playwright/test'
import { createActivationCode, go, pickProduct } from './helpers'

/**
 * 客户协议价（专业版）。商品零售 10、批发 9、成本 5；客户甲、乙都是批发客户。
 *   给甲设协议价 7.5 → 甲的协议价列表显示「比批发价低 16.7%」
 *   → 为甲开单：单价自动 7.50、标「协议价」→ 销售单详情显示来源「协议价」
 *   → 开单先选乙：单价 9.00；换成甲：变为 7.50
 *   → 商品详情能看到甲的协议价
 */
test('专业版：设置协议价 → 开单自动带出 → 换客户重新取价', async ({ page, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '协议价流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()
  const product = `E2E 协议价商品 ${stamp}`
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `cp-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 协议价', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 协议价店 ${stamp}`, businessType: 'WHOLESALE' },
  })
  expect(store.status()).toBe(201)
  const auth = { Authorization: `Bearer ${(await store.json()).accessToken}` }
  const redeem = await page.request.post('/api/v1/billing/redeem', { headers: auth, data: { code: await createActivationCode(baseURL!, 30, 'PRO') } })
  expect(redeem.status(), await redeem.text()).toBe(200)
  const p = await page.request.post('/api/v1/products', {
    headers: auth,
    data: { name: product, unit: '个', costPrice: '5', retailPrice: '10', wholesalePrice: '9', lowStockThreshold: 0, initialStock: 100 },
  })
  expect(p.status()).toBe(201)
  const productId = (await p.json()).id
  const newCustomer = async (name: string) => {
    const r = await page.request.post('/api/v1/customers', {
      headers: auth,
      data: { name, customerType: 'BUSINESS', priceTier: 'WHOLESALE' },
    })
    expect(r.status()).toBe(201)
    return (await r.json()).id as number
  }
  const jia = await newCustomer(`E2E 客户甲 ${stamp}`)
  await newCustomer(`E2E 客户乙 ${stamp}`)

  // 给甲设置协议价
  await go(page, `/customers/${jia}`)
  await expect(page.getByText('还没有协议价')).toBeVisible()
  await pickProduct(page, product)
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel(/协议价/)).toHaveValue('9.00')
  await dialog.getByLabel(/协议价/).fill('7.5')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText(`已设置「${product}」的协议价 ¥7.50`)).toBeVisible()
  await expect(page.getByText('比批发价低 16.7%')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('customer-prices.png'), fullPage: true })

  // 为甲开单：自动协议价
  await go(page, `/sales/new?customerId=${jia}`)
  await pickProduct(page, product)
  await expect(page.getByLabel(`${product} 单价`)).toHaveValue('7.50')
  await expect(page.getByText('协议价', { exact: true })).toBeVisible()
  await page.getByLabel(`${product} 数量`).fill('2')
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: product })).toContainText('协议价')
  await expect(page.getByRole('row').filter({ hasText: product })).toContainText('¥15.00')

  // 先选乙：批发价；换成甲：协议价
  await go(page, '/sales/new')
  await page.getByLabel('客户').selectOption({ label: `E2E 客户乙 ${stamp}（批发）` })
  await pickProduct(page, product)
  await expect(page.getByLabel(`${product} 单价`)).toHaveValue('9.00')
  await expect(page.getByText('协议价', { exact: true })).toHaveCount(0)
  await page.getByLabel('客户').selectOption({ label: `E2E 客户甲 ${stamp}（批发）` })
  await expect(page.getByLabel(`${product} 单价`)).toHaveValue('7.50')
  await expect(page.getByText('协议价', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('sale-contract.png') })

  // 商品详情
  await go(page, `/products/${productId}`)
  await expect(page.getByRole('heading', { name: '客户协议价' })).toBeVisible()
  await expect(page.getByRole('link', { name: new RegExp(`E2E 客户甲 ${stamp}`) })).toBeVisible()
})
