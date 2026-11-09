import { expect, test } from '@playwright/test'
import { createActivationCode, go, pickProduct, statCard } from './helpers'

/**
 * 商户完整流程：注册 → 开店 → 建商品 → 免费版受限 → 兑换激活码 → 采购收货 → 销售 → 取消 → 再次销售 → 核对报表。
 * 每次运行都会新建一个独立账号和店铺，金额全部按手算核对：
 *   初始库存 10 @ ¥5，采购 20 @ ¥6 → 库存 30
 *   销售 3 @ ¥12 = ¥36 → 取消（库存回到 30）
 *   销售 2 @ ¥12 = ¥24 → 库存 28，库存价值 28 × 5.67 = ¥158.76
 *   报表：销售额 ¥24.00、1 笔订单；采购金额 ¥120.00
 */
test('商户从注册到看报表的完整流程', async ({ page, baseURL }, testInfo) => {
  const stamp = `${Date.now()}-${testInfo.project.name}`
  const email = `e2e-${stamp}@e2e.test`
  const product = `E2E 数据线 ${stamp}`

  // ---------- 注册（未勾选协议时被拦下） ----------
  await page.goto('/register')
  await page.getByLabel('昵称').fill('E2E 店主')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码', { exact: true }).fill('E2ePassw0rd!')
  await page.getByLabel('确认密码').fill('E2ePassw0rd!')
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page.getByText('请阅读并同意用户协议和隐私政策')).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '注册' }).click()

  // ---------- 开店 ----------
  await expect(page).toHaveURL(/\/onboarding/)
  await page.getByLabel('店铺名称').fill(`E2E 测试店 ${stamp}`)
  await page.getByRole('button', { name: '创建店铺' }).click()
  await expect(page.getByText('今日销售额')).toBeVisible()

  // ---------- 建商品（初始库存 10，成本 5，零售价 12） ----------
  await go(page, '/products/new')
  await page.getByLabel('商品名称').fill(product)
  await page.getByLabel('单位').fill('条')
  await page.getByLabel('进货成本').fill('5')
  await page.getByLabel('零售价').fill('12')
  await page.getByLabel('初始库存').fill('10')
  await page.getByRole('button', { name: '创建商品' }).click()
  await expect(page.getByRole('heading', { name: product })).toBeVisible()

  // ---------- 免费版不能采购 ----------
  await go(page, '/purchases')
  await expect(page.getByText('「采购入库」是高级会员功能')).toBeVisible()

  // ---------- 兑换激活码 → 标准版 ----------
  const code = await createActivationCode(baseURL!)
  await go(page, '/settings/billing')
  await page.getByLabel('激活码').fill(code.toLowerCase())
  await page.getByRole('button', { name: '兑换' }).click()
  await expect(page.getByText(/已开通标准版/)).toBeVisible()
  await expect(page.getByText('标准版', { exact: true }).first()).toBeVisible()

  // ---------- 采购 20 @ ¥6 并收货 ----------
  await go(page, '/purchases/new')
  await page.getByRole('button', { name: '新增供应商' }).click()
  await page.getByLabel('供应商名称').fill(`E2E 供应商 ${stamp}`)
  await page.getByRole('button', { name: '添加供应商' }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
  await pickProduct(page, product)
  await page.getByLabel(`${product} 数量`).fill('20')
  await page.getByLabel(`${product} 单价`).fill('6')
  await page.getByRole('button', { name: '保存并下单' }).click()
  await expect(page.getByRole('heading', { name: /PO-\d{8}-\d{4}/ })).toBeVisible()
  await page.getByRole('button', { name: '确认收货' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '确认收货' }).click()
  await expect(page.getByText('已收货').first()).toBeVisible()

  // ---------- 销售 3 件后取消 ----------
  await go(page, '/sales/new')
  await pickProduct(page, product)
  await page.getByLabel(`${product} 数量`).fill('3')
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(statCard(page, '应收')).toContainText('¥36.00')
  await page.getByRole('button', { name: '取消订单' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认取消' }).click()
  await expect(page.getByText('已取消').first()).toBeVisible()

  // ---------- 再销售 2 件 ----------
  await go(page, '/sales/new')
  await pickProduct(page, product)
  await page.getByLabel(`${product} 数量`).fill('2')
  await page.getByRole('button', { name: '完成开单' }).click()
  await expect(page.getByRole('heading', { name: /SO-\d{8}-\d{4}/ })).toBeVisible()
  await expect(statCard(page, '应收')).toContainText('¥24.00')

  // ---------- 库存：10 + 20 − 3 + 3 − 2 = 28 ----------
  await go(page, '/inventory')
  await expect(statCard(page, '库存总量')).toContainText('28')
  // 加权平均成本 (10×5 + 20×6) / 30 = 5.67（成本保留 2 位小数），库存价值 28 × 5.67 = 158.76
  await expect(statCard(page, '库存价值（按成本）')).toContainText('¥158.76')

  // ---------- 报表与仪表盘 ----------
  await go(page, '/reports')
  await expect(statCard(page, '销售额')).toContainText('¥24.00')
  await expect(statCard(page, '订单数')).toContainText('1')
  await go(page, '/reports?tab=purchases')
  await expect(statCard(page, '采购金额')).toContainText('¥120.00')
  await go(page, '/')
  await expect(statCard(page, '今日销售额')).toContainText('¥24.00')
})

test('用户协议和隐私政策无需登录即可查看', async ({ page }) => {
  await page.goto('/terms')
  await expect(page.getByRole('heading', { name: 'SmallBiz Manager 用户协议' })).toBeVisible()
  await page.getByRole('link', { name: '隐私政策' }).last().click()
  await expect(page.getByRole('heading', { name: 'SmallBiz Manager 隐私政策' })).toBeVisible()
})
