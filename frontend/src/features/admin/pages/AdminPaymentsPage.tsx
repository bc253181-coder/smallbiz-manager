import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ReceiptText } from 'lucide-react'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { FormDialog } from '@/components/ui/form-dialog'
import { Field, Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { PLAN_LABELS } from '@/features/auth/types'
import {
  fetchImageBlob,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONES,
  PERIOD_LABELS,
  type PaymentOrder,
  type PaymentStatus,
} from '@/features/billing/api'
import { errorMessage } from '@/lib/api'
import { formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { adminApi } from '../api'

const FILTERS: { value: PaymentStatus | ''; label: string }[] = [
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'AWAITING_PAYMENT', label: '待付款' },
  { value: 'APPROVED', label: '已开通' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: '', label: '全部' },
]

export function AdminPaymentsPage() {
  const [status, setStatus] = useState<PaymentStatus | ''>('PENDING_REVIEW')
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin', 'payments', status],
    queryFn: () => adminApi.payments(status),
    refetchInterval: status === 'PENDING_REVIEW' ? 60_000 : false,
  })

  return (
    <div>
      <PageHeader title="付款审核" description="请在收款 App 中确认：金额一致、付款备注与参考码一致、交易单号未被使用过" />
      <div className="-mx-1 mb-4 flex gap-1 overflow-x-auto px-1" role="tablist">
        {FILTERS.map((f) => (
          <button
            key={f.label}
            type="button"
            role="tab"
            aria-selected={status === f.value}
            onClick={() => setStatus(f.value)}
            className={cn(
              'whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium',
              status === f.value ? 'bg-white text-blue-700 shadow-sm ring-1 ring-slate-200' : 'text-slate-600 hover:bg-white/60',
            )}
          >
            {f.label}
          </button>
        ))}
      </div>
      {isLoading ? (
        <div className="h-48 animate-pulse rounded-xl bg-slate-100" />
      ) : isError ? (
        <Card>
          <ErrorState onRetry={() => void refetch()} />
        </Card>
      ) : !data?.length ? (
        <Card>
          <EmptyState icon={ReceiptText} title={status === 'PENDING_REVIEW' ? '没有待审核的付款' : '没有记录'} />
        </Card>
      ) : (
        <div className="space-y-4">
          {data.map((o) => (
            <PaymentCard key={o.id} order={o} />
          ))}
        </div>
      )}
    </div>
  )
}

function ProofImage({ id }: { id: number }) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    fetchImageBlob(`/admin/payment-orders/${id}/proof-image`)
      .then((u) => {
        objectUrl = u
        if (!cancelled) setUrl(u)
      })
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [id])
  if (failed) return <p className="text-sm text-red-600">截图加载失败</p>
  if (!url) return <div className="h-48 w-36 animate-pulse rounded-lg bg-slate-100" />
  return (
    <a href={url} target="_blank" rel="noreferrer" title="点击查看大图">
      <img src={url} alt="付款截图" className="max-h-72 rounded-lg border border-slate-200" />
    </a>
  )
}

function PaymentCard({ order }: { order: PaymentOrder }) {
  const queryClient = useQueryClient()
  const [approving, setApproving] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)

  const done = (msg: string) => {
    void queryClient.invalidateQueries({ queryKey: ['admin'] })
    toast.success(msg)
  }
  const approve = useMutation({
    mutationFn: () => adminApi.approve(order.id),
    onSuccess: () => {
      setApproving(false)
      done(`已为「${order.tenantName}」开通`)
    },
    onError: (e) => toast.error(errorMessage(e, '操作失败')),
  })
  const reject = useMutation({
    mutationFn: () => adminApi.reject(order.id, reason.trim()),
    onSuccess: () => {
      setRejecting(false)
      done('已拒绝')
    },
    onError: (e) => toast.error(errorMessage(e, '操作失败')),
  })

  const pending = order.status === 'PENDING_REVIEW'

  return (
    <Card>
      <CardBody className="grid gap-5 md:grid-cols-[1fr_auto] [&>*]:min-w-0">
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-base font-semibold text-slate-900">{order.tenantName ?? `店铺 #${order.tenantId}`}</span>
            <Badge tone={PAYMENT_STATUS_TONES[order.status]}>{PAYMENT_STATUS_LABELS[order.status]}</Badge>
          </div>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-slate-500">金额</dt>
            <dd className="text-lg font-semibold tabular-nums text-slate-900">{formatMoney(order.amount, order.currency)}</dd>
            <dt className="text-slate-500">套餐</dt>
            <dd>
              {PLAN_LABELS[order.plan]} · {PERIOD_LABELS[order.period]}（{order.durationDays} 天）
            </dd>
            <dt className="text-slate-500">收款方式</dt>
            <dd>{order.method?.displayName ?? '—'}</dd>
            <dt className="text-slate-500">付款备注应为</dt>
            <dd>
              <span className="rounded bg-amber-50 px-1.5 py-0.5 font-mono font-semibold text-amber-800">{order.referenceCode}</span>
            </dd>
            <dt className="text-slate-500">交易单号</dt>
            <dd className="break-all font-mono">{order.transactionRef ?? '—'}</dd>
            {order.payerNote && (
              <>
                <dt className="text-slate-500">留言</dt>
                <dd className="break-words">{order.payerNote}</dd>
              </>
            )}
            <dt className="text-slate-500">单号</dt>
            <dd className="font-mono text-slate-600">{order.orderNo}</dd>
            <dt className="text-slate-500">下单 / 提交</dt>
            <dd className="text-slate-600">
              {formatDateTime(order.createdAt)} / {formatDateTime(order.submittedAt)}
            </dd>
            {order.reviewedAt && (
              <>
                <dt className="text-slate-500">审核时间</dt>
                <dd className="text-slate-600">{formatDateTime(order.reviewedAt)}</dd>
              </>
            )}
            {order.rejectReason && (
              <>
                <dt className="text-slate-500">拒绝原因</dt>
                <dd className="text-red-600">{order.rejectReason}</dd>
              </>
            )}
          </dl>
          {pending && (
            <div className="flex gap-2 pt-1">
              <Button onClick={() => setApproving(true)}>确认到账并开通</Button>
              <Button variant="secondary" onClick={() => setRejecting(true)}>
                拒绝
              </Button>
            </div>
          )}
        </div>
        {order.hasProof && <ProofImage id={order.id} />}
      </CardBody>

      <ConfirmDialog
        open={approving}
        onOpenChange={setApproving}
        title="确认已收到这笔付款？"
        description={`将为「${order.tenantName}」开通${PLAN_LABELS[order.plan]} ${order.durationDays} 天。请确认收款 App 中有 ${formatMoney(order.amount, order.currency)}、备注 ${order.referenceCode} 的到账记录。`}
        confirmText="确认开通"
        loading={approve.isPending}
        onConfirm={() => approve.mutate()}
      />
      <FormDialog
        open={rejecting}
        onClose={() => setRejecting(false)}
        title="拒绝这笔付款"
        description="店主会看到拒绝原因"
        submitText="拒绝"
        saving={reject.isPending}
        onSubmit={(e) => {
          e.preventDefault()
          if (!reason.trim()) {
            setReasonError('请填写拒绝原因')
            return
          }
          reject.mutate()
        }}
      >
        <Field label="原因" htmlFor={`rj-${order.id}`} error={reasonError ?? undefined} required>
          <Input
            id={`rj-${order.id}`}
            value={reason}
            maxLength={200}
            placeholder="例如：未查到到账记录 / 金额不符"
            onChange={(e) => {
              setReason(e.target.value)
              setReasonError(null)
            }}
          />
        </Field>
      </FormDialog>
    </Card>
  )
}
