import { useAuth } from '@/features/auth/AuthProvider'
import { CURRENCY_SYMBOLS } from './currency'
import { locale } from './i18n'

/** 金额显示（后端以字符串传输，如 "19.90"）。只用于显示，不参与计算。 */
export function formatMoney(value: string | null | undefined, currency = 'CNY'): string {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return value
  const symbol = CURRENCY_SYMBOLS[currency] ?? `${currency} `
  return symbol + n.toLocaleString(locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 按当前店铺币种格式化金额。 */
export function useMoney() {
  const { session } = useAuth()
  const currency = session?.currentTenant?.currency ?? 'CNY'
  return (value: string | null | undefined) => formatMoney(value, currency)
}

/** 中文：2026/11/09 14:30；英文：09 Nov 2026, 14:30。 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString(
    locale(),
    locale() === 'zh-CN'
      ? { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }
      : { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false },
  )
}

/** 只显示日期。中文：2026/11/09；英文：09 Nov 2026。 */
export function formatDay(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString(
    locale(),
    locale() === 'zh-CN' ? { year: 'numeric', month: '2-digit', day: '2-digit' } : { year: 'numeric', month: 'short', day: '2-digit' },
  )
}

export function formatQty(n: number): string {
  return n.toLocaleString(locale())
}
