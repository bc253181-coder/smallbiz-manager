import { AxiosError } from 'axios'
import { productApi } from '@/features/catalog/api'
import type { Product } from '@/features/catalog/types'

/**
 * 输入内容是否像扫码枪扫出来的条码 / SKU：没有空格和中文，长度 ≥ 4。
 * 商品名称（含中文或空格）仍走普通搜索。
 */
export function looksLikeCode(text: string): boolean {
  return /^[A-Za-z0-9._\-/]{4,64}$/.test(text.trim())
}

/** 按条码精确查找商品；找不到返回 null（其他错误照常抛出）。 */
export async function findByCode(code: string): Promise<Product | null> {
  try {
    return await productApi.lookup(code.trim())
  } catch (e) {
    if (e instanceof AxiosError && e.response?.status === 404) return null
    throw e
  }
}

/** 扫到商品时轻微震动（手机支持时）。 */
export function vibrate() {
  try {
    navigator.vibrate?.(60)
  } catch {
    // 不支持就算了
  }
}
