/** 店铺可选的币种（与后端 Currencies 一致）。 */
export const CURRENCIES = [
  { code: 'CNY', symbol: '¥', name: '人民币', timezone: 'Asia/Shanghai' },
  { code: 'MYR', symbol: 'RM ', name: '马来西亚令吉', timezone: 'Asia/Kuala_Lumpur' },
  { code: 'SGD', symbol: 'S$', name: '新加坡元', timezone: 'Asia/Singapore' },
  { code: 'USD', symbol: '$', name: '美元', timezone: 'America/New_York' },
  { code: 'HKD', symbol: 'HK$', name: '港币', timezone: 'Asia/Hong_Kong' },
  { code: 'TWD', symbol: 'NT$', name: '新台币', timezone: 'Asia/Taipei' },
  { code: 'THB', symbol: '฿', name: '泰铢', timezone: 'Asia/Bangkok' },
] as const

export type CurrencyCode = (typeof CURRENCIES)[number]['code']

export const CURRENCY_SYMBOLS: Record<string, string> = Object.fromEntries(CURRENCIES.map((c) => [c.code, c.symbol]))

/** 开店时的默认币种：按浏览器所在时区猜测，猜不到用人民币。 */
export function guessCurrency(): CurrencyCode {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
    const byTz: Record<string, CurrencyCode> = {
      'Asia/Kuala_Lumpur': 'MYR',
      'Asia/Kuching': 'MYR',
      'Asia/Singapore': 'SGD',
      'Asia/Hong_Kong': 'HKD',
      'Asia/Taipei': 'TWD',
      'Asia/Bangkok': 'THB',
    }
    return byTz[tz] ?? 'CNY'
  } catch {
    return 'CNY'
  }
}
