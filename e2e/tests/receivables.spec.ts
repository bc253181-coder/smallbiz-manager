import { expect, test, type Page } from '@playwright/test'
import { createActivationCode, go, pickProduct, statCard } from './helpers'

/**
 * 应收账款（专业版）完整流程，金额全部手算：
 *   商品零售价 ¥10
 *   销售 1：10 件 = ¥100，本次实收 ¥30 → 欠 ¥70
 *   销售 2：4 件 = ¥40，本次实收 ¥0 → 欠 ¥40；总欠款 ¥110
 *   收款 ¥80（先欠先还）→ 销售 1 收 70（付清），销售 2 收 10（还欠 30）；总欠款 ¥30，本月收回 ¥80
 *   对账单：期初 0 + 新增欠款 110 − 收款 80 = 期末 30
 *   作废这笔收款 → 总欠款回到 ¥110
 */
test('专业版：赊账 → 收款 → 对账单 → 作废收款', async ({ page, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '收款弹窗等流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()
  const product = `E2E 赊账商品 ${stamp}`
  const customer = `E2E 赊账客户 ${stamp}`

  // 注册并开店
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `ar-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: '赊账测试', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 赊账店 ${stamp}`, businessType: 'WHOLESALE' },
  })
  expect(store.status()).toBe(201)

  // 免费版：应收账款是专业版功能
  await go(page, '/receivables')
  await expect(page.getByRole('heading', { name: /是高级会员功能/ })).toBeVisible()

  // 兑换专业版激活码
  await go(page, '/settings/billing')
  await page.getByLabel('激活码').fill(await createActivationCode(baseURL!, 30, 'PRO'))
  await page.getByRole('button', { name: '兑换' }).click()
  await expect(page.getByText(/已开通专业版/)).toBeVisible()

  // 商品
  await go(page, '/products/new')
  await page.getByLabel('商品名称').fill(product)
  await page.getByLabel('单位').fill('个')
  await page.getByLabel('进货成本').fill('5')
  await page.getByLabel('零售价').fill('10')
  await page.getByLabel('初始库存').fill('100')
  await page.getByRole('button', { name: '创建商品' }).click()
  await expect(page.getByRole('heading', { name: product })).toBeVisible()

  // 销售 1：从开单页新增客户，赊账
  await go(page, '/sales/new')
  await page.getByRole('button', { name: '新增客户' }).click()
  await page.getByLabel('客户名称').fill(customer)
  await page.getByRole('button', { name: '添加客户' }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await creditSale(page, product, '10', '30')
  await expect(page.getByText('部分付款').first()).toBeVisible()
  await expect(page.getByRole('row', { name: /未收/ })).toContainText('¥70.00')
  const customerHref = await page.getByRole('link', { name: customer }).getAttribute('href')

  // 销售 2：全部赊账
  await go(page, '/sales/new')
  await page.getByLabel('客户').selectOption({ label: customer })
  await creditSale(page, product, '4', '0')
  await expect(page.getByText('未付款').first()).toBeVisible()

  // 应收账款页
  await go(page, '/receivables')
  await expect(statCard(page, '总欠款')).toContainText('¥110.00')
  const row = page.getByRole('row').filter({ hasText: customer })
  await expect(row).toContainText('¥110.00')
  await expect(row).toContainText('2 张')

  // 收款 80：预览先欠先还
  await row.getByRole('button', { name: '收款' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('收款金额')).toHaveValue('110.00')
  await dialog.getByLabel('收款金额').fill('80')
  await expect(dialog.getByText('将冲抵以下销售单')).toBeVisible()
  await expect(dialog.getByRole('listitem')).toHaveCount(2)
  await expect(dialog.getByRole('listitem').nth(0)).toContainText('¥70.00')
  await expect(dialog.getByRole('listitem').nth(1)).toContainText('¥10.00')
  await expect(dialog.getByRole('listitem').nth(1)).toContainText('仍欠 ¥30.00')
  await dialog.getByRole('button', { name: '确认收款' }).click()
  await expect(page.getByText(/已收款 ¥80.00/)).toBeVisible()
  await expect(statCard(page, '总欠款')).toContainText('¥30.00')
  await expect(statCard(page, '本月已收回')).toContainText('¥80.00')
  await page.screenshot({ path: testInfo.outputPath('receivables.png'), fullPage: true })

  // 对账单
  await go(page, `${customerHref}/statement`)
  await expect(statCard(page, '期初欠款')).toContainText('¥0.00')
  await expect(statCard(page, '本期新增欠款')).toContainText('¥110.00')
  await expect(statCard(page, '本期收款')).toContainText('¥80.00')
  await expect(statCard(page, '期末欠款')).toContainText('¥30.00')
  await page.screenshot({ path: testInfo.outputPath('statement.png'), fullPage: true })

  // 仪表盘提醒
  await go(page, '/')
  await expect(page.getByRole('link', { name: /应收账款 ¥30.00/ })).toBeVisible()

  // 作废收款 → 欠款恢复
  await go(page, '/receivables/payments')
  await page.getByRole('button', { name: '作废' }).first().click()
  await page.getByLabel('作废原因').fill('E2E 测试作废')
  await page.getByRole('button', { name: '确认作废' }).click()
  await expect(page.getByText('已作废').first()).toBeVisible()
  await go(page, '/receivables')
  await expect(statCard(page, '总欠款')).toContainText('¥110.00')
})

async function creditSale(page: Page, product: string, qty: string, paid: string) {
  await pickProduct(page, product)
  await page.getByLabel(`${product} 数量`).fill(qty)
  await page.getByLabel('本次实收').fill(paid)
  await expect(page.getByText(/本单欠款/)).toBeVisible()
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
}
