import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Plus, Search, Truck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { formatDate } from '@/lib/utils'
import { ExcelExportButton } from '@/features/excel/ExcelExportButton'
import { useMoney } from '@/lib/format'
import { useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { PURCHASE_STATUS_LABELS, purchaseApi, type PurchaseStatus } from '../api'
import { PurchaseStatusBadge } from '../PurchaseStatusBadge'

import { tr } from '@/lib/i18n'
const TABS: (PurchaseStatus | '')[] = ['', 'DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED']

export function PurchaseListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const money = useMoney()
  const q = params.get('q') ?? ''
  const status = (params.get('status') as PurchaseStatus | null) ?? undefined
  const page = Number(params.get('page') ?? 0)

  const [search, setSearch] = useState(q)
  const debounced = useDebouncedValue(search)
  useEffect(() => {
    if (debounced !== q) update({ q: debounced || null })
  }, [debounced]) // eslint-disable-line react-hooks/exhaustive-deps

  function update(changes: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['purchases', { q, status, page }],
    queryFn: () => purchaseApi.list({ q, status, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  return (
    <div>
      <PageHeader
        title={tr('采购')}
        description={tr('向供应商下单进货，确认收货后自动入库并更新成本')}
        actions={
          <>
            <ExcelExportButton path="/exports/purchases" params={{ q, status }} fallbackName={tr('采购.xlsx')} />
            <Link to="/purchases/new" className={buttonVariants()}>
              <Plus aria-hidden />
              {tr('新建采购单')}
            </Link>
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4">
          <div className="-mx-1 flex gap-1 overflow-x-auto px-1" role="tablist" aria-label={tr('按状态筛选')}>
            {TABS.map((t) => (
              <button
                key={t || 'all'}
                type="button"
                role="tab"
                aria-selected={(status ?? '') === t}
                onClick={() => update({ status: t || null })}
                className={cn(
                  'whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium',
                  (status ?? '') === t ? 'bg-blue-50 text-blue-700' : 'text-slate-600 hover:bg-slate-100',
                )}
              >
                {t ? PURCHASE_STATUS_LABELS[t] : tr('全部')}
              </button>
            ))}
          </div>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder={tr('搜索单号或供应商')} className="pl-9" aria-label={tr('搜索采购单')} />
          </div>
        </div>

        {isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          q || status ? (
            <EmptyState icon={Search} title={tr('没有符合条件的采购单')} />
          ) : (
            <EmptyState
              icon={Truck}
              title={tr('还没有采购单')}
              description={tr('向供应商进货时新建采购单。到货后点「确认收货」，库存会自动增加。')}
              action={
                <Link to="/purchases/new" className={buttonVariants()}>
                  <Plus aria-hidden />
                  {tr('新建采购单')}
                </Link>
              }
            />
          )
        ) : (
          data && (
            <div className={cn(isFetching && 'opacity-60')}>
              <table className="hidden w-full text-sm md:table">
                <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                  <tr>
                    <th className="px-4 py-3">{tr('单号')}</th>
                    <th className="px-4 py-3">{tr('供应商')}</th>
                    <th className="px-4 py-3">{tr('下单日期')}</th>
                    <th className="px-4 py-3 text-right">{tr('商品数')}</th>
                    <th className="px-4 py-3 text-right">{tr('金额')}</th>
                    <th className="px-4 py-3">{tr('状态')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.items.map((o) => (
                    <tr key={o.id} onClick={() => navigate(`/purchases/${o.id}`)} className="cursor-pointer hover:bg-slate-50">
                      <td className="whitespace-nowrap px-4 py-3">
                        <Link to={`/purchases/${o.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-slate-900 hover:text-blue-600">
                          {o.orderNo}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-slate-700">{o.supplierName}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDate(o.orderDate)}</td>
                      <td className="px-4 py-3 text-right tabular-nums text-slate-600">
                        {o.itemCount} {tr('种 /')} {o.totalQuantity} {tr('件')}
                      </td>
                      <td className="px-4 py-3 text-right font-medium tabular-nums">{money(o.totalAmount)}</td>
                      <td className="px-4 py-3">
                        <PurchaseStatusBadge status={o.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((o) => (
                  <li key={o.id}>
                    <Link to={`/purchases/${o.id}`} className="flex items-start justify-between gap-3 p-4 active:bg-slate-50">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-900">{o.supplierName}</p>
                        <p className="text-xs text-slate-500">
                          {o.orderNo} · {formatDate(o.orderDate)}
                        </p>
                        <p className="text-xs text-slate-500">
                          {o.itemCount} {tr('种 /')} {o.totalQuantity} {tr('件')}
                        </p>
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <span className="font-semibold tabular-nums">{money(o.totalAmount)}</span>
                        <PurchaseStatusBadge status={o.status} />
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} unit={tr('张采购单')} onChange={(p) => update({ page: String(p) }, false)} />
              </div>
            </div>
          )
        )}
      </Card>
    </div>
  )
}
