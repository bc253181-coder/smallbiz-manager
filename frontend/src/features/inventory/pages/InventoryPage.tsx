import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowDownToLine, ArrowLeftRight, ArrowUpFromLine, ClipboardCheck, History, Search, Warehouse } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { CameraScanButton } from '@/features/barcode/CameraScanButton'
import { useWarehouses, warehouseApi } from '@/features/warehouse/api'
import { productApi } from '@/features/catalog/api'
import { ProductThumb } from '@/features/catalog/components/ProductThumb'
import type { Product } from '@/features/catalog/types'
import { formatQty, useMoney } from '@/lib/format'
import { useCanManage, useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { inventoryApi } from '../api'
import { StockActionDialog, type StockMode, type StockTarget } from '../StockActionDialog'

import { tr } from '@/lib/i18n'
import { warehouseName } from '@/lib/systemText'
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

  // 多仓库：按仓库查看数量（只有一个仓库时不显示）
  const wh = useWarehouses()
  const warehouseId = params.get('w') ? Number(params.get('w')) : undefined
  const summary = useQuery({ queryKey: ['inventory', 'summary'], queryFn: inventoryApi.summary })
  const list = useQuery({
    queryKey: ['products', 'inventory', { q, lowStock, page }],
    queryFn: () => productApi.list({ q, lowStock, page, size: 20, sort: 'stockQty,asc' }),
    placeholderData: keepPreviousData,
  })

  const pageIds = list.data?.items.map((p) => p.id) ?? []
  const whStocks = useQuery({
    queryKey: ['warehouse-stocks', warehouseId, pageIds],
    queryFn: () => warehouseApi.stocks(warehouseId!, pageIds),
    enabled: wh.multi && !!warehouseId && pageIds.length > 0,
  })
  const shownQty = (p: Product) => (wh.multi && warehouseId ? (whStocks.data?.get(p.id) ?? null) : p.stockQty)

  const s = summary.data
  const showValue = s?.inventoryValue !== null && s?.inventoryValue !== undefined

  return (
    <div>
      <PageHeader
        title={tr('库存')}
        description={tr('查看库存、入库、出库和盘点。所有变动都会自动记录。')}
        actions={
          <>
            {canManage && wh.all.length > 1 && (
              <Link to="/inventory/transfers" className={buttonVariants({ variant: 'secondary' })}>
                <ArrowLeftRight aria-hidden />
                {tr('调拨')}
              </Link>
            )}
            <Link to="/inventory/movements" className={buttonVariants({ variant: 'secondary' })}>
              <History aria-hidden />
              {tr('库存流水')}
            </Link>
          </>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-5">
        <Stat label={tr('商品数')} value={s?.productCount} />
        <Stat label={tr('库存总量')} value={s?.totalQty} />
        {showValue && <Stat label={tr('库存价值（按成本）')} text={s ? money(s.inventoryValue) : undefined} />}
        <Stat
          label={tr('低库存')}
          value={s?.lowStockCount}
          tone={s?.lowStockCount ? 'amber' : undefined}
          onClick={() => update({ lowStock: 'true' })}
        />
        <Stat label={tr('缺货')} value={s?.outOfStockCount} tone={s?.outOfStockCount ? 'red' : undefined} />
      </div>

      <Card>
        {wh.multi && (
          <div className="-mb-px flex gap-1 overflow-x-auto border-b border-slate-100 px-4 pt-3" role="tablist" aria-label={tr('按仓库查看')}>
            {[{ id: undefined as number | undefined, name: tr('全部仓库') }, ...wh.active].map((w) => (
              <button
                key={w.id ?? 'all'}
                type="button"
                role="tab"
                aria-selected={warehouseId === w.id}
                onClick={() => update({ w: w.id ? String(w.id) : null }, false)}
                className={cn(
                  'whitespace-nowrap border-b-2 px-3 pb-2 text-sm font-medium',
                  warehouseId === w.id ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-800',
                )}
              >
                {warehouseName(w.name)}
              </button>
            ))}
          </div>
        )}
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={tr('搜索商品名称、SKU 或条码')}
              className="pl-9 pr-11"
              aria-label={tr('搜索商品')}
              onKeyDown={(e) => {
                // 扫码枪扫完按回车：立即搜索，不等输入停顿
                if (e.key === 'Enter') update({ q: search.trim() || null })
              }}
            />
            <CameraScanButton
              title={tr('扫码查库存')}
              onCode={(code) => {
                setSearch(code)
                update({ q: code })
              }}
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
            {tr('只看低库存')}
          </button>
        </div>

        {list.isLoading ? (
          <div className="h-48 animate-pulse bg-slate-50" />
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} />
        ) : list.data && list.data.items.length === 0 ? (
          <EmptyState
            icon={Warehouse}
            title={q || lowStock ? tr('没有符合条件的商品') : tr('还没有商品')}
            description={q || lowStock ? tr('换个条件试试') : tr('先在「商品」中添加商品，再来管理库存')}
            action={
              !(q || lowStock) && (
                <Link to="/products/new" className={buttonVariants()}>
                  {tr('新增商品')}
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
                          {p.sku} {tr('· 预警值')} {p.lowStockThreshold}
                          {p.costPrice !== null && tr(' · 价值 {{v0}}', { v0: money((Number(p.costPrice) * Math.max(p.stockQty, 0)).toFixed(2)) })}
                        </p>
                      </div>
                    </Link>
                    <div className="flex items-center justify-between gap-3 sm:justify-end">
                      <span className="flex items-center gap-2 whitespace-nowrap">
                        <span
                          className={cn(
                            'text-lg font-semibold tabular-nums',
                            (shownQty(p) ?? 0) <= 0 ? 'text-red-600' : p.lowStock ? 'text-amber-700' : 'text-slate-900',
                          )}
                        >
                          {shownQty(p) === null ? '…' : formatQty(shownQty(p)!)}
                        </span>
                        <span className="text-xs text-slate-500">
                          {p.unit}
                          {wh.multi && warehouseId && tr('（共 {{stockQty}}）', { stockQty: formatQty(p.stockQty) })}
                        </span>
                        {(shownQty(p) ?? 0) <= 0 ? <Badge tone="red">{tr('缺货')}</Badge> : p.lowStock && <Badge tone="amber">{tr('低库存')}</Badge>}
                      </span>
                      {canManage && (
                        <div className="flex flex-wrap justify-end gap-1.5">
                          <Button size="sm" variant="secondary" onClick={() => open('in', p)}>
                            <ArrowDownToLine aria-hidden />
                            {tr('入库')}
                          </Button>
                          <Button size="sm" variant="secondary" onClick={() => open('out', p)}>
                            <ArrowUpFromLine aria-hidden />
                            {tr('出库')}
                          </Button>
                          <Button size="sm" variant="secondary" onClick={() => open('adjust', p)}>
                            <ClipboardCheck aria-hidden />
                            {tr('盘点')}
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
                  unit={tr('件商品')}
                  onChange={(p) => update({ page: String(p) }, false)}
                />
              </div>
            </>
          )
        )}
      </Card>

      <StockActionDialog
        target={action?.target ?? null}
        mode={action?.mode ?? 'in'}
        onClose={() => setAction(null)}
        warehouses={wh.multi ? wh.usable : []}
        initialWarehouseId={wh.usable.some((x) => x.id === warehouseId) ? warehouseId : wh.defaultWarehouse?.id}
      />
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
  const shown = text ?? (value === undefined ? undefined : formatQty(value))
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
