import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { createActivationCode, go } from './helpers'

/**
 * Excel 导入导出：
 *   下载商品模板 → 上传有错误的文件（第 3 行零售价不是数字）→ 提示错误、不导入
 *   → 上传 3 个商品（含新分类、初始库存）→ 预检通过 → 导入 → 列表看到 3 个商品和库存
 *   → 专业版导出 Excel，文件是 .xlsx
 */
test('导入商品 → 预检错误 → 导入成功 → 导出 Excel', async ({ page, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '文件上传下载流程在桌面项目中验证；手机布局由布局检查覆盖')
  const stamp = Date.now()

  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `excel-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E 导入', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E 导入店 ${stamp}`, businessType: 'RETAIL' },
  })
  expect(store.status()).toBe(201)

  // 免费版也能导入；先下载模板
  await go(page, '/products')
  await page.getByRole('button', { name: '导入', exact: true }).click()
  const dialog = page.getByRole('dialog')
  const [template] = await Promise.all([
    page.waitForEvent('download'),
    dialog.getByRole('button', { name: '下载商品导入模板' }).click(),
  ])
  expect(template.suggestedFilename()).toBe('商品导入模板.xlsx')
  expect(readFileSync(await template.path()).subarray(0, 2).toString()).toBe('PK')

  // 有错误的文件：整批不导入
  const header = '商品名称*,单位*,零售价*,进货成本*,SKU,条码,分类,批发价,库存预警值,初始库存'
  const bad = `﻿${header}\n好商品${stamp},个,10,5,E2E-OK-${stamp},,,,,1\n坏商品${stamp},个,十元,5,E2E-BAD-${stamp},,,,,\n`
  await dialog.getByLabel('选择导入文件').setInputFiles({ name: 'bad.csv', mimeType: 'text/csv', buffer: Buffer.from(bad) })
  await dialog.getByRole('button', { name: '检查文件' }).click()
  await expect(dialog.getByText('发现 1 处问题')).toBeVisible()
  await expect(dialog.getByRole('row').filter({ hasText: '第 3 行 · 零售价' })).toContainText('必须是数字')
  await expect(dialog.getByRole('button', { name: '确认导入' })).toBeDisabled()

  // 正确的文件
  const rows = [
    `E2E 数据线 ${stamp},条,19.9,8.5,E2E-C-${stamp},,E2E 数码 ${stamp} / 数据线,,,10`,
    `E2E 充电头 ${stamp},个,29,12,E2E-H-${stamp},,E2E 数码 ${stamp} / 充电,,,5`,
    `E2E 手机壳 ${stamp},个,15,5.5,E2E-S-${stamp},,,,,`,
  ]
  await dialog.getByLabel('选择导入文件').setInputFiles({
    name: 'products.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(`﻿${header}\n${rows.join('\n')}\n`),
  })
  await dialog.getByRole('button', { name: '检查文件' }).click()
  await expect(dialog.getByText('检查通过')).toBeVisible()
  await expect(dialog.getByText(/共 3 行：将新增 3/)).toBeVisible()
  await expect(dialog.getByText(/将自动新建分类/)).toContainText(`E2E 数码 ${stamp} / 数据线`)
  await page.screenshot({ path: testInfo.outputPath('import-check.png') })
  await dialog.getByRole('button', { name: '确认导入 3 个商品' }).click()
  await expect(dialog.getByText('导入完成')).toBeVisible()
  await dialog.getByRole('button', { name: '完成' }).click()

  await page.getByLabel('搜索商品').fill(`${stamp}`)
  await expect(page.getByText(`E2E 数据线 ${stamp}`).first()).toBeVisible()
  await expect(page.getByText(`E2E 手机壳 ${stamp}`).first()).toBeVisible()
  await expect(page.getByRole('row').filter({ hasText: `E2E 数据线 ${stamp}` })).toContainText('10')

  // 普通会员导出 Excel：弹出升级窗口
  await page.getByRole('button', { name: '导出 Excel' }).click()
  const upgrade = page.getByRole('alertdialog')
  await expect(upgrade.getByText('「导出 Excel 表格」是高级会员功能。', { exact: false })).toBeVisible()
  await upgrade.getByRole('button', { name: '以后再说' }).click()

  // 开通专业版后导出
  await go(page, '/settings/billing')
  await page.getByLabel('激活码').fill(await createActivationCode(baseURL!, 30, 'PRO'))
  await page.getByRole('button', { name: '兑换' }).click()
  await expect(page.getByText(/已开通专业版/)).toBeVisible()
  await go(page, '/products')
  const [file] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '导出 Excel' }).click()])
  expect(file.suggestedFilename()).toMatch(/^商品_\d{4}-\d{2}-\d{2}\.xlsx$/)
  const bytes = readFileSync(await file.path())
  expect(bytes.subarray(0, 2).toString()).toBe('PK')
  expect(bytes.length).toBeGreaterThan(3000)
})
