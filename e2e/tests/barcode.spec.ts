import { expect, test, type Page } from '@playwright/test'
import { createActivationCode, go } from './helpers'

/** 扫码枪：像键盘一样快速输入条码，最后按回车。 */
async function scanWithGun(page: Page, field: ReturnType<Page['getByRole']>, code: string) {
  await field.click()
  await page.keyboard.type(code, { delay: 5 })
  await page.keyboard.press('Enter')
}

async function createProduct(page: Page, name: string, barcode: string | 'generate') {
  await go(page, '/products/new')
  await page.getByLabel('商品名称').fill(name)
  await page.getByLabel('单位').fill('个')
  await page.getByLabel('进货成本').fill('3')
  await page.getByLabel('零售价').fill('8')
  await page.getByLabel('初始库存').fill('20')
  if (barcode === 'generate') {
    await page.getByRole('button', { name: '生成店内码' }).click()
    await expect(page.getByLabel('条形码')).toHaveValue(/^20\d{11}$/)
  } else {
    await page.getByLabel('条形码').fill(barcode)
  }
  const code = await page.getByLabel('条形码').inputValue()
  await page.getByRole('button', { name: '创建商品' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  return { id: Number(page.url().split('/').pop()), code }
}

/**
 * 条码：手填条码 / 生成店内码 → 重复条码被拒 → 开单页用扫码枪扫两次数量变 2、扫不存在的码有提示
 * → 商品列表扫码直接打开详情 → 标准版打印标签（免费版提示升级）
 */
test('扫码枪开单、条码唯一、店内码、打印标签', async ({ page, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '扫码流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `scan-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 扫码', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 扫码店 ${stamp}`, businessType: 'RETAIL' },
  })
  expect(store.status()).toBe(201)

  // 条码在店铺内唯一：每次运行用不同的条码
  const cola = `69${String(stamp).slice(-10)}`
  const a = await createProduct(page, `E2E 可乐 ${stamp}`, cola)
  const b = await createProduct(page, `E2E 散装糖 ${stamp}`, 'generate')

  // 重复条码
  await go(page, '/products/new')
  await page.getByLabel('商品名称').fill(`E2E 重复 ${stamp}`)
  await page.getByLabel('单位').fill('个')
  await page.getByLabel('进货成本').fill('1')
  await page.getByLabel('零售价').fill('2')
  await page.getByLabel('条形码').fill(cola)
  await page.getByRole('button', { name: '创建商品' }).click()
  await expect(page.getByText(`条码「${cola}」已被商品「E2E 可乐 ${stamp}」使用`)).toBeVisible()

  // 开单：扫码枪扫两次 → 数量 2；再扫店内码商品
  await go(page, '/sales/new')
  const picker = page.getByRole('combobox').first()
  await scanWithGun(page, picker, cola)
  await expect(page.getByLabel(`E2E 可乐 ${stamp} 数量`)).toHaveValue('1')
  await scanWithGun(page, picker, cola)
  await expect(page.getByLabel(`E2E 可乐 ${stamp} 数量`)).toHaveValue('2')
  await scanWithGun(page, picker, b.code)
  await expect(page.getByLabel(`E2E 散装糖 ${stamp} 数量`)).toHaveValue('1')
  // 不存在的条码
  await scanWithGun(page, picker, '9999999999999')
  await expect(page.getByText('没有条码为「9999999999999」的商品')).toBeVisible()
  await expect(page.getByRole('button', { name: '用这个条码新建商品' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('scan-sale.png') })
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(page.getByText('¥24.00').first()).toBeVisible() // 2 × 8 + 1 × 8

  // 商品列表：扫码直接打开详情
  await go(page, '/products')
  await scanWithGun(page, page.getByLabel('搜索商品'), cola)
  await expect(page).toHaveURL(new RegExp(`/products/${a.id}$`))
  await expect(page.getByRole('heading', { name: `E2E 可乐 ${stamp}` })).toBeVisible()

  // 打印标签：免费版提示升级
  await go(page, `/products/labels?ids=${a.id},${b.id}`)
  await expect(page.getByRole('heading', { name: /是标准版功能/ })).toBeVisible()
  await go(page, '/settings/billing')
  await page.getByLabel('激活码').fill(await createActivationCode(baseURL!, 30, 'STANDARD'))
  await page.getByRole('button', { name: '兑换' }).click()
  await expect(page.getByText(/已开通标准版/)).toBeVisible()

  await go(page, `/products/labels?ids=${a.id},${b.id}`)
  await expect(page.getByText('2 个商品 · 共 2 张')).toBeVisible()
  await page.getByRole('button', { name: '增加一张' }).first().click()
  await expect(page.getByText('2 个商品 · 共 3 张')).toBeVisible()
  await expect(page.getByRole('button', { name: '打印 3 张' })).toBeEnabled()
  // 预览里画出了条码图形
  const preview = page.getByRole('img', { name: `条码 ${cola}` }).first()
  await expect(preview).toBeVisible()
  expect(await preview.locator('rect').count()).toBeGreaterThan(20)
  await page.screenshot({ path: testInfo.outputPath('labels.png'), fullPage: true })
})
