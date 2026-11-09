import { expect, test, type Page } from '@playwright/test'
import { adminCredentials } from './helpers'

/**
 * 在线支付（开发环境的模拟支付渠道）：
 * 选择套餐 → 在线支付 → 模拟付款页「付款成功」→ 回到结果页自动显示已开通 → 套餐和付款记录正确；
 * 付款失败 → 订单保持待付款，可以继续付款或取消；通知丢失 → 平台对账后补开通。
 */
async function newOwner(page: Page, tag: string) {
  const stamp = Date.now()
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `pay-${tag}-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 付款', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 在线付款店 ${stamp}`, businessType: 'RETAIL', currency: 'CNY' },
  })
  expect(store.status()).toBe(201)
}

async function chooseOnlineAndPay(page: Page, period: RegExp) {
  await page.goto('/settings/billing')
  await expect(page.getByText('在线支付 · 模拟支付（测试）')).toBeVisible()
  await page.getByRole('button', { name: period }).click()
  await page.getByRole('button', { name: /在线支付 · 模拟支付（测试）/ }).click()
  await page.getByRole('button', { name: /^去付款/ }).click()
  await expect(page).toHaveURL(/\/pay\/mock\/PAY-/)
  await expect(page.getByRole('heading', { name: '模拟支付' })).toBeVisible()
}

test('在线支付：付款成功自动开通、付款失败可继续付款', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '付款流程在桌面项目中验证')
  await newOwner(page, 'ok')

  // 付款失败：停留在付款页，订单仍是待付款，可以从付款单继续付款
  await chooseOnlineAndPay(page, /月付/)
  await expect(page.getByText('¥6.99')).toBeVisible()
  await page.getByRole('button', { name: '模拟付款失败' }).click()
  await expect(page.getByText('付款失败（模拟）')).toBeVisible()
  await page.goto('/settings/billing')
  await page.getByRole('link', { name: /待付款的付款单/ }).click()
  await expect(page.getByRole('heading', { name: '正在确认付款…' })).toBeVisible()

  // 继续付款 → 付款成功 → 回到结果页，自动显示已开通
  await page.getByRole('link', { name: '继续付款' }).click()
  await page.getByRole('button', { name: '模拟付款成功', exact: true }).click()
  await expect(page).toHaveURL(/\/settings\/billing\/pay\/\d+/)
  await expect(page.getByRole('heading', { name: '付款已确认，套餐已开通' })).toBeVisible()
  const receipt = page.getByLabel('收据')
  await expect(receipt.getByText('模拟支付（测试）')).toBeVisible()
  await expect(receipt.getByText(/^MOCK-/)).toBeVisible()
  await expect(page.getByRole('button', { name: '打印收据' })).toBeVisible()

  // 套餐页：已是标准版；付款记录显示在线支付；变更记录来源为在线支付
  await page.goto('/settings/billing')
  await expect(page.getByText('标准版').first()).toBeVisible()
  await expect(page.getByText(/· 在线支付$/).first()).toBeVisible()
  await expect(page.getByRole('cell', { name: '在线支付' }).first()).toBeVisible()
})

test('在线支付：通知丢失时，对账后补开通', async ({ page, request }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '付款流程在桌面项目中验证')
  await newOwner(page, 'lost')
  await chooseOnlineAndPay(page, /月付/)
  await page.getByRole('button', { name: /通知丢失/ }).click()
  await expect(page.getByRole('heading', { name: '正在确认付款…' })).toBeVisible()

  // 平台对账（定时任务每 10 分钟一次；这里用平台管理员的「立即对账」触发，独立的请求上下文，不影响页面登录）
  const login = await request.post('/api/v1/auth/login', { data: adminCredentials() })
  expect(login.ok()).toBeTruthy()
  const r = await request.post('/api/v1/admin/online-payments/reconcile', {
    headers: { Authorization: `Bearer ${(await login.json()).accessToken}` },
  })
  expect(r.ok()).toBeTruthy()
  expect((await r.json()).lastFixed).toBeGreaterThanOrEqual(1)

  // 结果页自动刷新后显示已开通
  await expect(page.getByRole('heading', { name: '付款已确认，套餐已开通' })).toBeVisible({ timeout: 30_000 })
})