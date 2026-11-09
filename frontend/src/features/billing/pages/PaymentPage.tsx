import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CircleCheck, Clock, Copy, ImageUp, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { PLAN_LABELS } from '@/features/auth/types'
import { errorMessage, refreshSession } from '@/lib/api'
import { formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  billingApi,
  fetchImageBlob,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONES,
  PERIOD_LABELS,
  type PaymentOrder,
} from '../api'

import { tr } from '@/lib/i18n'
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPT = ['image/jpeg', 'image/png', 'image/webp']

export function PaymentPage() {
  const id = Number(useParams().id)
  const queryClient = useQueryClient()
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['billing', 'payments', id],
    queryFn: () => billingApi.payment(id),
    // 审核中每 30 秒刷新一次状态
    refetchInterval: (q) => (q.state.data?.status === 'PENDING_REVIEW' ? 30_000 : false),
  })

  // 本页停留期间审核通过 → 刷新套餐和会话
  const prevStatus = useRef<string | undefined>(undefined)
  useEffect(() => {
    if (!data) return
    if (prevStatus.current && prevStatus.current !== 'APPROVED' && data.status === 'APPROVED') {
      void queryClient.invalidateQueries({ queryKey: ['billing'] })
      void refreshSession()
      toast.success(tr('付款已确认，套餐已开通'))
    }
    prevStatus.current = data.status
  }, [data, queryClient])

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={tr('扫码付款')}
        description={data ? tr('付款单 {{orderNo}}', { orderNo: data.orderNo }) : undefined}
        back={{ to: '/settings/billing', label: tr('套餐与续费') }}
      />
      {isLoading ? (
        <div className="h-96 animate-pulse rounded-xl bg-slate-100" />
      ) : isError || !data ? (
        <Card>
          <ErrorState onRetry={() => void refetch()} message={tr('付款单不存在或加载失败')} />
        </Card>
      ) : (
        <PaymentBody order={data} />
      )}
    </div>
  )
}

function PaymentBody({ order }: { order: PaymentOrder }) {
  return (
    <div className="space-y-6">
      <Card>
        <CardBody className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm text-slate-500">
              {PLAN_LABELS[order.plan]} · {PERIOD_LABELS[order.period]}（{order.durationDays} {tr('天）')}
            </p>
            <p className="text-3xl font-semibold tabular-nums text-slate-900">{formatMoney(order.amount, order.currency)}</p>
          </div>
          <Badge tone={PAYMENT_STATUS_TONES[order.status]} className="px-3 py-1 text-sm">
            {PAYMENT_STATUS_LABELS[order.status]}
          </Badge>
        </CardBody>
      </Card>

      {order.status === 'AWAITING_PAYMENT' && <AwaitingPayment order={order} />}
      {order.status === 'PENDING_REVIEW' && (
        <StatusCard icon={Clock} tone="blue" title={tr('已提交，等待人工核对')}>
          {tr('我们会核对到账金额和付款备注，通常几小时内开通（夜间可能顺延到第二天）。开通后刷新页面即可使用标准版功能。')}
          <ProofSummary order={order} />
        </StatusCard>
      )}
      {order.status === 'APPROVED' && (
        <StatusCard icon={CircleCheck} tone="green" title={tr('付款已确认，套餐已开通')}>
          <Link to="/" className={cn(buttonVariants({ size: 'sm' }), 'mt-3')}>
            {tr('开始使用')}
          </Link>
        </StatusCard>
      )}
      {order.status === 'REJECTED' && (
        <StatusCard icon={TriangleAlert} tone="red" title={tr('付款未通过核对')}>
          {tr('原因：')}{order.rejectReason}
          <p className="mt-2">{tr('如果你确实已付款，请通过右下角客服联系我们并提供付款截图，我们会尽快处理。')}</p>
          <Link to="/settings/billing" className={cn(buttonVariants({ size: 'sm', variant: 'secondary' }), 'mt-3')}>
            {tr('重新下单')}
          </Link>
        </StatusCard>
      )}
      {(order.status === 'EXPIRED' || order.status === 'CANCELLED') && (
        <StatusCard icon={Clock} tone="slate" title={order.status === 'EXPIRED' ? tr('付款单已过期') : tr('付款单已取消')}>
          {order.status === 'EXPIRED' && tr('付款单在 24 小时内有效。如果你已经付款，请联系客服处理。')}
          <Link to="/settings/billing" className={cn(buttonVariants({ size: 'sm', variant: 'secondary' }), 'mt-3')}>
            {tr('重新下单')}
          </Link>
        </StatusCard>
      )}
    </div>
  )
}

function StatusCard({
  icon: Icon,
  tone,
  title,
  children,
}: {
  icon: typeof Clock
  tone: 'blue' | 'green' | 'red' | 'slate'
  title: string
  children?: ReactNode
}) {
  const tones = {
    blue: 'bg-blue-50 text-blue-600',
    green: 'bg-emerald-50 text-emerald-600',
    red: 'bg-red-50 text-red-600',
    slate: 'bg-slate-100 text-slate-500',
  }
  return (
    <Card>
      <CardBody className="flex gap-4">
        <span className={cn('flex size-10 shrink-0 items-center justify-center rounded-full', tones[tone])}>
          <Icon className="size-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1 text-sm text-slate-600">
          <h2 className="mb-1 text-base font-semibold text-slate-900">{title}</h2>
          {children}
        </div>
      </CardBody>
    </Card>
  )
}

function ProofSummary({ order }: { order: PaymentOrder }) {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!order.hasProof) return
    let revoked = false
    let objectUrl: string | null = null
    fetchImageBlob(`/billing/payment-orders/${order.id}/proof-image`)
      .then((u) => {
        objectUrl = u
        if (!revoked) setUrl(u)
      })
      .catch(() => undefined)
    return () => {
      revoked = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [order.id, order.hasProof])

  return (
    <dl className="mt-3 grid gap-x-4 gap-y-1 sm:grid-cols-[auto_1fr]">
      <dt className="text-slate-500">{tr('提交时间')}</dt>
      <dd>{formatDateTime(order.submittedAt)}</dd>
      {order.transactionRef && (
        <>
          <dt className="text-slate-500">{tr('交易单号')}</dt>
          <dd className="break-all font-mono">{order.transactionRef}</dd>
        </>
      )}
      {url && (
        <>
          <dt className="text-slate-500">{tr('付款截图')}</dt>
          <dd>
            <img src={url} alt={tr('付款截图')} className="mt-1 max-h-48 rounded-lg border border-slate-200" />
          </dd>
        </>
      )}
    </dl>
  )
}

function useCountdown(iso: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(t)
  }, [])
  const ms = new Date(iso).getTime() - now
  if (ms <= 0) return tr('已到期')
  const h = Math.floor(ms / 3_600_000)
  const m = Math.floor((ms % 3_600_000) / 60_000)
  return h > 0 ? tr('{{h}} 小时 {{m}} 分钟', { h: h, m: m }) : tr('{{m}} 分钟', { m: m })
}

function AwaitingPayment({ order }: { order: PaymentOrder }) {
  const queryClient = useQueryClient()
  const method = order.method
  const remaining = useCountdown(order.expiresAt)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)
  const [txn, setTxn] = useState('')
  const [note, setNote] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)

  // 离开页面时释放预览图
  useEffect(() => () => void (preview && URL.revokeObjectURL(preview)), [preview])

  const update = (o: PaymentOrder) => {
    queryClient.setQueryData(['billing', 'payments', o.id], o)
    void queryClient.invalidateQueries({ queryKey: ['billing', 'payments'], exact: true })
  }

  const submit = useMutation({
    mutationFn: () => billingApi.submitProof(order.id, file!, txn, note),
    onSuccess: (o) => {
      update(o)
      toast.success(tr('已提交，请等待核对'))
    },
    onError: (e) => toast.error(errorMessage(e, tr('提交失败'))),
  })
  const cancel = useMutation({
    mutationFn: () => billingApi.cancel(order.id),
    onSuccess: (o) => {
      update(o)
      setConfirmCancel(false)
      toast.success(tr('付款单已取消'))
    },
    onError: (e) => toast.error(errorMessage(e, tr('取消失败'))),
  })

  function pick(f: File | undefined) {
    setFileError(null)
    if (!f) return
    if (!ACCEPT.includes(f.type)) {
      setFileError(tr('请上传 JPG、PNG 或 WebP 格式的截图'))
      return
    }
    if (f.size > MAX_BYTES) {
      setFileError(tr('截图不能超过 5MB'))
      return
    }
    setFile(f)
    setPreview(URL.createObjectURL(f))
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast.success(tr('已复制'))
    } catch {
      toast.error(tr('复制失败，请手动输入'))
    }
  }

  return (
    <>
      <Card>
        <CardHeader title={tr('第 1 步：用{{v0}}扫码付款', { v0: method?.displayName ?? tr('收款码') })} description={tr('请在 {{remaining}} 内完成付款', { remaining: remaining })} />
        <CardBody className="grid gap-6 sm:grid-cols-2 [&>*]:min-w-0">
          <div className="flex justify-center">
            {method?.qrImageUrl ? (
              <img
                src={method.qrImageUrl}
                alt={tr('{{displayName}}收款码', { displayName: method.displayName })}
                className="w-full max-w-64 rounded-xl border border-slate-200 bg-white"
              />
            ) : (
              <p className="text-sm text-slate-500">{tr('收款码暂不可用，请联系客服')}</p>
            )}
          </div>
          <div className="space-y-4 text-sm">
            <div>
              <p className="text-slate-500">{tr('付款金额（请输入准确金额）')}</p>
              <p className="text-2xl font-semibold tabular-nums text-slate-900">{formatMoney(order.amount, order.currency)}</p>
            </div>
            {method?.payeeName && (
              <div>
                <p className="text-slate-500">{tr('收款人')}</p>
                <p className="font-medium text-slate-800">{method.payeeName}</p>
              </div>
            )}
            <div>
              <p className="text-slate-500">{tr('付款备注（必填，用于核对）')}</p>
              <div className="mt-1 flex items-center gap-2">
                <span className="rounded-lg bg-amber-50 px-3 py-1.5 font-mono text-lg font-semibold tracking-wider text-amber-800">
                  {order.referenceCode}
                </span>
                <Button size="sm" variant="ghost" onClick={() => void copy(order.referenceCode)} aria-label={tr('复制付款备注')}>
                  <Copy aria-hidden />
                </Button>
              </div>
            </div>
            {method?.instructions && <p className="whitespace-pre-line rounded-lg bg-slate-50 p-3 text-slate-600">{method.instructions}</p>}
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title={tr('第 2 步：上传付款截图')} description={tr('截图需能看到金额、时间和付款备注')} />
        <CardBody>
          <form
            className="space-y-4"
            noValidate
            onSubmit={(e) => {
              e.preventDefault()
              if (!file) {
                setFileError(tr('请上传付款截图'))
                return
              }
              submit.mutate()
            }}
          >
            <Field label={tr('付款截图')} htmlFor="proof-file" error={fileError ?? undefined} required>
              <label
                htmlFor="proof-file"
                className={cn(
                  'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-5 text-sm text-slate-500 hover:bg-slate-50',
                  fileError ? 'border-red-300' : 'border-slate-300',
                )}
              >
                {preview ? (
                  <img src={preview} alt={tr('已选择的截图')} className="max-h-56 rounded-lg" />
                ) : (
                  <>
                    <ImageUp className="size-8 text-slate-400" aria-hidden />
                    {tr('点击选择截图（JPG / PNG / WebP，不超过 5MB）')}
                  </>
                )}
              </label>
              <input
                id="proof-file"
                type="file"
                accept={ACCEPT.join(',')}
                className="sr-only"
                onChange={(e) => pick(e.target.files?.[0])}
              />
            </Field>
            <Field label={tr('交易单号')} htmlFor="proof-txn" hint={tr('在付款详情里可以找到，填写后核对更快（选填）')}>
              <Input id="proof-txn" value={txn} maxLength={100} onChange={(e) => setTxn(e.target.value)} autoComplete="off" />
            </Field>
            <Field label={tr('留言')} htmlFor="proof-note" hint={tr('选填')}>
              <Input id="proof-note" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} />
            </Field>
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
              <Button variant="ghost" onClick={() => setConfirmCancel(true)} disabled={submit.isPending}>
                {tr('取消付款单')}
              </Button>
              <Button type="submit" loading={submit.isPending}>
                {tr('我已付款，提交核对')}
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>

      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={tr('取消这张付款单？')}
        description={tr('如果你已经付款，请不要取消，直接上传截图即可。')}
        confirmText={tr('取消付款单')}
        danger
        loading={cancel.isPending}
        onConfirm={() => cancel.mutate()}
      />
    </>
  )
}
