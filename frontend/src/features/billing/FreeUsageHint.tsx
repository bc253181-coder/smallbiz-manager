import { Info } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '@/lib/utils'
import { tr } from '@/lib/i18n'
import { usePlan } from './api'

/**
 * 普通会员的次数提示（透明展示）：「已新建 2 / 3 个商品」。高级会员或旧订阅有效期内不显示。
 * kind：stores 店铺 / products 商品。
 */
export function FreeUsageHint({ kind, className }: { kind: 'stores' | 'products'; className?: string }) {
  const { data } = usePlan()
  const m = data?.membership
  if (!m) return null
  const used = kind === 'stores' ? m.storesUsed : m.productsUsed
  const max = kind === 'stores' ? m.storesMax : m.productsMax
  if (max === null) return null
  const full = used >= max
  return (
    <p
      className={cn(
        'flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border px-3 py-2 text-sm',
        full ? 'border-amber-200 bg-amber-50 text-amber-800' : 'border-slate-200 bg-slate-50 text-slate-600',
        className,
      )}
      data-testid={`free-usage-${kind}`}
    >
      <Info className="size-4 shrink-0" aria-hidden />
      {kind === 'stores'
        ? tr('普通会员：已创建 {{used}} / {{max}} 家店铺', { used, max })
        : tr('普通会员：已新建 {{used}} / {{max}} 个商品（删除不返还次数）', { used, max })}
      <Link to="/settings/billing" className="font-medium underline">
        {full ? tr('立即升级') : tr('了解高级会员')}
      </Link>
    </p>
  )
}
