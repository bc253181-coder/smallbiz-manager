import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { History, X } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { productApi } from '@/features/catalog/api'
import { MOVEMENT_LABELS, type MovementType } from '@/features/catalog/types'
import { formatDateTime, useMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { inventoryApi } from '../api'

export function MovementsPage() {
  const [params, setParams] = useSearchParams()
  const money = useMoney()
  const productId = params.get('productId') ? Number(params.get('productId')) : undefined
  const type = (params.get('type') as MovementType | null) ?? undefined
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Number(params.get('page') ?? 0)

  function update(changes: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  const product = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productApi.get(productId!),
    enabled: productId !== undefined,
  })
  const invalidRange = !!(from && to && from > to)
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['inventory', 'movements', { productId, type, from, to, page }],
    queryFn: () => inventoryApi.movements({ productId, type, from, to, page, size: 20 }),
    placeholderData: keepPreviousData,
    enabled: !invalidRange,
  })
  const showCost = data?.items.some((m) => m.unitCost !== null) ?? false
  const filtered = !!(productId || type || from || to)

  return (
    <div>
      <PageHeader title="库存流水" description="每一次库存变动的记录，不可修改或删除" back={{ to: '/inventory', label: '返回库存' }} />

      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 lg:flex-row lg:items-end">
          {productId !== undefined && (
            <div className="flex h-10 items-center gap-2 rounded-lg bg-blue-50 px-3 text-sm text-blue-800">
              <span className="max-w-56 truncate">商品：{product.data?.name ?? `#${productId}`}</span>
              <button type="button" onClick={() => update({ productId: null })} aria-label="取消按商品筛选" className="rounded p-0.5 hover:bg-blue-100">
                <X className="size-4" />
              </button>
            </div>
          )}
          <div className="grid flex-1 grid-cols-1 gap-2 sm:grid-cols-3">
            <label className="text-xs text-slate-500">
              类型
              <Select value={type ?? ''} onChange={(e) => update({ type: e.target.value || null })} className="mt-1">
                <option value="">全部类型</option>
                {(Object.keys(MOVEMENT_LABELS) as MovementType[]).map((t) => (
                  <option key={t} value={t}>
                    {MOVEMENT_LABELS[t]}
                  </option>
                ))}
              </Select>
            </label>
            <label className="text-xs text-slate-500">
              开始日期
              <Input type="date" value={from} max={to || undefined} onChange={(e) => update({ from: e.target.value || null })} className="mt-1" />
            </label>
            <label className="text-xs text-slate-500">
              结束日期
              <Input type="date" value={to} min={from || undefined} onChange={(e) => update({ to: e.target.value || null })} className="mt-1" />
            </label>
          </div>
          {filtered && (
            <Button variant="ghost" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
              清除筛选
            </Button>
          )}
        </div>

        {invalidRange ? (
          <p className="p-6 text-center text-sm text-red-600">开始日期不能晚于结束日期</p>
        ) : isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          <EmptyState icon={History} title={filtered ? '没有符合条件的记录' : '还没有库存变动'} />
        ) : (
          data && (
            <div className={cn(isFetching && 'opacity-60')}>
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full min-w-[760px] text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                    <tr>
                      <th className="px-4 py-3">时间</th>
                      <th className="px-4 py-3">商品</th>
                      <th className="px-4 py-3">类型</th>
                      <th className="px-4 py-3 text-right">变动</th>
                      <th className="px-4 py-3 text-right">变动后</th>
                      {showCost && <th className="px-4 py-3 text-right">单价</th>}
                      <th className="px-4 py-3">备注</th>
                      <th className="px-4 py-3">操作人</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.items.map((m) => (
                      <tr key={m.id}>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(m.createdAt)}</td>
                        <td className="px-4 py-3">
                          {m.productSku ? (
                            <Link to={`/products/${m.productId}`} className="font-medium text-slate-900 hover:text-blue-600">
                              {m.productName}
                            </Link>
                          ) : (
                            <span className="text-slate-400">{m.productName}</span>
                          )}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">{MOVEMENT_LABELS[m.type]}</td>
                        <td className={cn('px-4 py-3 text-right font-medium tabular-nums', m.qtyChange > 0 ? 'text-emerald-700' : 'text-red-600')}>
                          {m.qtyChange > 0 ? `+${m.qtyChange}` : m.qtyChange}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{m.qtyAfter}</td>
                        {showCost && <td className="px-4 py-3 text-right tabular-nums text-slate-600">{money(m.unitCost)}</td>}
                        <td className="px-4 py-3 text-slate-600">{m.reason ?? '—'}</td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{m.operatorName ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((m) => (
                  <li key={m.id} className="flex items-start justify-between gap-3 p-4">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-slate-900">{m.productName}</p>
                      <p className="text-xs text-slate-500">
                        {MOVEMENT_LABELS[m.type]} · {formatDateTime(m.createdAt)}
                      </p>
                      {m.reason && <p className="mt-0.5 truncate text-xs text-slate-500">{m.reason}</p>}
                    </div>
                    <div className="text-right">
                      <p className={cn('font-semibold tabular-nums', m.qtyChange > 0 ? 'text-emerald-700' : 'text-red-600')}>
                        {m.qtyChange > 0 ? `+${m.qtyChange}` : m.qtyChange}
                      </p>
                      <p className="text-xs text-slate-500">余 {m.qtyAfter}</p>
                    </div>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination
                  page={data.page}
                  totalPages={data.totalPages}
                  totalItems={data.totalItems}
                  onChange={(p) => update({ page: String(p) }, false)}
                />
              </div>
            </div>
          )
        )}
      </Card>
    </div>
  )
}
