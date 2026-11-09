import { expect, test, type Page } from '@playwright/test'

/**
 * 在线客服：
 * 1) 没有配置 AI 密钥时（开发环境默认）：和以前一样按关键词回答，更新后的话术生效
 * 2) AI 回答的显示：在浏览器里模拟接口返回（不需要密钥，不花钱），检查 AI 标识、按钮、追问时带上历史、转人工说明
 */
async function openSupport(page: Page) {
  await page.getByRole('button', { name: '打开在线客服' }).click()
  return page.getByRole('dialog', { name: '在线客服' })
}

async function askQuestion(page: Page, text: string) {
  await page.getByLabel('输入问题').fill(text)
  await page.getByRole('button', { name: '发送', exact: true }).click()
}

test('没有 AI 密钥时按关键词回答，话术已更新', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '客服流程在桌面项目中验证')
  await page.goto('/login')
  const chat = await openSupport(page)
  await expect(chat.getByText('SmallBiz 小助手', { exact: true })).toBeVisible()

  // 过时的「批量导入正在开发中」已改为实际做法
  await askQuestion(page, '怎么批量导入商品')
  await expect(chat.getByText(/先下载导入模板/)).toBeVisible()
  await expect(chat.getByText(/正在开发中/)).toHaveCount(0)

  // 新增话术：条码扫码
  await askQuestion(page, '扫码枪怎么用')
  await expect(chat.getByText(/扫码枪直接扫/)).toBeVisible()
  await expect(chat.getByText('AI 生成，仅供参考')).toHaveCount(0)
})

test('AI 回答：显示 AI 标识和按钮，追问带上历史，答不了转人工', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', '客服流程在桌面项目中验证')
  const asked: { message: string; history: { role: string; text: string }[]; lang: string }[] = []
  await page.route('**/api/v1/support/welcome**', (route) =>
    route.fulfill({ json: { greeting: '你好！我是 SmallBiz AI 小助手', quickReplies: [], ai: true } }),
  )
  await page.route('**/api/v1/support/ask', async (route) => {
    const body = route.request().postDataJSON()
    asked.push(body)
    if (asked.length === 1) {
      await route.fulfill({
        json: { matched: true, faqId: null, question: null, answer: '标准版 6.99 元/月，年付 69.9 元。', actionLabel: '查看套餐', actionPath: '/settings/billing', handoff: null, source: 'AI', logId: 101 },
      })
    } else if (asked.length === 2) {
      await route.fulfill({
        json: { matched: true, faqId: null, question: null, answer: '专业版 9.9 元/月。', actionLabel: null, actionPath: null, handoff: null, source: 'AI', logId: 102 },
      })
    } else {
      await route.fulfill({
        json: { matched: false, faqId: null, question: null, answer: '退款需要人工客服处理。', actionLabel: null, actionPath: null, handoff: { wechatId: 'TEST_WECHAT', tip: '添加时请备注：店铺名 + 问题简述' }, source: 'AI', logId: 103 },
      })
    }
  })
  let rated: unknown = null
  await page.route('**/api/v1/support/ai/101/feedback', async (route) => {
    rated = route.request().postDataJSON()
    await route.fulfill({ status: 204 })
  })

  await page.goto('/login')
  const chat = await openSupport(page)
  await expect(chat.getByText('SmallBiz AI 小助手', { exact: true })).toBeVisible()
  await expect(chat.getByText('根据帮助文档回答 · 答不上来会转人工')).toBeVisible()

  await askQuestion(page, '一个月多少钱')
  await expect(chat.getByText('标准版 6.99 元/月，年付 69.9 元。')).toBeVisible()
  await expect(chat.getByText('AI 生成，仅供参考')).toBeVisible()
  await expect(chat.getByRole('button', { name: '查看套餐 →' })).toBeVisible()
  await chat.getByRole('button', { name: '有帮助', exact: true }).click()
  await expect.poll(() => rated).toEqual({ helpful: true })

  // 追问：带上前面的对话（不含欢迎语）
  await askQuestion(page, '那专业版呢')
  await expect(chat.getByText('专业版 9.9 元/月。')).toBeVisible()
  expect(asked[1].message).toBe('那专业版呢')
  expect(asked[1].lang).toBe('zh-CN')
  expect(asked[1].history).toEqual([
    { role: 'user', text: '一个月多少钱' },
    { role: 'bot', text: '标准版 6.99 元/月，年付 69.9 元。' },
  ])

  // 答不了：显示 AI 的简短说明 + 客服微信，不显示「AI 生成」和评价
  await askQuestion(page, '我要退款')
  await expect(chat.getByText('退款需要人工客服处理。')).toBeVisible()
  await expect(chat.getByText('TEST_WECHAT')).toBeVisible()
  await expect(chat.getByText('这个问题我还答不好，', { exact: false })).toHaveCount(0)
  await expect(chat.getByText('AI 生成，仅供参考')).toHaveCount(2)
})
