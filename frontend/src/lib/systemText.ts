import { serverText } from './api'
import { getLang, tr } from './i18n'

/**
 * 系统自动生成并保存在数据库里的中文（库存流水的原因、默认分类名等）：英文界面下按固定格式翻译显示。
 * 用户自己填写的原因、备注原样显示。
 */
const RULES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^新建商品时的初始库存$/, () => 'Opening stock when the product was created'],
  [/^取消销售 (\S+)$/, (m) => `Cancelled sale ${m[1]}`],
  [/^销售 (\S+)$/, (m) => `Sale ${m[1]}`],
  [/^采购收货 (\S+)$/, (m) => `Purchase received ${m[1]}`],
  [/^调拨 (\S+)：(.+) → (.+)$/, (m) => `Transfer ${m[1]}: ${warehouseName(m[2])} → ${warehouseName(m[3])}`],
]

export function systemText(text: string | null | undefined): string {
  if (!text) return text ?? ''
  if (getLang() === 'zh-CN') return text
  for (const [re, fmt] of RULES) {
    const m = text.match(re)
    if (m) return fmt(m)
  }
  return serverText(text)
}

/** 默认仓库在中文店铺里叫「主仓库」（系统创建）：英文界面显示为 Main warehouse。 */
export function warehouseName(name: string | null | undefined): string {
  if (!name) return name ?? ''
  return name === '主仓库' ? tr('主仓库') : name
}
