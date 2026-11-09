import { useQuery } from '@tanstack/react-query'
import { Bell, CheckCircle2 } from 'lucide-react'
import { Popover } from 'radix-ui'
import { useState } from 'react'
import { Link } from 'react-router'
import { ProductThumb } from '@/features/catalog/components/ProductThumb'
import { inventoryApi } from '@/features/inventory/api'
import { cn } from '@/lib/utils'

import { tr } from '@/lib/i18n'
/** 顶栏通知：在售商品中的低库存提醒。 */
export function LowStockBell() {
  const [open, setOpen] = useState(false)
  const { data } = useQuery({
    queryKey: ['inventory', 'low-stock', 5],
    queryFn: () => inventoryApi.lowStock(5),
    refetchInterval: 60_000,
  })
  const count = data?.count ?? 0

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger
        className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
        aria-label={count > 0 ? tr('通知：{{count}} 件商品库存不足', { count: count }) : tr('通知')}
      >
        <Bell className="size-5" />
        {count > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={8}
          className="z-50 w-[min(20rem,calc(100vw-2rem))] rounded-xl border border-slate-200 bg-white shadow-lg"
        >
          <div className="border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-900">{tr('库存提醒')}</p>
            <p className="text-xs text-slate-500">{count > 0 ? tr('{{count}} 件在售商品库存不高于预警值', { count: count }) : tr('所有在售商品库存充足')}</p>
          </div>
          {count === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-8 text-sm text-slate-500">
              <CheckCircle2 className="size-8 text-emerald-500" aria-hidden />
              {tr('暂无需要补货的商品')}
            </div>
          ) : (
            <ul className="max-h-80 divide-y divide-slate-100 overflow-y-auto">
              {data?.items.map((p) => (
                <li key={p.id}>
                  <Link to={`/products/${p.id}`} onClick={() => setOpen(false)} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
                    <ProductThumb url={p.imageUrl} name={p.name} className="size-9" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{p.name}</p>
                      <p className="text-xs text-slate-500">{tr('预警值')} {p.lowStockThreshold}</p>
                    </div>
                    <span className={cn('whitespace-nowrap text-sm font-semibold tabular-nums', p.stockQty <= 0 ? 'text-red-600' : 'text-amber-700')}>
                      {p.stockQty <= 0 ? tr('缺货') : tr('剩 {{stockQty}} {{unit}}', { stockQty: p.stockQty, unit: p.unit })}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {count > 0 && (
            <Link
              to="/inventory?lowStock=true"
              onClick={() => setOpen(false)}
              className="block border-t border-slate-100 px-4 py-2.5 text-center text-sm font-medium text-blue-600 hover:bg-slate-50"
            >
              {tr('查看全部低库存商品')}
            </Link>
          )}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}
