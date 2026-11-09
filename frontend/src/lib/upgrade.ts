/**
 * 升级提示：服务端拒绝（达到普通会员次数上限 / 使用高级功能）时弹出升级窗口。
 * 状态放在模块里，任何地方都能打开（例如全局的请求错误处理）。
 */
import { useSyncExternalStore } from 'react'

export type UpgradeReason =
  | { kind: 'stores'; max?: number }
  | { kind: 'products'; max?: number }
  | { kind: 'reportDays'; days?: number }
  | { kind: 'feature'; feature?: string }

let current: UpgradeReason | null = null
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((l) => l())
}

export function openUpgrade(reason: UpgradeReason) {
  current = reason
  emit()
}

export function closeUpgrade() {
  current = null
  emit()
}

export function useUpgradeReason(): UpgradeReason | null {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => current,
    () => null,
  )
}

/** 从服务端错误详情得到升级原因。 */
export function upgradeReasonFromError(code: string | undefined, details: Record<string, unknown> | undefined): UpgradeReason | null {
  if (code === 'PLAN_LIMIT_REACHED') {
    const max = typeof details?.max === 'number' ? details.max : undefined
    return details?.limit === 'stores' ? { kind: 'stores', max } : { kind: 'products', max }
  }
  if (code === 'PLAN_FEATURE_REQUIRED') {
    if (typeof details?.reportDays === 'number') return { kind: 'reportDays', days: details.reportDays }
    return { kind: 'feature', feature: typeof details?.feature === 'string' ? details.feature : undefined }
  }
  return null
}
