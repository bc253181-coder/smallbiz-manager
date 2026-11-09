import { useQuery } from '@tanstack/react-query'
import { Plus, ReceiptText } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { CardHeader } from '@/components/ui/card'
import { EmptyState, ErrorState } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { formatDateTime, useMoney } from '@/lib/format'
import { cn, formatDate } from '@/lib/utils'
import { saleApi } from './api'
import { SaleStatusBadge } from './SaleStatusBadge'

import { tr } from '@/lib/i18n'
/** 客户详情页：累计消费 + 历史订单。 */
export function CustomerSalesHistory({ customerId }: { customerId: number }) {
  const money = useMoney()
  const [page, setPage] = useState(0)
  const stats = useQuery({ queryKey: ['customer-stats', customerId], queryFn: () => saleApi.customerStats(customerId) })
  const orders = useQuery({
    queryKey: ['sales', { customerId, page }],
    queryFn: () => saleApi.list({ customerId, page, size: 10 }),
  })

  return (
    <>
      <CardHeader
        title={tr('购买记录')}
        action={
          <Link to={`/sales/new?customerId=${customerId}`} className={buttonVariants({ size: 'sm' })}>
            <Plus aria-hidden />
            {tr('给他开单')}
          </Link>
        }
      />
      <div className="grid grid-cols-3 divide-x divide-slate-100 border-b border-slate-100 text-center">
        <Stat label={tr('订单数')} value={stats.data ? String(stats.data.orderCount) : undefined} />
        <Stat label={tr('累计消费')} value={stats.data ? money(stats.data.totalSpent) : undefined} />
        <Stat label={tr('最近购买')} value={stats.data ? (stats.data.lastOrderAt ? formatDate(stats.data.lastOrderAt) : '—') : undefined} />
      </div>
      {orders.isLoading ? (
        <div className="h-32 animate-pulse bg-slate-50" />
      ) : orders.isError ? (
        <ErrorState onRetry={() => void orders.refetch()} />
      ) : !orders.data || orders.data.items.length === 0 ? (
        <EmptyState icon={ReceiptText} title={tr('还没有购买记录')} description={tr('开单时选择这位客户，记录会显示在这里')} />
      ) : (
        <>
          <ul className="divide-y divide-slate-100">
            {orders.data.items.map((o) => (
              <li key={o.id}>
                <Link to={`/sales/${o.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-900">{o.orderNo}</p>
                    <p className="text-xs text-slate-500">
                      {formatDateTime(o.orderDate)} · {o.totalQuantity} {tr('件')}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={cn('text-sm font-medium tabular-nums', o.status === 'CANCELLED' && 'text-slate-400 line-through')}>
                      {money(o.totalAmount)}
                    </span>
                    <SaleStatusBadge status={o.status} paymentStatus={o.paymentStatus} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <div className="border-t border-slate-100 px-5 py-3">
            <Pagination page={orders.data.page} totalPages={orders.data.totalPages} totalItems={orders.data.totalItems} unit={tr('张')} onChange={setPage} />
          </div>
        </>
      )}
      <p className="px-5 pb-4 text-xs text-slate-500">{tr('累计消费不含已取消的订单。')}</p>
    </>
  )
}

function Stat({ label, value }: { label: string; value?: string }) {
  return (
    <div className="px-2 py-3">
      <p className="text-xs text-slate-500">{label}</p>
      {value === undefined ? (
        <div className="mx-auto mt-1 h-5 w-12 animate-pulse rounded bg-slate-100" />
      ) : (
        <p className="mt-0.5 truncate text-sm font-semibold tabular-nums text-slate-900">{value}</p>
      )}
    </div>
  )
}
