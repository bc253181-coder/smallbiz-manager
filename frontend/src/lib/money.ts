/**
 * 金额计算（仅用于界面上的实时预览；最终金额以后端计算为准）。
 * 一律换算成"分"做整数运算，避免 0.1 + 0.2 这类浮点误差。
 */

const MONEY = /^\d{1,12}(\.\d{1,2})?$/

export function isMoney(s: string): boolean {
  return MONEY.test(s.trim())
}

/** "12.3" → 1230；格式不正确返回 null。 */
export function toCents(s: string | null | undefined): number | null {
  if (s === null || s === undefined) return null
  const t = s.trim()
  if (!MONEY.test(t)) return null
  const [int, frac = ''] = t.split('.')
  return Number(int) * 100 + Number(frac.padEnd(2, '0'))
}

/** 1230 → "12.30" */
export function fromCents(cents: number): string {
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(cents)
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}
