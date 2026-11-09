import { expect, test, type Page } from '@playwright/test'
import { createActivationCode } from './helpers'

/**
 * 普通会员 + 高级永久会员（docs/16）：
 * 普通会员建满 3 个商品 → 第 4 个弹出升级窗口（文字、价格）→ 已有商品照常编辑 → 会员页显示已用次数
 * → 会员页只有激活码 → 输错提示 → 兑换永久会员激活码 → 立即可以继续新建、高级功能解锁
 */
async function newOwner(page: Page) {
  const stamp = Date.now()
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `member-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 会员', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const token = (await reg.json()).accessToken
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: `E2E 会员店 ${stamp}`, businessType: 'RETAIL', currency: 'CNY' },
  })
  expect(store.status()).toBe(201)
  return { stamp, token: (await store.json()).accessToken as string }
}

async function createProductViaForm(page: Page, name: string) {
  await page.goto('/products/new')
  await page.getByLabel('商品名称').fill(name)
  await page.getByLabel('单位').fill('个')
  await page.getByLabel('进货成本').fill('3')
  await page.getByLabel('零售价').fill('8')
  await page.getByRole('button', { name: '创建商品' }).click()
}

test('普通会员次数用完 → 升级窗口 → 升级永久会员后继续使用', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '会员流程在桌面项目中验证')
  const { stamp, token } = await newOwner(page)
  for (let i = 1; i <= 3; i++) {
    const r = await page.request.post('/api/v1/products', {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: `会员商品${i} ${stamp}`, unit: '个', costPrice: '1', retailPrice: '2', lowStockThreshold: 0, initialStock: 0 },
    })
    expect(r.status()).toBe(201)
  }

  // 商品页透明显示已用次数
  await page.goto('/products')
  await expect(page.getByTestId('free-usage-products')).toContainText('已新建 3 / 3 个商品')

  // 第 4 个：服务端拒绝，弹出升级窗口
  await createProductViaForm(page, `会员商品4 ${stamp}`)
  const dialog = page.getByRole('alertdialog')
  await expect(dialog.getByRole('heading', { name: '已达到免费使用次数' })).toBeVisible()
  await expect(dialog).toContainText('你已经使用完当前普通会员的商品创建额度。升级高级永久会员，即可解锁完整功能，不再受到普通会员次数限制。')
  await expect(dialog.getByTestId('upgrade-price')).toHaveText('¥29.90')
  await expect(dialog).toContainText('一次性付款，永久享有高级会员权益，无需自动续费。')
  await dialog.getByRole('button', { name: '以后再说' }).click()

  // 已有商品照常查看
  await page.goto('/products')
  await expect(page.getByText(`会员商品1 ${stamp}`).first()).toBeVisible()

  // 会员页：普通会员、已用次数、价格；只有激活码一种开通方式（不显示任何付款方式）
  await page.goto('/settings/billing?redeem=1')
  await expect(page.getByTestId('membership-level')).toHaveText('普通会员')
  await expect(page.getByRole('progressbar', { name: '已新建商品' })).toHaveAttribute('aria-valuenow', '3')
  await expect(page.getByTestId('lifetime-price')).toHaveText('¥29.90')
  await expect(page.getByText('付款方式')).toHaveCount(0)
  await expect(page.getByText(/扫码付款|在线支付/)).toHaveCount(0)
  await expect(page.getByLabel('激活码')).toBeFocused()

  // 输错：提示不正确，不开通
  await page.getByLabel('激活码').fill('SBZ-AAAA-BBBB-CCCC')
  await page.getByRole('button', { name: '兑换并开通' }).click()
  await expect(page.getByText('激活码不正确，请检查后重新输入')).toBeVisible()
  await expect(page.getByTestId('membership-level')).toHaveText('普通会员')

  // 正确的永久会员激活码（在小红书售出）：立即开通
  const code = await createActivationCode(testInfo.project.use.baseURL ?? 'http://localhost:5173', 0, 'LIFETIME')
  await page.getByLabel('激活码').fill(code.toLowerCase())
  await page.getByRole('button', { name: '兑换并开通' }).click()
  await expect(page.getByText('已开通高级永久会员')).toBeVisible()
  // 升级后立即生效：会员页显示永久会员，不再计数；可以继续新建；高级功能解锁
  await page.goto('/settings/billing')
  await expect(page.getByTestId('membership-level')).toHaveText('高级永久会员')
  await expect(page.getByText('你是高级永久会员')).toBeVisible()
  await createProductViaForm(page, `会员商品5 ${stamp}`)
  await expect(page.getByRole('heading', { name: `会员商品5 ${stamp}` })).toBeVisible()
  await page.goto('/products')
  await expect(page.getByTestId('free-usage-products')).toHaveCount(0)
  await page.goto('/customers')
  await expect(page.getByRole('heading', { name: '客户', exact: true })).toBeVisible()
})
