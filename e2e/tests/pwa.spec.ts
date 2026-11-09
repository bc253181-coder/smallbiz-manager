import { expect, request, test, type Page } from '@playwright/test'

/**
 * PWA：Service Worker 只在生产构建里启用，所以针对 `npm run build && npm run preview`（4173 端口，接口代理到后端）。
 * 没有启动 preview 时自动跳过。
 */
const PREVIEW = process.env.E2E_PWA_URL ?? 'http://localhost:4173'
test.use({ baseURL: PREVIEW })

test.beforeAll(async () => {
  const api = await request.newContext()
  const ok = await api
    .get(`${PREVIEW}/manifest.webmanifest`)
    .then((r) => r.ok())
    .catch(() => false)
  await api.dispose()
  test.skip(!ok, `生产预览没有运行（${PREVIEW}）：先在 frontend 里 npm run build && npm run preview`)
})

async function registerOwner(page: Page, tag: string) {
  const stamp = Date.now()
  const reg = await page.request.post('/api/v1/auth/register', {
    data: { email: `pwa-${tag}-${stamp}@e2e.test`, password: 'E2ePassw0rd!', displayName: 'E2E PWA', agreeTerms: true },
  })
  expect(reg.status()).toBe(201)
  const store = await page.request.post('/api/v1/tenants', {
    headers: { Authorization: `Bearer ${(await reg.json()).accessToken}` },
    data: { name: `E2E PWA 店 ${stamp}`, businessType: 'RETAIL' },
  })
  expect(store.status()).toBe(201)
}

const OFFLINE = '网络已断开，恢复后会自动刷新数据'

test('manifest、Service Worker、断网打开、恢复后刷新数据、接口不缓存', async ({ page, context }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '离线流程在桌面项目中验证')

  // manifest 字段
  const manifest = await (await page.request.get('/manifest.webmanifest')).json()
  expect(manifest).toMatchObject({ name: 'SmallBiz Manager', short_name: 'SmallBiz', display: 'standalone', start_url: '/', theme_color: '#2563eb' })
  const sizes = manifest.icons.map((i: { sizes: string; purpose?: string }) => `${i.sizes}${i.purpose ? ':' + i.purpose : ''}`)
  expect(sizes).toEqual(expect.arrayContaining(['192x192', '512x512', '512x512:maskable']))
  for (const icon of [...manifest.icons.map((i: { src: string }) => i.src), '/icons/apple-touch-icon.png']) {
    const r = await page.request.get(icon)
    expect(r.ok(), icon).toBeTruthy()
    expect(r.headers()['content-type']).toContain('image/png')
  }

  await registerOwner(page, 'off')
  await page.goto('/products')
  await expect(page.getByRole('heading', { name: '商品', exact: true })).toBeVisible()

  // Service Worker 注册并接管页面
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => undefined))
  if (!(await page.evaluate(() => !!navigator.serviceWorker.controller))) {
    await page.reload()
    await expect(page.getByRole('heading', { name: '商品', exact: true })).toBeVisible()
  }
  expect(await page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true)

  // 断网：已打开的页面显示提示；接口请求失败，而不是返回缓存的旧数据
  await context.setOffline(true)
  await expect(page.getByText(OFFLINE)).toBeVisible()
  const apiResult = await page.evaluate(() =>
    fetch('/api/v1/products?page=0&size=1').then(
      (r) => `status ${r.status}`,
      () => 'network error',
    ),
  )
  expect(apiResult).toBe('network error')

  // 断网时重新打开页面：页面程序从手机缓存加载，显示断网提示（不会被当成未登录跳到登录页）
  await page.reload()
  await expect(page.getByText(OFFLINE)).toBeVisible()
  expect(new URL(page.url()).pathname).toBe('/products')

  // 恢复网络：提示消失，自动恢复登录并重新加载数据
  await context.setOffline(false)
  await expect(page.getByText(OFFLINE)).toBeHidden()
  await expect(page.getByRole('heading', { name: '商品', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '还没有商品' })).toBeVisible() // 商品列表接口重新加载成功
  expect(new URL(page.url()).pathname).toBe('/products')
})

test('手机上提示添加到主屏幕，关闭后不再提示；设置页有安装入口', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', '安装引导只在手机宽度显示')
  await registerOwner(page, 'install')

  // 模拟安卓浏览器的「可安装」事件（真实事件只在满足安装条件的 HTTPS 站点上由浏览器发出）
  const fireInstallable = () =>
    page.evaluate(() => {
      const e = new Event('beforeinstallprompt', { cancelable: true }) as Event & Record<string, unknown>
      ;(window as unknown as { __prompted: number }).__prompted = 0
      e.prompt = async () => {
        ;(window as unknown as { __prompted: number }).__prompted++
      }
      e.userChoice = Promise.resolve({ outcome: 'dismissed' })
      window.dispatchEvent(e)
    })

  await page.goto('/')
  await expect(page.locator('main').first()).toBeVisible()
  const banner = page.getByRole('dialog', { name: '添加到主屏幕' })
  await expect(banner).toBeHidden()
  await fireInstallable()
  await expect(banner).toBeVisible()
  await expect(banner.getByText('把 SmallBiz 添加到主屏幕')).toBeVisible()

  // 点安装会弹出系统安装框（用户取消时提示保留）
  await banner.getByRole('button', { name: '安装' }).click()
  expect(await page.evaluate(() => (window as unknown as { __prompted: number }).__prompted)).toBe(1)

  // 关闭后 30 天内不再提示
  await fireInstallable()
  await banner.getByRole('button', { name: '不再提示' }).click()
  await expect(banner).toBeHidden()
  await page.reload()
  await expect(page.locator('main').first()).toBeVisible()
  await fireInstallable()
  await expect(banner).toBeHidden()

  // 设置页的安装入口仍可用
  await page.goto('/settings')
  const entry = page.getByTestId('install-app')
  await expect(entry).toBeVisible()
  await expect(entry.getByText('安装到手机')).toBeVisible()
  await expect(entry.getByRole('button', { name: '安装' })).toBeHidden() // 浏览器还没发出可安装事件：只显示操作说明
  await fireInstallable()
  await entry.getByRole('button', { name: '安装' }).click()
  expect(await page.evaluate(() => (window as unknown as { __prompted: number }).__prompted)).toBe(1)
  await expect(banner).toBeHidden() // 关闭过的顶部提示不会再出现
})
