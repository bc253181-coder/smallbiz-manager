import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { HandCoins, Plus, Printer, XCircle } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Input } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { tenantApi } from '@/features/auth/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { PRICE_TIER_LABELS } from '@/features/partners/api'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { useFeature } from '@/features/billing/api'
import { METHOD_LABELS, type PaymentMethod } from '@/features/receivables/api'
import { ReceivePaymentDialog } from '@/features/receivables/ReceivePaymentDialog'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { PAYMENT_STATUS_LABELS, PRICE_SOURCE_LABELS, saleApi } from '../api'
import { useWarehouses } from '@/features/warehouse/api'
import { SaleStatusBadge } from '../SaleStatusBadge'

const BACK = { to: '/sales', label: '返回销售列表' }

export function SaleDetailPage() {
  const id = Number(useParams().id)
  const wh = useWarehouses()
  const queryClient = useQueryClient()
  const money = useMoney()
  const canManage = useCanManage()
  const { session } = useAuth()
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')
  const [receiving, setReceiving] = useState(false)
  const canCredit = useFeature('RECEIVABLES')

  const { data: o, isLoading, isError, error, refetch } = useQuery({ queryKey: ['sale', id], queryFn: () => saleApi.get(id) })
  const { data: shop } = useQuery({ queryKey: ['tenant'], queryFn: tenantApi.current })

  const cancel = useMutation({
    mutationFn: () => saleApi.cancel(id, reason.trim()),
    onSuccess: async (u) => {
      queryClient.setQueryData(['sale', id], u)
      await Promise.all(
        [['sales'], ['products'], ['product'], ['product-movements'], ['inventory'], ['customer-stats'], ['receivables'], ['customer-receivable']].map((k) =>
          queryClient.invalidateQueries({ queryKey: k }),
        ),
      )
      setCancelling(false)
      toast.success('销售单已取消，库存已恢复')
    },
    onError: (e) => {
      setCancelling(false)
      toast.error(errorMessage(e, '取消失败'))
      void refetch()
    },
  })

  if (isLoading) return <div className="h-96 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  if (isError || !o) {
    return (
      <div>
        <PageHeader title="销售单" back={BACK} />
        <Card>
          {errorCode(error) === 'SALE_NOT_FOUND' ? (
            <div className="flex flex-col items-center gap-3 py-14 text-center">
              <p className="font-semibold text-slate-900">销售单不存在</p>
              <p className="text-sm text-slate-500">它可能不属于当前店铺</p>
              <Link to="/sales" className={buttonVariants({ variant: 'secondary' })}>
                返回销售列表
              </Link>
            </div>
          ) : (
            <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
          )}
        </Card>
      </div>
    )
  }

  const showCost = o.totalCost !== null
  const owes = o.status === 'COMPLETED' && Number(o.outstanding) > 0

  return (
    <div>
      <div className="print:hidden">
        <PageHeader
          back={BACK}
          title={
            <span className="flex flex-wrap items-center gap-2">
              {o.orderNo}
              <SaleStatusBadge status={o.status} />
            </span>
          }
          description={`${o.customerName ?? '散客'} · ${formatDateTime(o.orderDate)}`}
          actions={
            <>
              {owes && canCredit && o.customerId && (
                <Button onClick={() => setReceiving(true)}>
                  <HandCoins aria-hidden />
                  收款
                </Button>
              )}
              {canManage && o.status === 'COMPLETED' && (
                <Button variant="secondary" onClick={() => setCancelling(true)}>
                  <XCircle aria-hidden />
                  取消订单
                </Button>
              )}
              <Button variant="secondary" onClick={() => window.print()}>
                <Printer aria-hidden />
                打印
              </Button>
              <Link to="/sales/new" className={buttonVariants()}>
                <Plus aria-hidden />
                再开一单
              </Link>
            </>
          }
        />
      </div>

      {/* 仅打印时显示的抬头 */}
      <div className="mb-6 hidden text-center print:block">
        <h1 className="text-xl font-bold">{session?.currentTenant?.name}</h1>
        {(shop?.contactPhone || shop?.address) && (
          <p className="text-sm">{[shop?.contactPhone, shop?.address].filter(Boolean).join(' · ')}</p>
        )}
        <p className="mt-2 text-sm">
          销售单 {o.orderNo} · {formatDateTime(o.orderDate)} · 客户：{o.customerName ?? '散客'}
        </p>
      </div>

      {o.status === 'CANCELLED' && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {formatDateTime(o.cancelledAt)} 由 {o.cancelledByName ?? '—'} 取消，库存已恢复{o.cancelReason ? `。原因：${o.cancelReason}` : ''}。
        </div>
      )}

      <div className="grid gap-6 xl:grid-cols-3 [&>*]:min-w-0 print:block">
        <Card className="xl:col-span-2 print:border-0 print:shadow-none">
          <CardHeader title="商品明细" description={`${o.itemCount} 种，共 ${o.totalQuantity} 件 · ${PRICE_TIER_LABELS[o.priceTier]}`} />
          <div className="overflow-x-auto">
            <table className="w-full min-w-[420px] text-sm">
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
                  <tr key={i.productId}>
                    <td className="px-4 py-3">
                      <Link to={`/products/${i.productId}`} className="font-medium text-slate-900 hover:text-blue-600 print:text-black">
                        {i.productName}
                      </Link>
                      <p className="text-xs text-slate-500">{i.sku}</p>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                      {i.quantity} {i.unit}
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums">
                      {money(i.unitPrice)}
                      {/* 价格来源只在屏幕上显示，打印给客户的单据上不显示 */}
                      <p className={cn('text-xs print:hidden', i.priceSource === 'CONTRACT' ? 'text-violet-700' : i.priceSource === 'MANUAL' ? 'text-amber-700' : 'text-slate-400')}>
                        {PRICE_SOURCE_LABELS[i.priceSource]}
                      </p>
                    </td>
                    <td className="px-4 py-3 text-right font-medium tabular-nums">{money(i.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="text-sm">
                <AmountRow label="商品合计" value={money(o.subtotal)} />
                {Number(o.discountAmount) > 0 && <AmountRow label="优惠" value={`−${money(o.discountAmount)}`} />}
                <AmountRow label="应收" value={money(o.totalAmount)} strong />
                {o.paymentStatus !== 'PAID' && o.status === 'COMPLETED' && (
                  <>
                    <AmountRow label="已收" value={money(o.paidAmount)} />
                    <AmountRow label="未收" value={money(o.outstanding)} />
                  </>
                )}
              </tfoot>
            </table>
          </div>
        </Card>

        <Card className="print:hidden">
          <CardHeader title="单据信息" />
          <CardBody>
            <dl className="space-y-3 text-sm">
              <Row label="客户">
                {o.customerId ? (
                  <Link to={`/customers/${o.customerId}`} className="text-blue-600 hover:underline">
                    {o.customerName}
                  </Link>
                ) : (
                  '散客'
                )}
              </Row>
              <Row label="开单人">{o.createdByName ?? '—'}</Row>
              {wh.all.length > 1 && o.warehouseName && <Row label="出货仓库">{o.warehouseName}</Row>}
              <Row label="收款">
                <Badge tone={o.paymentStatus === 'PAID' ? 'green' : o.paymentStatus === 'PARTIAL' ? 'amber' : 'red'}>
                  {PAYMENT_STATUS_LABELS[o.paymentStatus]}
                </Badge>
              </Row>
              {o.paymentStatus !== 'PAID' || o.payments.length > 0 ? (
                <>
                  <Row label="开单时实收">{money(o.paidAtSale)}</Row>
                  <Row label="已收合计">{money(o.paidAmount)}</Row>
                  {o.status === 'COMPLETED' && (
                    <Row label="未收">
                      <span className={owes ? 'text-red-600' : undefined}>{money(o.outstanding)}</span>
                    </Row>
                  )}
                </>
              ) : null}
              {showCost && (
                <>
                  <Row label="成本">{money(o.totalCost)}</Row>
                  <Row label="毛利">
                    <span className={Number(o.grossProfit) < 0 ? 'text-red-600' : 'text-emerald-700'}>{money(o.grossProfit)}</span>
                  </Row>
                </>
              )}
              {o.notes && <Row label="备注">{o.notes}</Row>}
            </dl>
          </CardBody>
        </Card>
      </div>

      {o.payments.length > 0 && (
        <Card className="mt-6 print:hidden">
          <CardHeader title="收款记录" description="收款按开单时间先欠先还，冲抵到这张单的金额" />
          <ul className="divide-y divide-slate-100">
            {o.payments.map((p) => (
              <li key={p.paymentId} className={cn('flex items-center justify-between gap-3 px-5 py-3 text-sm', p.status === 'VOIDED' && 'text-slate-400')}>
                <span className="min-w-0">
                  <span className="block font-mono text-xs">{p.paymentNo}</span>
                  <span className="block text-xs text-slate-500">
                    {formatDateTime(p.receivedAt)} · {METHOD_LABELS[p.method as PaymentMethod] ?? p.method}
                    {p.status === 'VOIDED' && ' · 已作废'}
                  </span>
                </span>
                <span className={cn('tabular-nums', p.status === 'VOIDED' ? 'line-through' : 'font-medium text-emerald-700')}>{money(p.amount)}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {receiving && o.customerId && (
        <ReceivePaymentDialog
          customerId={o.customerId}
          customerName={o.customerName ?? ''}
          salesOrderId={o.id}
          onClose={() => {
            setReceiving(false)
            void refetch()
          }}
        />
      )}

      <FormDialog
        open={cancelling}
        onClose={() => setCancelling(false)}
        title="取消这张销售单？"
        description="取消后商品库存会恢复，这笔销售不再计入统计。此操作不能撤销。"
        submitText="确认取消"
        saving={cancel.isPending}
        onSubmit={(e) => {
          e.preventDefault()
          cancel.mutate()
        }}
      >
        <Field label="取消原因（选填）" htmlFor="sale-cancel-reason">
          <Input id="sale-cancel-reason" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} placeholder="例如：客户退货" />
        </Field>
      </FormDialog>
    </div>
  )
}

function AmountRow({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <tr className={strong ? 'border-t border-slate-200' : undefined}>
      <td colSpan={3} className="px-4 py-2 text-right text-slate-600">
        {label}
      </td>
      <td className={strong ? 'px-4 py-3 text-right text-base font-semibold tabular-nums' : 'px-4 py-2 text-right tabular-nums'}>{value}</td>
    </tr>
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
