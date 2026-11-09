import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { FolderTree, Package, Plus, Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input, Select } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { ExcelExportButton } from '@/features/excel/ExcelExportButton'
import { ImportButton } from '@/features/excel/ImportDialog'
import { useMoney, formatQty } from '@/lib/format'
import { useCanManage, useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { categoryApi, flattenCategories, productApi } from '../api'
import { ProductThumb } from '../components/ProductThumb'
import { STATUS_LABELS, type Product, type ProductStatus } from '../types'

const SORTS = [
  { value: '', label: '最近更新' },
  { value: 'name,asc', label: '名称 A→Z' },
  { value: 'stockQty,asc', label: '库存 少→多' },
  { value: 'stockQty,desc', label: '库存 多→少' },
  { value: 'retailPrice,asc', label: '零售价 低→高' },
  { value: 'retailPrice,desc', label: '零售价 高→低' },
  { value: 'createdAt,desc', label: '最新添加' },
]

export function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const money = useMoney()
  const canManage = useCanManage()

  const q = params.get('q') ?? ''
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const status = (params.get('status') as ProductStatus | null) ?? undefined
  const lowStock = params.get('lowStock') === 'true'
  const sort = params.get('sort') ?? ''
  const page = Number(params.get('page') ?? 0)

  // 搜索框输入防抖后再写入网址
  const [search, setSearch] = useState(q)
  const debounced = useDebouncedValue(search)
  useEffect(() => {
    if (debounced !== q) update({ q: debounced || null })
  }, [debounced])

  function update(changes: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    if (resetPage) next.delete('page')
    setParams(next, { replace: true })
  }

  const filtered = !!(q || categoryId || status || lowStock)

  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoryApi.tree })
  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['products', { q, categoryId, status, lowStock, sort, page }],
    queryFn: () => productApi.list({ q, categoryId, status, lowStock, sort, page, size: 20 }),
    placeholderData: keepPreviousData,
  })

  const showCost = data?.items.some((p) => p.costPrice !== null) ?? false

  return (
    <div>
      <PageHeader
        title="商品"
        description="管理商品资料、价格和库存预警"
        actions={
          <>
            <Link to="/products/categories" className={buttonVariants({ variant: 'secondary' })}>
              <FolderTree aria-hidden />
              商品分类
            </Link>
            <ImportButton type="products" />
            <ExcelExportButton
              path="/exports/products"
              params={{ q, categoryId, status, lowStock: lowStock || undefined }}
              fallbackName="商品.xlsx"
            />
            {canManage && (
              <Link to="/products/new" className={buttonVariants()}>
                <Plus aria-hidden />
                新增商品
              </Link>
            )}
          </>
        }
      />

      <Card>
        {/* 筛选栏 */}
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 xl:flex-row xl:items-center">
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
          <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
            <Select
              aria-label="按分类筛选"
              value={categoryId ?? ''}
              onChange={(e) => update({ categoryId: e.target.value || null })}
              className="sm:w-40"
            >
              <option value="">全部分类</option>
              {flattenCategories(categories ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.label}
                </option>
              ))}
            </Select>
            <Select
              aria-label="按状态筛选"
              value={status ?? ''}
              onChange={(e) => update({ status: e.target.value || null })}
              className="sm:w-32"
            >
              <option value="">全部状态</option>
              <option value="ACTIVE">在售</option>
              <option value="INACTIVE">已下架</option>
            </Select>
            <Select aria-label="排序" value={sort} onChange={(e) => update({ sort: e.target.value || null })} className="sm:w-36">
              {SORTS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </Select>
            <button
              type="button"
              onClick={() => update({ lowStock: lowStock ? null : 'true' })}
              aria-pressed={lowStock}
              className={cn(
                'h-10 rounded-lg border px-3 text-sm font-medium transition-colors',
                lowStock ? 'border-amber-300 bg-amber-50 text-amber-800' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
              )}
            >
              只看低库存
            </button>
          </div>
        </div>

        {isLoading ? (
          <ListSkeleton />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : data && data.items.length === 0 ? (
          filtered ? (
            <EmptyState
              icon={Search}
              title="没有符合条件的商品"
              description="换个关键词或清除筛选条件试试"
              action={
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSearch('')
                    setParams(new URLSearchParams(), { replace: true })
                  }}
                >
                  <X aria-hidden />
                  清除筛选
                </Button>
              }
            />
          ) : (
            <EmptyState
              icon={Package}
              title="还没有商品"
              description="添加第一个商品，录入名称、价格和库存，开始管理你的生意"
              action={
                canManage && (
                  <Link to="/products/new" className={buttonVariants()}>
                    <Plus aria-hidden />
                    新增商品
                  </Link>
                )
              }
            />
          )
        ) : (
          data && (
            <div className={cn(isFetching && 'opacity-60 transition-opacity')}>
              {/* 桌面：表格 */}
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full min-w-[600px] text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-4 py-3">商品</th>
                      <th className="px-4 py-3">分类</th>
                      <th className="px-4 py-3 text-right">零售价</th>
                      <th className="hidden px-4 py-3 text-right xl:table-cell">批发价</th>
                      {showCost && <th className="hidden px-4 py-3 text-right xl:table-cell">成本</th>}
                      <th className="px-4 py-3 text-right">库存</th>
                      <th className="px-4 py-3">状态</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.items.map((p) => (
                      <tr
                        key={p.id}
                        onClick={() => navigate(`/products/${p.id}`)}
                        className="cursor-pointer hover:bg-slate-50"
                      >
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <ProductThumb url={p.imageUrl} name={p.name} />
                            <div className="min-w-0">
                              <Link
                                to={`/products/${p.id}`}
                                onClick={(e) => e.stopPropagation()}
                                className="block max-w-xs truncate font-medium text-slate-900 hover:text-blue-600"
                              >
                                {p.name}
                              </Link>
                              <span className="text-xs text-slate-500">{p.sku}</span>
                            </div>
                          </div>
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{p.categoryName ?? '—'}</td>
                        <td className="px-4 py-3 text-right tabular-nums">{money(p.retailPrice)}</td>
                        <td className="hidden px-4 py-3 text-right tabular-nums text-slate-600 xl:table-cell">{money(p.wholesalePrice)}</td>
                        {showCost && <td className="hidden px-4 py-3 text-right tabular-nums text-slate-600 xl:table-cell">{money(p.costPrice)}</td>}
                        <td className="px-4 py-3 text-right">
                          <StockCell product={p} />
                        </td>
                        <td className="px-4 py-3">
                          <Badge tone={p.status === 'ACTIVE' ? 'green' : 'slate'}>{STATUS_LABELS[p.status]}</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* 手机：卡片 */}
              <ul className="divide-y divide-slate-100 md:hidden">
                {data.items.map((p) => (
                  <li key={p.id}>
                    <Link to={`/products/${p.id}`} className="flex items-center gap-3 p-4 active:bg-slate-50">
                      <ProductThumb url={p.imageUrl} name={p.name} className="size-12" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-slate-900">{p.name}</p>
                        <p className="truncate text-xs text-slate-500">
                          {p.sku}
                          {p.categoryName && ` · ${p.categoryName}`}
                        </p>
                        <p className="mt-1 text-sm font-medium tabular-nums text-slate-900">{money(p.retailPrice)}</p>
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <StockCell product={p} />
                        {p.status === 'INACTIVE' && <Badge>已下架</Badge>}
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>

              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination
                  page={data.page}
                  totalPages={data.totalPages}
                  totalItems={data.totalItems}
                  unit="件商品"
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

function StockCell({ product }: { product: Product }) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <span className={cn('tabular-nums', product.lowStock ? 'font-semibold text-amber-700' : 'text-slate-900')}>
        {formatQty(product.stockQty)}
      </span>
      <span className="text-xs text-slate-500">{product.unit}</span>
      {product.lowStock && <Badge tone="amber">低库存</Badge>}
    </span>
  )
}

function ListSkeleton() {
  return (
    <div className="space-y-3 p-4" aria-label="加载中">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="size-10 animate-pulse rounded-lg bg-slate-100" />
          <div className="h-4 flex-1 animate-pulse rounded bg-slate-100" />
          <div className="h-4 w-20 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </div>
  )
}
