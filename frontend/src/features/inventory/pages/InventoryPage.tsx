import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowDownToLine, ArrowUpFromLine, ClipboardCheck, History, Search, Warehouse } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { productApi } from '@/features/catalog/api'
import { ProductThumb } from '@/features/catalog/components/ProductThumb'
import type { Product } from '@/features/catalog/types'
import { formatQty, useMoney } from '@/lib/format'
import { useCanManage, useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { inventoryApi } from '../api'
import { StockActionDialog, type StockMode, type StockTarget } from '../StockActionDialog'

export function InventoryPage() {
  const [params, setParams] = useSearchParams()
  const money = useMoney()
  const canManage = useCanManage()

  const q = params.get('q') ?? ''
  const lowStock = params.get('lowStock') === 'true'
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

  const [action, setAction] = useState<{ mode: StockMode; target: StockTarget } | null>(null)
  const open = (mode: StockMode, p: Product) =>
    setAction({ mode, target: { id: p.id, name: p.name, sku: p.sku, unit: p.unit, stockQty: p.stockQty } })

  const summary = useQuery({ queryKey: ['inventory', 'summary'], queryFn: inventoryApi.summary })
  const list = useQuery({
    queryKey: ['products', 'inventory', { q, lowStock, page }],
    queryFn: () => productApi.list({ q, lowStock, page, size: 20, sort: 'stockQty,asc' }),
    placeholderData: keepPreviousData,
  })

  const s = summary.data
  const showValue = s?.inventoryValue !== null && s?.inventoryValue !== undefined

  return (
    <div>
      <PageHeader
        title="库存"
        description="查看库存、入库、出库和盘点。所有变动都会自动记录。"
        actions={
          <Link to="/inventory/movements" className={buttonVariants({ variant: 'secondary' })}>
            <History aria-hidden />
            库存流水
          </Link>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-5">
        <Stat label="商品数" value={s?.productCount} />
        <Stat label="库存总量" value={s?.totalQty} />
        {showValue && <Stat label="库存价值（按成本）" text={s ? money(s.inventoryValue) : undefined} />}
        <Stat
          label="低库存"
          value={s?.lowStockCount}
          tone={s?.lowStockCount ? 'amber' : undefined}
          onClick={() => update({ lowStock: 'true' })}
        />
        <Stat label="缺货" value={s?.outOfStockCount} tone={s?.outOfStockCount ? 'red' : undefined} />
      </div>

      <Card>
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索商品名称、SKU 或条码"
              className="pl-9"
              aria-label="搜索商品"
            />
          </div>
          <button
            type="button"
            onClick={() => update({ lowStock: lowStock ? null : 'true' })}
            aria-pressed={lowStock}
            className={cn(
              'h-10 rounded-lg border px-3 text-sm font-medium',
              lowStock ? 'border-amber-300 bg-amber-50 text-amber-800' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
            )}
          >
            只看低库存
          </button>
        </div>

        {list.isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : list.data && list.data.items.length === 0 ? (
          <EmptyState
            icon={Warehouse}
            title={q || lowStock ? '没有符合条件的商品' : '还没有商品'}
            description={q || lowStock ? '换个条件试试' : '先在「商品」中添加商品，再来管理库存'}
            action={
              !(q || lowStock) && (
                <Link to="/products/new" className={buttonVariants()}>
                  新增商品
                </Link>
              )
            }
          />
        ) : (
          list.data && (
            <>
              <ul className="divide-y divide-slate-100">
                {list.data.items.map((p) => (
                  <li key={p.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                    <Link to={`/products/${p.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                      <ProductThumb url={p.imageUrl} name={p.name} />
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-900 hover:text-blue-600">{p.name}</p>
                        <p className="truncate text-xs text-slate-500">
                          {p.sku} · 预警值 {p.lowStockThreshold}
                          {p.costPrice !== null && ` · 价值 ${money((Number(p.costPrice) * Math.max(p.stockQty, 0)).toFixed(2))}`}
                        </p>
                      </div>
                    </Link>
                    <div className="flex items-center justify-between gap-3 sm:justify-end">
                      <span className="flex items-center gap-2 whitespace-nowrap">
                        <span
                          className={cn(
                            'text-lg font-semibold tabular-nums',
                            p.stockQty <= 0 ? 'text-red-600' : p.lowStock ? 'text-amber-700' : 'text-slate-900',
                          )}
                        >
                          {formatQty(p.stockQty)}
                        </span>
                        <span className="text-xs text-slate-500">{p.unit}</span>
                        {p.stockQty <= 0 ? <Badge tone="red">缺货</Badge> : p.lowStock && <Badge tone="amber">低库存</Badge>}
                      </span>
                      {canManage && (
                        <div className="flex gap-1.5">
                          <Button size="sm" variant="secondary" onClick={() => open('in', p)}>
                            <ArrowDownToLine aria-hidden />
                            入库
                          </Button>
                          <Button size="sm" variant="secondary" onClick={() => open('out', p)}>
                            <ArrowUpFromLine aria-hidden />
                            出库
                          </Button>
                          <Button size="sm" variant="secondary" onClick={() => open('adjust', p)}>
                            <ClipboardCheck aria-hidden />
                            盘点
                          </Button>
                        </div>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination
                  page={list.data.page}
                  totalPages={list.data.totalPages}
                  totalItems={list.data.totalItems}
                  unit="件商品"
                  onChange={(p) => update({ page: String(p) }, false)}
                />
              </div>
            </>
          )
        )}
      </Card>

      <StockActionDialog target={action?.target ?? null} mode={action?.mode ?? 'in'} onClose={() => setAction(null)} />
    </div>
  )
}

function Stat({
  label,
  value,
  text,
  tone,
  onClick,
}: {
  label: string
  value?: number
  text?: string
  tone?: 'amber' | 'red'
  onClick?: () => void
}) {
  const shown = text ?? (value === undefined ? undefined : value.toLocaleString('zh-CN'))
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn(
        'rounded-xl border border-slate-200 bg-white p-4 text-left shadow-sm',
        onClick && 'transition-colors hover:border-blue-200',
      )}
    >
      <p className="text-sm text-slate-500">{label}</p>
      {shown === undefined ? (
        <div className="mt-2 h-7 w-16 animate-pulse rounded bg-slate-100" />
      ) : (
        <p
          className={cn(
            'mt-1 text-xl font-semibold tabular-nums sm:text-2xl',
            tone === 'amber' ? 'text-amber-700' : tone === 'red' ? 'text-red-600' : 'text-slate-900',
          )}
        >
          {shown}
        </p>
      )}
    </Tag>
  )
}
