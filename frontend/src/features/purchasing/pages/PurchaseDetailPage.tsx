import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, PackageCheck, Pencil, Send, Trash2, XCircle } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input, Select } from '@/components/ui/form'
import { useWarehouses } from '@/features/warehouse/api'
import { FormDialog } from '@/components/ui/form-dialog'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { formatDate } from '@/lib/utils'
import { purchaseApi, type PurchaseDetail } from '../api'
import { PurchaseStatusBadge } from '../PurchaseStatusBadge'

const BACK = { to: '/purchases', label: '返回采购列表' }

export function PurchaseDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const money = useMoney()
  const [dialog, setDialog] = useState<'receive' | 'cancel' | 'delete' | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  // 多仓库：选择收货仓库（只有一个仓库时不显示）
  const wh = useWarehouses()
  const [receiveWarehouse, setReceiveWarehouse] = useState<number | null>(null)
  const receiveTo = receiveWarehouse ?? wh.defaultWarehouse?.id ?? null

  const { data: o, isLoading, isError, error, refetch } = useQuery({ queryKey: ['purchase', id], queryFn: () => purchaseApi.get(id) })

  async function refreshAfter(updated?: PurchaseDetail) {
    if (updated) queryClient.setQueryData(['purchase', id], updated)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['purchases'] }),
      queryClient.invalidateQueries({ queryKey: ['supplier-products'] }),
    ])
  }

  const onError = (fallback: string) => (e: unknown) => {
    setDialog(null)
    toast.error(errorMessage(e, fallback))
    void refetch()
  }

  const submit = useMutation({
    mutationFn: () => purchaseApi.submit(id),
    onSuccess: async (u) => {
      await refreshAfter(u)
      toast.success('已下单，到货后请确认收货')
    },
    onError: onError('下单失败'),
  })
  const receive = useMutation({
    mutationFn: () => purchaseApi.receive(id, wh.multi && receiveTo ? receiveTo : undefined),
    onSuccess: async (u) => {
      await refreshAfter(u)
      // 收货影响库存、成本、低库存提醒
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['product'] }),
        queryClient.invalidateQueries({ queryKey: ['product-movements'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['warehouses'] }),
        queryClient.invalidateQueries({ queryKey: ['warehouse-stocks'] }),
      ])
      setDialog(null)
      toast.success(`收货完成，${u.totalQuantity} 件商品已入库`)
    },
    onError: onError('收货失败'),
  })
  const cancel = useMutation({
    mutationFn: () => purchaseApi.cancel(id, cancelReason.trim()),
    onSuccess: async (u) => {
      await refreshAfter(u)
      setDialog(null)
      toast.success('采购单已取消')
    },
    onError: onError('取消失败'),
  })
  const remove = useMutation({
    mutationFn: () => purchaseApi.remove(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['purchase', id] })
      await refreshAfter()
      toast.success('草稿已删除')
      navigate('/purchases', { replace: true })
    },
    onError: onError('删除失败'),
  })

  if (isLoading) return <div className="h-96 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  if (isError || !o) {
    const notFound = errorCode(error) === 'PURCHASE_NOT_FOUND'
    return (
      <div>
        <PageHeader title="采购单" back={BACK} />
        <Card>
          {notFound ? (
            <div className="flex flex-col items-center gap-3 py-14 text-center">
              <p className="font-semibold text-slate-900">采购单不存在</p>
              <p className="text-sm text-slate-500">它可能已被删除，或者不属于当前店铺</p>
              <Link to="/purchases" className={buttonVariants({ variant: 'secondary' })}>
                返回采购列表
              </Link>
            </div>
          ) : (
            <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
          )}
        </Card>
      </div>
    )
  }

  const editable = o.status === 'DRAFT' || o.status === 'ORDERED'

  return (
    <div>
      <PageHeader
        back={BACK}
        title={
          <span className="flex flex-wrap items-center gap-2">
            {o.orderNo}
            <PurchaseStatusBadge status={o.status} />
          </span>
        }
        description={`供应商：${o.supplierName}`}
        actions={
          <>
            {o.status === 'DRAFT' && (
              <Button variant="secondary" onClick={() => setDialog('delete')}>
                <Trash2 aria-hidden />
                删除草稿
              </Button>
            )}
            {o.status === 'ORDERED' && (
              <Button variant="secondary" onClick={() => setDialog('cancel')}>
                <XCircle aria-hidden />
                取消采购
              </Button>
            )}
            {editable && (
              <Link to={`/purchases/${o.id}/edit`} className={buttonVariants({ variant: 'secondary' })}>
                <Pencil aria-hidden />
                编辑
              </Link>
            )}
            {o.status === 'DRAFT' && (
              <Button loading={submit.isPending} onClick={() => submit.mutate()}>
                <Send aria-hidden />
                提交下单
              </Button>
            )}
            {o.status === 'ORDERED' && (
              <Button onClick={() => setDialog('receive')}>
                <PackageCheck aria-hidden />
                确认收货
              </Button>
            )}
          </>
        }
      />

      {o.status === 'RECEIVED' && (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          <CheckCircle2 className="size-4 shrink-0" aria-hidden />
          {formatDateTime(o.receivedAt)} 由 {o.receivedByName ?? '—'} 确认收货，{o.totalQuantity} 件商品已入库。
        </div>
      )}
      {o.status === 'CANCELLED' && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {formatDateTime(o.cancelledAt)} 由 {o.cancelledByName ?? '—'} 取消{o.cancelReason ? `，原因：${o.cancelReason}` : ''}。
        </div>
      )}

      {/* [&>*]:min-w-0：网格子元素默认不收缩，宽表格会把整页撑宽；让表格在卡片内部横向滚动 */}
      <div className="grid gap-6 xl:grid-cols-3 [&>*]:min-w-0">
        <Card className="xl:col-span-2">
          <CardHeader title="采购商品" description={`${o.itemCount} 种，共 ${o.totalQuantity} 件`} />
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-4 py-3">商品</th>
                  <th className="px-4 py-3 text-right">数量</th>
                  <th className="px-4 py-3 text-right">单价</th>
                  <th className="px-4 py-3 text-right">小计</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {o.items.map((i) => (
                  <tr key={i.id}>
                    <td className="px-4 py-3">
                      <Link to={`/products/${i.productId}`} className="font-medium text-slate-900 hover:text-blue-600">
                        {i.productName}
                      </Link>
                      <p className="text-xs text-slate-500">{i.sku}</p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                      {i.quantity} {i.unit}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">{money(i.unitCost)}</td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{money(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-slate-200">
                  <td colSpan={3} className="px-4 py-3 text-right font-medium text-slate-600">
                    合计
                  </td>
                  <td className="px-4 py-3 text-right text-base font-semibold tabular-nums">{money(o.totalAmount)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </Card>

        <Card>
          <CardHeader title="单据信息" />
          <CardBody>
            <dl className="space-y-3 text-sm">
              <Row label="供应商">
                <Link to={`/suppliers/${o.supplierId}`} className="text-blue-600 hover:underline">
                  {o.supplierName}
                </Link>
              </Row>
              <Row label="下单日期">{formatDate(o.orderDate)}</Row>
              <Row label="预计到货">{o.expectedDate ? o.expectedDate.replaceAll('-', '/') : '—'}</Row>
              <Row label="创建人">{o.createdByName ?? '—'}</Row>
              {o.warehouseName && wh.all.length > 1 && <Row label="收货仓库">{o.warehouseName}</Row>}
              {o.submittedAt && <Row label="提交下单">{formatDateTime(o.submittedAt)}</Row>}
              {o.notes && <Row label="备注">{o.notes}</Row>}
            </dl>
          </CardBody>
        </Card>
      </div>

      <ConfirmDialog
        open={dialog === 'receive'}
        onOpenChange={(v) => !v && setDialog(null)}
        title="确认收货？"
        description={
          <>
            {o.itemCount} 种商品共 {o.totalQuantity} 件将入库，进货成本按加权平均自动更新。收货后采购单不能再修改或取消。
            {wh.multi && (
              <span className="mt-3 block">
                <label htmlFor="receive-warehouse" className="mb-1 block text-sm font-medium text-slate-700">
                  收货仓库
                </label>
                <Select id="receive-warehouse" value={receiveTo ?? ''} onChange={(e) => setReceiveWarehouse(Number(e.target.value))}>
                  {wh.usable.map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.name}
                    </option>
                  ))}
                </Select>
              </span>
            )}
          </>
        }
        confirmText="确认收货"
        loading={receive.isPending}
        onConfirm={() => receive.mutate()}
      />
      <ConfirmDialog
        open={dialog === 'delete'}
        onOpenChange={(v) => !v && setDialog(null)}
        title="删除这张草稿？"
        description="删除后无法恢复。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
      <FormDialog
        open={dialog === 'cancel'}
        onClose={() => setDialog(null)}
        title="取消采购单"
        description="取消后不能再收货。库存不会变化。"
        submitText="确认取消"
        saving={cancel.isPending}
        onSubmit={(e) => {
          e.preventDefault()
          cancel.mutate()
        }}
      >
        <Field label="取消原因（选填）" htmlFor="cancel-reason">
          <Input id="cancel-reason" value={cancelReason} maxLength={200} onChange={(e) => setCancelReason(e.target.value)} placeholder="例如：供应商缺货" />
        </Field>
      </FormDialog>
    </div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="shrink-0 text-slate-500">{label}</dt>
      <dd className="min-w-0 break-words text-right font-medium text-slate-900">{children}</dd>
    </div>
  )
}
