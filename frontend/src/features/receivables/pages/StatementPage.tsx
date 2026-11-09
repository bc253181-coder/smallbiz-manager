import { useQuery } from '@tanstack/react-query'
import { Printer } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardBody } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorMessage } from '@/lib/api'
import { formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { receivableApi } from '../api'

import { tr } from '@/lib/i18n'
function ymd(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 客户对账单：期初欠款 + 新增欠款 − 收款 = 期末欠款，可打印（A4）。 */
export function StatementPage() {
  const customerId = Number(useParams().id)
  const [params, setParams] = useSearchParams()
  const now = new Date()
  const from = params.get('from') ?? ymd(new Date(now.getFullYear(), now.getMonth(), 1))
  const to = params.get('to') ?? ymd(now)

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['receivables', 'statement', customerId, from, to],
    queryFn: () => receivableApi.statement(customerId, from, to),
    enabled: from <= to,
  })
  const money = (v: string | null) => (v === null ? '' : formatMoney(v, data?.currency))

  function update(changes: Record<string, string>) {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(changes)) next.set(k, v)
    setParams(next, { replace: true })
  }

  return (
    <div>
      <div className="print:hidden">
        <PageHeader
          title={data ? tr('对账单 · {{customerName}}', { customerName: data.customerName }) : tr('对账单')}
          back={{ to: `/customers/${customerId}`, label: tr('客户详情') }}
          actions={
            <Button onClick={() => window.print()} disabled={!data}>
              <Printer aria-hidden />
              {tr('打印')}
            </Button>
          }
        />
        <Card className="mb-6">
          <CardBody className="flex flex-wrap items-center gap-2">
            <Input type="date" aria-label={tr('开始日期')} value={from} max={to} onChange={(e) => e.target.value && update({ from: e.target.value })} className="w-40" />
            <span className="text-slate-400">{tr('至')}</span>
            <Input type="date" aria-label={tr('结束日期')} value={to} min={from} onChange={(e) => e.target.value && update({ to: e.target.value })} className="w-40" />
            {from > to && <span className="text-sm text-red-600">{tr('开始日期不能晚于结束日期')}</span>}
          </CardBody>
        </Card>
      </div>

      {isLoading ? (
        <div className="h-96 animate-pulse rounded-xl bg-slate-100" />
      ) : isError || !data ? (
        <Card>
          <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
        </Card>
      ) : (
        <Card className="print:border-0 print:shadow-none">
          <CardBody className="space-y-6 print:p-0">
            <header className="text-center">
              <h1 className="text-xl font-bold text-slate-900">{data.storeName} {tr('· 客户对账单')}</h1>
              {(data.storePhone || data.storeAddress) && (
                <p className="mt-1 text-sm text-slate-600">{[data.storePhone, data.storeAddress].filter(Boolean).join(' · ')}</p>
              )}
            </header>
            <div className="flex flex-wrap justify-between gap-2 text-sm">
              <p>
                {tr('客户：')}<span className="font-medium">{data.customerName}</span>
                {data.customerPhone && <span className="text-slate-500">（{data.customerPhone}）</span>}
              </p>
              <p>
                {tr('期间：')}{data.from} {tr('至')} {data.to}
              </p>
            </div>

            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4 print:grid-cols-4">
              <Total label={tr('期初欠款')} value={money(data.openingBalance)} />
              <Total label={tr('本期新增欠款')} value={money(data.totalDebit)} />
              <Total label={tr('本期收款')} value={money(data.totalCredit)} />
              <Total label={tr('期末欠款')} value={money(data.closingBalance)} strong />
            </dl>

            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-sm">
                <thead className="border-y border-slate-200 bg-slate-50 text-left text-xs font-medium text-slate-500 print:bg-transparent">
                  <tr>
                    <th className="px-3 py-2">{tr('日期')}</th>
                    <th className="px-3 py-2">{tr('单号')}</th>
                    <th className="px-3 py-2">{tr('摘要')}</th>
                    <th className="px-3 py-2 text-right">{tr('销售金额')}</th>
                    <th className="px-3 py-2 text-right">{tr('当场付')}</th>
                    <th className="px-3 py-2 text-right">{tr('新增欠款')}</th>
                    <th className="px-3 py-2 text-right">{tr('收款')}</th>
                    <th className="px-3 py-2 text-right">{tr('欠款余额')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  <tr className="text-slate-500">
                    <td className="px-3 py-2" colSpan={7}>
                      {tr('期初欠款')}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{money(data.openingBalance)}</td>
                  </tr>
                  {data.lines.map((l) => (
                    <tr key={`${l.type}-${l.refId}`}>
                      <td className="whitespace-nowrap px-3 py-2">{formatDateTime(l.date)}</td>
                      <td className="whitespace-nowrap px-3 py-2">
                        {l.type === 'SALE' ? (
                          <Link to={`/sales/${l.refId}`} className="text-blue-600 hover:underline print:text-black">
                            {l.docNo}
                          </Link>
                        ) : (
                          <span className="font-mono text-xs">{l.docNo}</span>
                        )}
                      </td>
                      <td className="px-3 py-2">{l.description}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{money(l.amount)}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{money(l.paidAtSale)}</td>
                      <td className="px-3 py-2 text-right tabular-nums">{Number(l.debit) > 0 ? money(l.debit) : ''}</td>
                      <td className="px-3 py-2 text-right tabular-nums text-emerald-700">{Number(l.credit) > 0 ? money(l.credit) : ''}</td>
                      <td className="px-3 py-2 text-right font-medium tabular-nums">{money(l.balance)}</td>
                    </tr>
                  ))}
                  {data.lines.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-3 py-6 text-center text-slate-500">
                        {tr('本期间没有销售和收款')}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <footer className="flex flex-wrap justify-between gap-2 border-t border-slate-100 pt-4 text-xs text-slate-500">
              <span>{tr('生成时间：')}{formatDateTime(data.generatedAt)}{tr('。已取消的销售单、已作废的收款不计入。')}</span>
              <span className="hidden print:inline">{tr('客户确认签字：________________')}</span>
            </footer>
          </CardBody>
        </Card>
      )}
    </div>
  )
}

function Total({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={cn('rounded-lg border p-3', strong ? 'border-blue-200 bg-blue-50 print:bg-transparent' : 'border-slate-200')}>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={cn('mt-0.5 font-semibold tabular-nums', strong ? 'text-lg text-blue-800 print:text-black' : 'text-slate-900')}>{value}</dd>
    </div>
  )
}
