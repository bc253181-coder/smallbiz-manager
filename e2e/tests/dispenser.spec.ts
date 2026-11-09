import { expect, request as playwrightRequest, test } from '@playwright/test'
import { adminCredentials } from './helpers'

/**
 * 发码台（docs/17）：平台管理员生成永久会员激活码 → 按小红书订单取码 → 同一订单再取需确认
 * → 买家在会员页兑换成功 → 发码台显示「已兑换」和店铺名
 */
test('发码台：按订单取码，买家兑换后显示已兑换', async ({ page, browser }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '后台在桌面项目中验证')
  const baseURL = testInfo.project.use.baseURL ?? 'http://localhost:5173'
  const stamp = Date.now()
  const order = `XHS-E2E-${stamp}`

  // 准备：生成 2 个永久会员激活码（原码加密保存，可以从发码台取出）
  const api = await playwrightRequest.newContext({ baseURL })
  const login = await api.post('/api/v1/auth/login', { data: adminCredentials() })
  expect(login.ok()).toBeTruthy()
  const token = (await login.json()).accessToken
  const batch = await api.post('/api/v1/admin/code-batches', {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: `E2E 发码 ${stamp}`, plan: 'PRO', durationDays: 0, trial: false, lifetime: true, quantity: 2 },
  })
  expect(batch.status()).toBe(201)
  await api.dispose()

  // 平台管理员登录后台
  const creds = adminCredentials()
  await page.goto('/login')
  await page.getByLabel('邮箱').fill(creds.email)
  await page.getByLabel('密码').fill(creds.password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL((u) => !u.pathname.startsWith('/login'))
  await page.goto('/admin/dispenser')
  await expect(page.getByRole('heading', { name: '发码台' })).toBeVisible()

  // 取码
  await page.getByLabel('小红书订单号').fill(order)
  await page.getByRole('button', { name: '取码' }).click()
  const codeEl = page.getByTestId('dispensed-code')
  await expect(codeEl).toHaveCount(1)
  const code = (await codeEl.textContent())!.trim()
  expect(code).toMatch(/^SBZ-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/)

  // 同一订单再取：先提醒，取消后不发
  await page.getByLabel('小红书订单号').fill(order)
  await page.getByRole('button', { name: '取码' }).click()
  const confirm = page.getByRole('alertdialog')
  await expect(confirm.getByText('这个订单已经发过码')).toBeVisible()
  await confirm.getByRole('button', { name: '取消' }).click()

  // 发码记录：已发出
  await page.getByLabel('搜索发码记录').fill(order)
  const row = page.getByRole('row').filter({ hasText: order })
  await expect(row.getByText('已发出')).toBeVisible()

  // 买家：注册、开店、在会员页兑换
  const buyerCtx = await browser.newContext({ baseURL, locale: 'zh-CN' })
  const buyer = await buyerCtx.newPage()
  const reg = await buyer.request.post('/api/v1/auth/register', {
    data: { email: `buyer-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 买家', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await buyer.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 买家店 ${stamp}`, businessType: 'RETAIL', currency: 'CNY' },
  })
  expect(store.status()).toBe(201)
  await buyer.goto('/settings/billing')
  await buyer.getByLabel('激活码').fill(code)
  await buyer.getByRole('button', { name: '兑换并开通' }).click()
  await expect(buyer.getByText('已开通高级永久会员')).toBeVisible()
  await expect(buyer.getByTestId('membership-level')).toHaveText('高级永久会员')
  await buyerCtx.close()

  // 发码台：已兑换，显示买家店铺
  await page.reload()
  await page.getByLabel('搜索发码记录').fill(order)
  await expect(row.getByText('已兑换')).toBeVisible()
  await expect(row.getByText(`E2E 买家店 ${stamp}`)).toBeVisible()
})
