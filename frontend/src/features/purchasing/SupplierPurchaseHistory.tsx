import { useQuery } from '@tanstack/react-query'
import { Plus, Truck } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { CardHeader } from '@/components/ui/card'
import { EmptyState, ErrorState } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { useMoney } from '@/lib/format'
import { cn, formatDate } from '@/lib/utils'
import { purchaseApi } from './api'
import { PurchaseStatusBadge } from './PurchaseStatusBadge'

import { tr } from '@/lib/i18n'
/** 供应商详情页：采购记录 + 供应过的商品。 */
export function SupplierPurchaseHistory({ supplierId }: { supplierId: number }) {
  const money = useMoney()
  const [page, setPage] = useState(0)
  const [tab, setTab] = useState<'orders' | 'products'>('orders')
  const orders = useQuery({
    queryKey: ['purchases', { supplierId, page }],
    queryFn: () => purchaseApi.list({ supplierId, page, size: 10 }),
  })
  const products = useQuery({
    queryKey: ['supplier-products', supplierId],
    queryFn: () => purchaseApi.suppliedProducts(supplierId),
    enabled: tab === 'products',
  })

  return (
    <>
      <CardHeader
        title={tr('采购记录')}
        action={
          <Link to={`/purchases/new?supplierId=${supplierId}`} className={buttonVariants({ size: 'sm' })}>
            <Plus aria-hidden />
            {tr('新建采购单')}
          </Link>
        }
      />
      <div className="flex gap-1 border-b border-slate-100 px-4 py-2" role="tablist">
        {(['orders', 'products'] as const).map((t) => (
          <button
            key={t}
            type="button"
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn('rounded-lg px-3 py-1.5 text-sm font-medium', tab === t ? 'bg-blue-50 text-blue-700' : 'text-slate-600 hover:bg-slate-100')}
          >
            {t === 'orders' ? tr('采购单') : tr('供应过的商品')}
          </button>
        ))}
      </div>

      {tab === 'orders' ? (
        orders.isLoading ? (
          <div className="h-32 animate-pulse bg-slate-50" />
        ) : orders.isError ? (
          <ErrorState onRetry={() => void orders.refetch()} />
        ) : !orders.data || orders.data.items.length === 0 ? (
          <EmptyState icon={Truck} title={tr('还没有向该供应商采购过')} />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {orders.data.items.map((o) => (
                <li key={o.id}>
                  <Link to={`/purchases/${o.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-slate-900">{o.orderNo}</p>
                      <p className="text-xs text-slate-500">
                        {formatDate(o.orderDate)} · {o.itemCount} {tr('种 /')} {o.totalQuantity} {tr('件')}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-medium tabular-nums">{money(o.totalAmount)}</span>
                      <PurchaseStatusBadge status={o.status} />
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
            <div className="border-t border-slate-100 px-5 py-3">
              <Pagination page={orders.data.page} totalPages={orders.data.totalPages} totalItems={orders.data.totalItems} unit={tr('张')} onChange={setPage} />
            </div>
          </>
        )
      ) : products.isLoading ? (
        <div className="h-32 animate-pulse bg-slate-50" />
      ) : products.isError ? (
        <ErrorState onRetry={() => void products.refetch()} />
      ) : !products.data || products.data.length === 0 ? (
        <EmptyState icon={Truck} title={tr('暂无已收货的商品')} description={tr('采购单确认收货后，这里会列出该供应商供应过的商品')} />
      ) : (
        <ul className="divide-y divide-slate-100">
          {products.data.map((p) => (
            <li key={p.productId}>
              <Link to={`/products/${p.productId}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">{p.productName}</p>
                  <p className="text-xs text-slate-500">
                    {p.sku} {tr('· 采购')} {p.orderCount} {tr('次 · 最近')} {formatDate(p.lastReceivedAt)}
                  </p>
                </div>
                <span className="whitespace-nowrap text-sm tabular-nums text-slate-700">
                  {tr('累计')} {p.totalQuantity} {p.unit}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
