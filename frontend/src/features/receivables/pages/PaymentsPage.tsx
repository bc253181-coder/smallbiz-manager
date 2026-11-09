import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ReceiptText } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Field, Input } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { errorMessage } from '@/lib/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { METHOD_LABELS, RECEIVABLE_QUERY_KEYS, receivableApi, type CustomerPayment } from '../api'

import { tr } from '@/lib/i18n'
const STATUS_TABS = [
  { value: '', label: tr('全部') },
  { value: 'VALID', label: tr('有效') },
  { value: 'VOIDED', label: tr('已作废') },
]

export function PaymentsPage() {
  const money = useMoney()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const customerId = params.get('customerId') ? Number(params.get('customerId')) : undefined
  const page = Number(params.get('page') ?? '0')
  const [voiding, setVoiding] = useState<CustomerPayment | null>(null)

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['customer-payments', { status, customerId, page }],
    queryFn: () => receivableApi.payments({ status, customerId, page, size: 20 }),
  })

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) {
      if (v === null || v === '') next.delete(k)
      else next.set(k, v)
    }
    setParams(next, { replace: true })
  }

  return (
    <div>
      <PageHeader title={tr('收款记录')} description={tr('收款只能作废、不能修改；作废后相关销售单恢复为未结清')} back={{ to: '/receivables', label: tr('应收账款') }} />
      <div className="-mx-1 mb-4 flex gap-1 overflow-x-auto px-1" role="tablist" aria-label={tr('按状态筛选')}>
        {STATUS_TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={status === t.value}
            onClick={() => update({ status: t.value, page: null })}
            className={cn(
              'whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium',
              status === t.value ? 'bg-white text-blue-700 shadow-sm ring-1 ring-slate-200' : 'text-slate-600 hover:bg-white/60',
            )}
          >
            {t.label}
          </button>
        ))}
        {customerId && (
          <button type="button" onClick={() => update({ customerId: null, page: null })} className="ml-auto whitespace-nowrap text-sm text-blue-600 hover:underline">
            {tr('显示所有客户')}
          </button>
        )}
      </div>
      <Card>
        {isLoading ? (
          <div className="m-5 h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
        ) : !data?.items.length ? (
          <EmptyState icon={ReceiptText} title={tr('还没有收款记录')} />
        ) : (
          <ul className="divide-y divide-slate-100">
            {data.items.map((p) => (
              <li key={p.id} className={cn('flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:gap-4', p.status === 'VOIDED' && 'bg-slate-50/60')}>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-slate-900">{p.customerName ?? '—'}</span>
                    <span className="font-mono text-xs text-slate-500">{p.paymentNo}</span>
                    {p.status === 'VOIDED' && <Badge tone="red">{tr('已作废')}</Badge>}
                  </div>
                  <p className="text-xs text-slate-500">
                    {formatDateTime(p.receivedAt)} · {METHOD_LABELS[p.method]} {tr('· 经手人')} {p.createdByName ?? '—'}
                    {p.notes ? ` · ${p.notes}` : ''}
                  </p>
                  <p className="mt-1 text-xs text-slate-600">
                    {tr('冲抵：')}
                    {p.allocations.map((a, i) => (
                      <span key={a.salesOrderId}>
                        {i > 0 && '、'}
                        <Link to={`/sales/${a.salesOrderId}`} className="text-blue-600 hover:underline">
                          {a.orderNo}
                        </Link>{' '}
                        {money(a.amount)}
                      </span>
                    ))}
                  </p>
                  {p.status === 'VOIDED' && (
                    <p className="mt-1 text-xs text-red-700">
                      {formatDateTime(p.voidedAt)} {tr('由')} {p.voidedByName ?? '—'} {tr('作废：')}{p.voidReason}
                    </p>
                  )}
                </div>
                <div className="flex items-center justify-between gap-3 sm:justify-end">
                  <span className={cn('text-lg font-semibold tabular-nums', p.status === 'VOIDED' ? 'text-slate-400 line-through' : 'text-slate-900')}>
                    {money(p.amount)}
                  </span>
                  {p.status === 'VALID' && (
                    <Button size="sm" variant="ghost" className="text-red-600" onClick={() => setVoiding(p)}>
                      {tr('作废')}
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {data && data.totalPages > 1 && (
          <div className="border-t border-slate-100 px-4 py-3">
            <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} onChange={(p) => update({ page: String(p) })} />
          </div>
        )}
      </Card>
      {voiding && <VoidDialog payment={voiding} onClose={() => setVoiding(null)} />}
    </div>
  )
}

function VoidDialog({ payment, onClose }: { payment: CustomerPayment; onClose: () => void }) {
  const queryClient = useQueryClient()
  const money = useMoney()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const mutation = useMutation({
    mutationFn: () => receivableApi.voidPayment(payment.id, reason.trim()),
    onSuccess: async () => {
      await Promise.all(RECEIVABLE_QUERY_KEYS.map((k) => queryClient.invalidateQueries({ queryKey: k })))
      toast.success(tr('收款已作废，相关销售单已恢复为未结清'))
      onClose()
    },
    onError: (e) => toast.error(errorMessage(e, tr('作废失败'))),
  })
  return (
    <FormDialog
      open
      onClose={onClose}
      title={tr('作废收款 {{paymentNo}}', { paymentNo: payment.paymentNo })}
      description={tr('{{v0}} · {{amount}}。作废后不能恢复，如需要请重新登记收款。', { v0: payment.customerName ?? '', amount: money(payment.amount) })}
      submitText={tr('确认作废')}
      saving={mutation.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        if (!reason.trim()) {
          setError(tr('请填写作废原因'))
          return
        }
        mutation.mutate()
      }}
    >
      <Field label={tr('作废原因')} htmlFor="void-reason" error={error ?? undefined} required>
        <Input
          id="void-reason"
          value={reason}
          maxLength={200}
          placeholder={tr('例如：金额登记错误')}
          onChange={(e) => {
            setReason(e.target.value)
            setError(null)
          }}
        />
      </Field>
    </FormDialog>
  )
}
