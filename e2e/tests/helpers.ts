import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, request, type Locator, type Page } from '@playwright/test'

/** 平台管理员账号：优先读环境变量，否则读项目根目录 .env.dev 中的开发账号（不会打印出来）。 */
export function adminCredentials(): { email: string; password: string } {
  if (process.env.E2E_ADMIN_EMAIL && process.env.E2E_ADMIN_PASSWORD) {
    return { email: process.env.E2E_ADMIN_EMAIL, password: process.env.E2E_ADMIN_PASSWORD }
  }
  const file = fileURLToPath(new URL('../../.env.dev', import.meta.url))
  const vars = Object.fromEntries(
    readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .map((l) => l.match(/^\s*([A-Z0-9_]+)=(.*)$/))
      .filter((m): m is RegExpMatchArray => !!m)
      .map((m) => [m[1], m[2].trim()]),
  )
  if (!vars.DEV_TEST_EMAIL || !vars.DEV_TEST_PASSWORD) {
    throw new Error('请设置 E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD，或在 .env.dev 中配置 DEV_TEST_EMAIL / DEV_TEST_PASSWORD')
  }
  return { email: vars.DEV_TEST_EMAIL, password: vars.DEV_TEST_PASSWORD }
}

/** 以平台管理员身份通过 API 生成一个标准版激活码（模拟在小红书售出）。 */
export async function createActivationCode(baseURL: string, days = 30, plan: 'STANDARD' | 'PRO' | 'LIFETIME' = 'STANDARD'): Promise<string> {
  const api = await request.newContext({ baseURL })
  try {
    const login = await api.post('/api/v1/auth/login', { data: adminCredentials() })
    expect(login.ok(), '平台管理员登录失败').toBeTruthy()
    const { accessToken } = await login.json()
    const res = await api.post('/api/v1/admin/code-batches', {
      headers: { Authorization: `Bearer ${accessToken}` },
      data:
        plan === 'LIFETIME'
          ? { name: 'E2E 永久会员', plan: 'PRO', durationDays: 0, trial: false, lifetime: true, quantity: 1 }
          : { name: 'E2E 自动测试', plan, durationDays: days, trial: false, quantity: 1 },
    })
    expect(res.status(), await res.text()).toBe(201)
    return (await res.json()).codes[0]
  } finally {
    await api.dispose()
  }
}

/** 报表 / 仪表盘上"标题 + 数值"的小卡片。 */
export function statCard(page: Page, label: string): Locator {
  return page
    .locator('div.rounded-xl, a.rounded-xl')
    .filter({ has: page.getByText(label, { exact: true }) })
    .last()
}

/** 在商品搜索框中搜索并选中商品。 */
export async function pickProduct(page: Page, name: string) {
  const picker = page.getByRole('combobox').first()
  await picker.click()
  await picker.fill(name)
  await page.getByRole('option').filter({ hasText: name }).first().click()
}

/** 手机布局下导航在抽屉菜单里；直接通过网址跳转，两种布局都适用。 */
export async function go(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('main, article').first()).toBeVisible()
}
