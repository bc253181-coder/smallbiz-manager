/**
 * 界面多语言（简体中文 / English）。
 *
 * <p>做法：代码里直接写中文原文，用 {@link tr} 包起来；中文原文本身就是翻译词典的键。
 * 中文用户不需要词典；切到英文时先加载英文词典（单独打包，按需下载），再加载整个应用。
 * 切换语言会刷新页面，因此模块顶层定义的文字（如状态名称表）也会是正确的语言。
 *
 * <p>英文词典里没有的文字会显示中文原文（开发时在控制台提示），并由检查脚本在构建时报错。
 */

export type Lang = 'zh-CN' | 'en'

export const LANGS: { code: Lang; label: string }[] = [
  { code: 'zh-CN', label: '简体中文' },
  { code: 'en', label: 'English' },
]

const STORAGE_KEY = 'sb:lang'

let lang: Lang = 'zh-CN'
let dict: Record<string, string> = {}

/** 当前语言：本地保存的选择 → 浏览器语言（中文以外的都用英文）。 */
function detect(): Lang {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'zh-CN' || saved === 'en') return saved
  } catch {
    // 无痕模式等：按浏览器语言
  }
  const nav = (navigator.languages?.[0] ?? navigator.language ?? 'zh-CN').toLowerCase()
  return nav.startsWith('zh') ? 'zh-CN' : 'en'
}

/** 应用启动前调用：确定语言并加载词典。 */
export async function initI18n() {
  lang = detect()
  if (lang === 'en') {
    dict = (await import('@/locales/en.json')).default as Record<string, string>
  }
  document.documentElement.lang = lang
}

export function getLang(): Lang {
  return lang
}

/** 切换语言：保存后刷新页面。 */
export function setLang(next: Lang, reload = true) {
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // 忽略
  }
  if (reload && next !== lang) window.location.reload()
}

/** 用户的语言保存在账号上：登录后与本地不一致时以账号为准。返回是否需要刷新。 */
export function syncLangFromAccount(accountLang: string | null | undefined): boolean {
  if ((accountLang === 'zh-CN' || accountLang === 'en') && accountLang !== lang) {
    setLang(accountLang, false)
    return true
  }
  return false
}

const warned = new Set<string>()

/**
 * 翻译。key 是中文原文；{{name}} 为变量。
 * 例：tr('已收款 {{amount}}', { amount: '¥10.00' })
 */
export function tr(key: string, vars?: Record<string, string | number | null | undefined>): string {
  let s = key
  if (lang !== 'zh-CN') {
    const hit = dict[key]
    if (hit !== undefined) {
      s = hit
    } else if (import.meta.env.DEV && !warned.has(key)) {
      warned.add(key)
      console.warn('[i18n] 缺少英文翻译：', key)
    }
  }
  if (vars) {
    s = s.replace(/\{\{(\w+)\}\}/g, (m, k: string) => (k in vars ? String(vars[k] ?? '') : m))
  }
  return s
}

/** 有没有这条翻译（用于后端错误提示：没有逐字翻译时退回按错误代码的通用提示）。 */
export function hasTranslation(key: string): boolean {
  return lang === 'zh-CN' || dict[key] !== undefined
}

/** 日期、数字的地区格式。 */
export function locale(): string {
  return lang === 'en' ? 'en-GB' : 'zh-CN'
}
