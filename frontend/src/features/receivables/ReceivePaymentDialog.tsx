import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { Field, Input, Select, Textarea } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { fromCents, isMoney, toCents } from '@/lib/money'
import { METHOD_LABELS, RECEIVABLE_QUERY_KEYS, receivableApi, type OpenOrder, type PaymentMethod } from './api'

/** 本地时间 → datetime-local 输入框的值。 */
function localInputValue(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 预览先欠先还的冲抵结果（与后端规则一致，按分计算）。 */
function allocate(orders: OpenOrder[], cents: number) {
  const result: { order: OpenOrder; part: number }[] = []
  orders.reduce((left, o) => {
    const part = Math.min(left, toCents(o.outstanding) ?? 0)
    if (part > 0) result.push({ order: o, part })
    return left - part
  }, cents)
  return result
}

/**
 * 登记收款。salesOrderId 有值时只收这张单；否则按开单时间先欠先还，并在提交前预览冲抵结果。
 * 由调用方在打开时挂载（关闭即卸载），每次打开都是新的表单。
 */
export function ReceivePaymentDialog({
  customerId,
  customerName,
  salesOrderId,
  onClose,
}: {
  customerId: number
  customerName: string
  salesOrderId?: number
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const money = useMoney()
  const { data, isLoading } = useQuery({
    queryKey: ['customer-receivable', customerId],
    queryFn: () => receivableApi.customer(customerId),
  })
  const targets = (data?.orders ?? []).filter((o) => !salesOrderId || o.id === salesOrderId)
  const maxCents = targets.reduce((s, o) => s + (toCents(o.outstanding) ?? 0), 0)

  const [amount, setAmount] = useState<string | null>(null)
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [receivedAt, setReceivedAt] = useState(() => localInputValue(new Date()))
  // 没改过收款时间时不传，由服务器取当前时间（输入框只精确到分钟，可能早于刚开的单）
  const [timeEdited, setTimeEdited] = useState(false)
  const [notes, setNotes] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  // 金额默认 = 全部欠款（数据加载后）
  const amountValue = amount ?? (data ? fromCents(maxCents) : '')
  const cents = isMoney(amountValue) ? (toCents(amountValue) ?? 0) : 0

  const preview = allocate(targets, cents)

  const save = useMutation({
    mutationFn: () =>
      receivableApi.receive({
        customerId,
        amount: amountValue,
        method,
        receivedAt: timeEdited && receivedAt ? new Date(receivedAt).toISOString() : null,
        notes,
        salesOrderId: salesOrderId ?? null,
      }),
    onSuccess: async (p) => {
      await Promise.all(RECEIVABLE_QUERY_KEYS.map((k) => queryClient.invalidateQueries({ queryKey: k })))
      toast.success(`已收款 ${money(p.amount)}（${p.paymentNo}）`)
      onClose()
    },
    onError: (e) => {
      const fields = fieldErrors(e)
      // 收款时间相关的错误显示在时间输入框下
      if (errorCode(e) === 'INVALID_RECEIVED_AT') fields.receivedAt = errorMessage(e)
      setErrors(fields)
      toast.error(errorMessage(e, '收款失败'))
    },
  })

  return (
    <FormDialog
      open
      onClose={onClose}
      title={`收款：${customerName}`}
      description={salesOrderId ? '只冲抵这张销售单' : '按开单时间先欠先还'}
      submitText="确认收款"
      saving={save.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        const errs: Record<string, string> = {}
        if (!isMoney(amountValue) || cents <= 0) errs.amount = '请输入正确的金额'
        else if (cents > maxCents) errs.amount = `不能超过欠款 ${money(fromCents(maxCents))}`
        if (receivedAt && new Date(receivedAt).getTime() > Date.now() + 60_000) errs.receivedAt = '收款时间不能晚于现在'
        setErrors(errs)
        if (Object.keys(errs).length === 0) save.mutate()
      }}
    >
      {isLoading ? (
        <div className="h-24 animate-pulse rounded-lg bg-slate-100" />
      ) : maxCents === 0 ? (
        <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">没有需要收款的欠款。</p>
      ) : (
        <>
          <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
            {salesOrderId ? '这张单' : '当前'}欠款 <span className="font-semibold tabular-nums">{money(fromCents(maxCents))}</span>
          </p>
          <Field label="收款金额" htmlFor="rp-amount" error={errors.amount} required>
            <Input
              id="rp-amount"
              inputMode="decimal"
              value={amountValue}
              onChange={(e) => setAmount(e.target.value)}
              invalid={!!errors.amount}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="收款方式" htmlFor="rp-method">
              <Select id="rp-method" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                {(Object.keys(METHOD_LABELS) as PaymentMethod[]).map((m) => (
                  <option key={m} value={m}>
                    {METHOD_LABELS[m]}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="收款时间" htmlFor="rp-time" error={errors.receivedAt}>
              <Input id="rp-time" type="datetime-local" value={receivedAt} onChange={(e) => {
                setReceivedAt(e.target.value)
                setTimeEdited(true)
              }}
              />
            </Field>
          </div>
          <Field label="备注" htmlFor="rp-notes">
            <Textarea id="rp-notes" rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          {preview.length > 0 && (
            <div className="rounded-lg border border-slate-200 text-sm">
              <p className="border-b border-slate-100 px-3 py-2 text-xs font-medium text-slate-500">将冲抵以下销售单</p>
              <ul className="divide-y divide-slate-100">
                {preview.map(({ order, part }) => (
                  <li key={order.id} className="flex items-center justify-between gap-3 px-3 py-2">
                    <span className="min-w-0">
                      <span className="block truncate font-medium text-slate-800">{order.orderNo}</span>
                      <span className="block text-xs text-slate-500">{formatDateTime(order.orderDate)}</span>
                    </span>
                    <span className="shrink-0 text-right tabular-nums">
                      {money(fromCents(part))}
                      {part < (toCents(order.outstanding) ?? 0) && (
                        <span className="block text-xs text-slate-500">仍欠 {money(fromCents((toCents(order.outstanding) ?? 0) - part))}</span>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </FormDialog>
  )
}
