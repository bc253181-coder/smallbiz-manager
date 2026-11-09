import { expect, test } from '@playwright/test'
import { createActivationCode, go, pickProduct } from './helpers'

/**
 * 员工（专业版）完整流程：
 *   店主兑换专业版 → 生成店员邀请链接 → 新用户打开链接注册并加入 → 店员看不到成本、能开单
 *   → 店主在销售列表按开单人筛选只看到店员的 1 张单 → 店主移除店员 → 店员下一次操作就被请出店铺
 */
test('专业版：邀请店员 → 店员开单 → 按开单人筛选 → 移除', async ({ page, browser, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '多账号流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()
  const store = `E2E 员工店 ${stamp}`
  const product = `E2E 员工商品 ${stamp}`
  const staffName = `E2E 店员 ${stamp}`

  // 店主：注册、开店、兑换专业版
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `boss-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 店主', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const created = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: store, businessType: 'RETAIL' },
  })
  expect(created.status()).toBe(201)

  // 免费版：员工管理提示需要专业版
  await go(page, '/settings/staff')
  await expect(page.getByText('员工账号是专业版功能')).toBeVisible()
  await go(page, '/settings/billing')
  await page.getByLabel('激活码').fill(await createActivationCode(baseURL!, 30, 'PRO'))
  await page.getByRole('button', { name: '兑换' }).click()
  await expect(page.getByText(/已开通专业版/)).toBeVisible()

  // 商品（成本 5、零售价 10）
  await go(page, '/products/new')
  await page.getByLabel('商品名称').fill(product)
  await page.getByLabel('单位').fill('个')
  await page.getByLabel('进货成本').fill('5')
  await page.getByLabel('零售价').fill('10')
  await page.getByLabel('初始库存').fill('50')
  await page.getByRole('button', { name: '创建商品' }).click()
  await expect(page.getByRole('heading', { name: product })).toBeVisible()

  // 店主开 1 张单
  await go(page, '/sales/new')
  await pickProduct(page, product)
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()

  // 生成邀请链接
  await go(page, '/settings/staff')
  await expect(page.getByText('已用 0 / 5')).toBeVisible()
  await page.getByRole('button', { name: '邀请员工' }).click()
  await page.getByLabel('备注').fill('小王')
  await page.getByRole('button', { name: '生成邀请链接' }).click()
  const link = (await page.getByLabel('邀请链接', { exact: true }).textContent())!.trim()
  expect(link).toMatch(/\/invite\/[A-Za-z0-9_-]{40,}$/)
  await page.getByRole('button', { name: '完成' }).click()
  await expect(page.getByText('已用 1 / 5')).toBeVisible()
  await expect(page.getByText('小王 · 店员')).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('staff-page.png'), fullPage: true })

  // 店员：在另一个浏览器里打开链接 → 注册 → 回到邀请页 → 加入
  const staffContext = await browser.newContext()
  const staff = await staffContext.newPage()
  await staff.goto(link)
  await expect(staff.getByText(store)).toBeVisible()
  await expect(staff.getByText(/邀请你以「店员」身份加入/)).toBeVisible()
  await staff.getByRole('link', { name: '还没有账号？注册' }).click()
  await staff.getByLabel('昵称').fill(staffName)
  await staff.getByLabel('邮箱').fill(`staff-${stamp}@e2e.test`)
  await staff.getByLabel('密码', { exact: true }).fill('E2ePassw0rd!')
  await staff.getByLabel('确认密码').fill('E2ePassw0rd!')
  await staff.getByRole('checkbox').check()
  await staff.getByRole('button', { name: '注册' }).click()
  await expect(staff).toHaveURL(/\/invite\//)
  await staff.getByRole('button', { name: `以 ${staffName} 的身份加入` }).click()
  await expect(staff.getByText('今日销售额')).toBeVisible()
  await expect(staff.getByText(store).first()).toBeVisible()

  // 店员看不到成本，能开单
  await go(staff, '/products')
  await staff.getByRole('link', { name: product }).first().click()
  await expect(staff.getByRole('heading', { name: product })).toBeVisible()
  await expect(staff.getByText('进货成本')).toHaveCount(0)
  await go(staff, '/sales/new')
  await pickProduct(staff, product)
  await staff.getByRole('button', { name: '完成开单' }).click()
  await expect(staff.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()

  // 店主：按开单人筛选
  await go(page, '/sales')
  await expect(page.getByRole('row')).toHaveCount(3) // 表头 + 2 张单
  await page.getByLabel('开单人').selectOption({ label: staffName })
  await expect(page.getByRole('row')).toHaveCount(2)
  await expect(page.getByRole('row').nth(1)).toContainText(staffName)
  await page.screenshot({ path: testInfo.outputPath('sales-by-creator.png'), fullPage: true })

  // 店主移除店员
  await go(page, '/settings/staff')
  await page.getByRole('listitem').filter({ hasText: staffName }).getByRole('button', { name: '移除' }).click()
  await page.getByRole('button', { name: '移除', exact: true }).last().click()
  await expect(page.getByText(`已移除 ${staffName}`)).toBeVisible()
  await expect(page.getByText('已用 0 / 5')).toBeVisible()

  // 店员下一次操作就被请出店铺（没有其他店铺 → 创建店铺引导页）
  await staff.goto('/products')
  await expect(staff).toHaveURL(/\/onboarding/)
  await staffContext.close()
})
