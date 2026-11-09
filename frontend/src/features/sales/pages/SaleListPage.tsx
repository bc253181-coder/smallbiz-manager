import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Plus, ReceiptText, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { ExcelExportButton } from '@/features/excel/ExcelExportButton'
import { formatDateTime, useMoney } from '@/lib/format'
import { useCanManage, useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { saleApi, type SaleStatus } from '../api'
import { SaleStatusBadge } from '../SaleStatusBadge'

const TABS: { value: SaleStatus | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

export function SaleListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const money = useMoney()
  const q = params.get('q') ?? ''
  const status = (params.get('status') as SaleStatus | null) ?? undefined
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Number(params.get('page') ?? 0)
  const createdBy = params.get('createdBy') ? Number(params.get('createdBy')) : undefined
  const canManage = useCanManage()
  // 只有一个人开过单时不显示开单人筛选和列
  const creators = useQuery({ queryKey: ['sales', 'creators'], queryFn: saleApi.creators, enabled: canManage })
  const showCreator = canManage && (creators.data?.length ?? 0) > 1

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

  const invalidRange = !!(from && to && from > to)
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['sales', { q, status, from, to, createdBy, page }],
    queryFn: () => saleApi.list({ q, status, from, to, createdBy, page, size: 20 }),
    placeholderData: keepPreviousData,
    enabled: !invalidRange,
  })
  const filtered = !!(q || status || from || to || createdBy)

  return (
    <div>
      <PageHeader
        title="销售"
        description="开单记录。已取消的订单不计入任何统计。"
        actions={
          <>
            <ExcelExportButton
              path="/exports/sales"
              params={{ q, status, from, to, createdBy }}
              fallbackName="销售.xlsx"
            />
            <Link to="/sales/new" className={buttonVariants()}>
              <Plus aria-hidden />
              开单
            </Link>
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4">
          <div className="flex gap-1" role="tablist" aria-label="按状态筛选">
            {TABS.map((t) => (
              <button
                key={t.value || 'all'}
                type="button"
                role="tab"
                aria-selected={(status ?? '') === t.value}
                onClick={() => update({ status: t.value || null })}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-sm font-medium',
                  (status ?? '') === t.value ? 'bg-blue-50 text-blue-700' : 'text-slate-600 hover:bg-slate-100',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
          <div className={cn('grid gap-2', showCreator ? 'sm:grid-cols-2 lg:grid-cols-[1fr_9rem_10rem_10rem]' : 'sm:grid-cols-[1fr_10rem_10rem]')}>
            <div className={cn('relative', showCreator && 'sm:col-span-2 lg:col-span-1')}>
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
              <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="搜索单号或客户" className="pl-9" aria-label="搜索销售单" />
            </div>
            {showCreator && (
              <Select aria-label="开单人" value={createdBy ?? ''} onChange={(e) => update({ createdBy: e.target.value || null })}>
                <option value="">全部开单人</option>
                {creators.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </Select>
            )}
            <Input type="date" aria-label="开始日期" value={from} max={to || undefined} onChange={(e) => update({ from: e.target.value || null })} />
            <Input type="date" aria-label="结束日期" value={to} min={from || undefined} onChange={(e) => update({ to: e.target.value || null })} />
          </div>
        </div>

        {invalidRange ? (
          <p className="p-6 text-center text-sm text-red-600">开始日期不能晚于结束日期</p>
        ) : isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          filtered ? (
            <EmptyState icon={Search} title="没有符合条件的销售单" />
          ) : (
            <EmptyState
              icon={ReceiptText}
              title="还没有销售记录"
              description="开单后库存会自动扣减，销售额和毛利会计入报表"
              action={
                <Link to="/sales/new" className={buttonVariants()}>
                  <Plus aria-hidden />
                  开单
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
                    <th className="px-4 py-3">单号</th>
                    <th className="px-4 py-3">客户</th>
                    <th className="px-4 py-3">时间</th>
                    {showCreator && <th className="px-4 py-3">开单人</th>}
                    <th className="px-4 py-3 text-right">商品数</th>
                    <th className="px-4 py-3 text-right">金额</th>
                    <th className="px-4 py-3">状态</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.items.map((o) => (
                    <tr key={o.id} onClick={() => navigate(`/sales/${o.id}`)} className="cursor-pointer hover:bg-slate-50">
                      <td className="whitespace-nowrap px-4 py-3">
                        <Link to={`/sales/${o.id}`} onClick={(e) => e.stopPropagation()} className="font-medium text-slate-900 hover:text-blue-600">
                          {o.orderNo}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-slate-700">{o.customerName ?? <span className="text-slate-400">散客</span>}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(o.orderDate)}</td>
                      {showCreator && <td className="max-w-32 truncate px-4 py-3 text-slate-600">{o.createdByName ?? '—'}</td>}
                      <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums text-slate-600">{o.totalQuantity} 件</td>
                      <td className={cn('px-4 py-3 text-right font-medium tabular-nums', o.status === 'CANCELLED' && 'text-slate-400 line-through')}>
                        {money(o.totalAmount)}
                      </td>
                      <td className="px-4 py-3">
                        <SaleStatusBadge status={o.status} paymentStatus={o.paymentStatus} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((o) => (
                  <li key={o.id}>
                    <Link to={`/sales/${o.id}`} className="flex items-start justify-between gap-3 p-4 active:bg-slate-50">
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-900">{o.customerName ?? '散客'}</p>
                        <p className="text-xs text-slate-500">
                          {o.orderNo} · {formatDateTime(o.orderDate)}
                          {showCreator && o.createdByName && ` · ${o.createdByName}`}
                        </p>
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <span className={cn('font-semibold tabular-nums', o.status === 'CANCELLED' && 'text-slate-400 line-through')}>
                          {money(o.totalAmount)}
                        </span>
                        <SaleStatusBadge status={o.status} paymentStatus={o.paymentStatus} />
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} unit="张销售单" onChange={(p) => update({ page: String(p) }, false)} />
              </div>
            </div>
          )
        )}
      </Card>
    </div>
  )
}
