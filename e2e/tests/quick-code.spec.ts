import { expect, test } from '@playwright/test'
import { devEnv } from './helpers'

/**
 * 快速发码（docs/18）：不登录，输入密码 → 生成激活码 → 买家在会员页兑换开通永久会员；密码错误时提示。
 * 需要 .env.dev 中配置 QUICK_CODE_PASSWORD。
 */
const PASSWORD = process.env.QUICK_CODE_PASSWORD ?? devEnv().QUICK_CODE_PASSWORD ?? ''

test('快速发码：输入密码生成激活码，买家兑换开通永久会员', async ({ page, browser }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '在桌面项目中验证')
  test.skip(!PASSWORD, '没有配置 QUICK_CODE_PASSWORD')
  const baseURL = testInfo.project.use.baseURL ?? 'http://localhost:5173'
  const stamp = Date.now()

  await page.goto('/quick-code')
  await expect(page.getByRole('heading', { name: '快速发码' })).toBeVisible()

  // 密码错误
  await page.getByLabel('密码').fill('not-the-password')
  await page.getByRole('button', { name: '生成激活码' }).click()
  await expect(page.getByText('密码不正确')).toBeVisible()

  // 密码正确：生成一个码；再点一次生成另一个
  await page.getByLabel('密码').fill(PASSWORD)
  await page.getByRole('button', { name: '生成激活码' }).click()
  const first = (await page.getByTestId('quick-code').textContent())!.trim()
  expect(first).toMatch(/^SBZ-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/)
  await page.getByRole('button', { name: '生成激活码' }).click()
  await expect(page.getByTestId('quick-code')).not.toHaveText(first)

  // 买家兑换第一个码
  const buyerCtx = await browser.newContext({ baseURL, locale: 'zh-CN' })
  const buyer = await buyerCtx.newPage()
  const reg = await buyer.request.post('/api/v1/auth/register', {
    data: { email: `quick-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 快速买家', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await buyer.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 快速店 ${stamp}`, businessType: 'RETAIL', currency: 'CNY' },
  })
  expect(store.status()).toBe(201)
  await buyer.goto('/settings/billing')
  await buyer.getByLabel('激活码').fill(first)
  await buyer.getByRole('button', { name: '兑换并开通' }).click()
  await expect(buyer.getByText('已开通高级永久会员')).toBeVisible()
  await expect(buyer.getByTestId('membership-level')).toHaveText('高级永久会员')
  await buyerCtx.close()
})
