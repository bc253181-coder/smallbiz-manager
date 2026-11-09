import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { History, PackageX, Pencil, Tag, Trash2 } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ProductCustomerPricesCard } from '@/features/partners/ProductCustomerPricesCard'
import { ProductWarehouseStocks } from '@/features/warehouse/ProductWarehouseStocks'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime, formatQty, useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { StockActionDialog, type StockMode } from '@/features/inventory/StockActionDialog'
import { productApi } from '../api'
import { ProductThumb } from '../components/ProductThumb'
import { MOVEMENT_LABELS, STATUS_LABELS } from '../types'

export function ProductDetailPage() {
  const productId = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const money = useMoney()
  const canManage = useCanManage()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [movementPage, setMovementPage] = useState(0)
  const [stockMode, setStockMode] = useState<StockMode | null>(null)

  const { data: p, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productApi.get(productId),
  })
  const movements = useQuery({
    queryKey: ['product-movements', productId, movementPage],
    queryFn: () => productApi.movements(productId, movementPage),
    enabled: !!p,
  })

  const remove = useMutation({
    mutationFn: () => productApi.remove(productId),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['product', productId] })
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      await queryClient.invalidateQueries({ queryKey: ['categories'] })
      toast.success('商品已删除')
      navigate('/products', { replace: true })
    },
    onError: (e) => {
      setConfirmDelete(false)
      toast.error(errorMessage(e, '删除失败'))
    },
  })

  if (isLoading) return <div className="h-96 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  if (isError || !p) {
    return (
      <div>
        <PageHeader title="商品" back={{ to: '/products', label: '返回商品列表' }} />
        <Card>
          {errorCode(error) === 'PRODUCT_NOT_FOUND' ? (
            <EmptyState
              icon={PackageX}
              title="商品不存在"
              description="它可能已被删除，或者不属于当前店铺"
              action={
                <Link to="/products" className={buttonVariants({ variant: 'secondary' })}>
                  返回商品列表
                </Link>
              }
            />
          ) : (
            <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
          )}
        </Card>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        back={{ to: '/products', label: '返回商品列表' }}
        title={
          <span className="flex flex-wrap items-center gap-2">
            {p.name}
            <Badge tone={p.status === 'ACTIVE' ? 'green' : 'slate'}>{STATUS_LABELS[p.status]}</Badge>
          </span>
        }
        description={`SKU：${p.sku}`}
        actions={
          canManage && (
            <>
              <Button variant="secondary" onClick={() => setConfirmDelete(true)}>
                <Trash2 aria-hidden />
                删除
              </Button>
              <Link to={`/products/labels?ids=${p.id}`} className={buttonVariants({ variant: 'secondary' })}>
                <Tag aria-hidden />
                打印标签
              </Link>
              <Link to={`/products/${p.id}/edit`} className={buttonVariants()}>
                <Pencil aria-hidden />
                编辑
              </Link>
            </>
          )
        }
      />

      <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <Card className="lg:col-span-2">
          <CardBody className="flex flex-col gap-6 sm:flex-row">
            <ProductThumb url={p.imageUrl} name={p.name} className="size-40 self-center rounded-xl sm:self-start" />
            <dl className="grid flex-1 grid-cols-2 gap-x-6 gap-y-4 text-sm">
              <Item label="分类">{p.categoryName ?? '未分类'}</Item>
              <Item label="单位">{p.unit}</Item>
              <Item label="零售价">{money(p.retailPrice)}</Item>
              <Item label="批发价">{p.wholesalePrice ? money(p.wholesalePrice) : '同零售价'}</Item>
              {p.costPrice !== null && <Item label="进货成本">{money(p.costPrice)}</Item>}
              <Item label="条形码">{p.barcode ?? '—'}</Item>
              {p.description && (
                <div className="col-span-2">
                  <dt className="text-slate-500">描述</dt>
                  <dd className="mt-1 whitespace-pre-wrap text-slate-900">{p.description}</dd>
                </div>
              )}
            </dl>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="库存" />
          <CardBody className="space-y-4">
            <div>
              <p className="text-sm text-slate-500">当前库存</p>
              <p className={cn('mt-1 text-3xl font-semibold tabular-nums', p.lowStock ? 'text-amber-700' : 'text-slate-900')}>
                {formatQty(p.stockQty)}
                <span className="ml-1 text-base font-normal text-slate-500">{p.unit}</span>
              </p>
              {p.lowStock && (
                <Badge tone="amber" className="mt-2">
                  库存不高于预警值 {p.lowStockThreshold}，建议补货
                </Badge>
              )}
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">低库存预警值</span>
              <span className="font-medium">
                {p.lowStockThreshold} {p.unit}
              </span>
            </div>
            {p.costPrice !== null && (
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">库存价值（按成本）</span>
                <span className="font-medium">{money((Number(p.costPrice) * p.stockQty).toFixed(2))}</span>
              </div>
            )}
            {canManage && (
              <div className="grid grid-cols-3 gap-2 pt-1">
                {(['in', 'out', 'adjust'] as const).map((mode) => (
                  <Button key={mode} size="sm" variant="secondary" onClick={() => setStockMode(mode)}>
                    {mode === 'in' ? '入库' : mode === 'out' ? '出库' : '盘点'}
                  </Button>
                ))}
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="mt-6 grid gap-6 empty:hidden lg:grid-cols-2 [&>*]:min-w-0">
        <ProductWarehouseStocks productId={p.id} unit={p.unit} />
        <ProductCustomerPricesCard productId={p.id} />
      </div>

      <Card className="mt-6">
        <CardHeader
          title="库存变动记录"
          description="每一次库存变化都会自动记录，不可修改"
          action={
            <Link to={`/inventory/movements?productId=${p.id}`} className="whitespace-nowrap text-sm font-medium text-blue-600 hover:underline">
              按条件查看
            </Link>
          }
        />
        {movements.isLoading ? (
          <div className="h-32 animate-pulse bg-slate-50" />
        ) : movements.isError ? (
          <ErrorState onRetry={() => void movements.refetch()} />
        ) : movements.data && movements.data.items.length === 0 ? (
          <EmptyState icon={History} title="暂无库存变动" />
        ) : (
          movements.data && (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                    <tr>
                      <th className="px-4 py-3">时间</th>
                      <th className="px-4 py-3">类型</th>
                      <th className="px-4 py-3 text-right">变动</th>
                      <th className="px-4 py-3 text-right">变动后</th>
                      <th className="px-4 py-3">备注</th>
                      <th className="px-4 py-3">操作人</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {movements.data.items.map((m) => (
                      <tr key={m.id}>
                        <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDateTime(m.createdAt)}</td>
                        <td className="px-4 py-3">{MOVEMENT_LABELS[m.type]}</td>
                        <td
                          className={cn(
                            'px-4 py-3 text-right font-medium tabular-nums',
                            m.qtyChange > 0 ? 'text-emerald-700' : 'text-red-600',
                          )}
                        >
                          {m.qtyChange > 0 ? `+${m.qtyChange}` : m.qtyChange}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{m.qtyAfter}</td>
                        <td className="px-4 py-3 text-slate-600">{m.reason ?? '—'}</td>
                        <td className="px-4 py-3 text-slate-600">{m.operatorName ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="border-t border-slate-100 px-4 py-3">
                <Pagination
                  page={movements.data.page}
                  totalPages={movements.data.totalPages}
                  totalItems={movements.data.totalItems}
                  onChange={setMovementPage}
                />
              </div>
            </>
          )
        )}
      </Card>

      <StockActionDialog
        mode={stockMode ?? 'in'}
        target={stockMode ? { id: p.id, name: p.name, sku: p.sku, unit: p.unit, stockQty: p.stockQty } : null}
        onClose={() => setStockMode(null)}
      />

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={`删除「${p.name}」？`}
        description="删除后商品将不再显示，SKU 可以被新商品使用。已有的库存记录会保留。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </div>
  )
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="mt-1 font-medium text-slate-900">{children}</dd>
    </div>
  )
}
