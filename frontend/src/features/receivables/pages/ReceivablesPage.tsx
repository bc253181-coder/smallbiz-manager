import { useQuery } from '@tanstack/react-query'
import { Download, FileText, HandCoins, ReceiptText, Search } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { downloadCsv } from '@/features/reports/api'
import { errorMessage } from '@/lib/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { useDebouncedValue } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { receivableApi, type Aging, type ReceivableCustomerRow } from '../api'
import { ReceivePaymentDialog } from '../ReceivePaymentDialog'

const AGE_FILTERS = [
  { value: '', label: '全部' },
  { value: '30', label: '超过 30 天' },
  { value: '60', label: '超过 60 天' },
  { value: '90', label: '超过 90 天' },
]

export function ReceivablesPage() {
  const money = useMoney()
  const [params, setParams] = useSearchParams()
  const minAge = params.get('age') ?? ''
  const [search, setSearch] = useState('')
  const q = useDebouncedValue(search.trim())
  const [paying, setPaying] = useState<ReceivableCustomerRow | null>(null)
  const [exporting, setExporting] = useState(false)

  const summary = useQuery({ queryKey: ['receivables', 'summary'], queryFn: receivableApi.summary })
  const list = useQuery({
    queryKey: ['receivables', 'customers', q, minAge],
    queryFn: () => receivableApi.customers({ q, minAgeDays: minAge ? Number(minAge) : undefined }),
  })
  const s = summary.data

  return (
    <div>
      <PageHeader
        title="应收账款"
        description="客户赊账未结清的金额。收款按开单时间先欠先还；账龄按开单日期计算"
        actions={
          <Link to="/receivables/payments" className={buttonVariants({ variant: 'secondary' })}>
            <ReceiptText aria-hidden />
            收款记录
          </Link>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4 [&>*]:min-w-0">
        <Stat label="总欠款" value={s ? money(s.totalOutstanding) : undefined} sub={s ? `${s.customerCount} 位客户 · ${s.openOrderCount} 张单` : undefined} />
        <Stat label="逾期超过 30 天" value={s ? money(s.overdue30) : undefined} tone={s && Number(s.overdue30) > 0 ? 'red' : undefined} />
        <Stat label="本月已收回" value={s ? money(s.collectedThisMonth) : undefined} sub="不含开单时当场收的钱" />
        <AgingCard aging={s?.aging} />
      </div>

      <Card>
        <CardHeader
          title="欠款客户"
          action={
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <div className="relative sm:w-56">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
                <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="客户名称或电话" className="pl-9" aria-label="搜索客户" />
              </div>
              <Button
                variant="secondary"
                size="sm"
                loading={exporting}
                onClick={async () => {
                  setExporting(true)
                  try {
                    await downloadCsv('/receivables/export', { q, minAgeDays: minAge }, '应收账款.csv')
                  } catch (e) {
                    toast.error(errorMessage(e, '导出失败'))
                  } finally {
                    setExporting(false)
                  }
                }}
              >
                <Download aria-hidden />
                导出 CSV
              </Button>
            </div>
          }
        />
        <div className="flex gap-1 overflow-x-auto border-b border-slate-100 px-4 py-2" role="tablist" aria-label="按账龄筛选">
          {AGE_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              role="tab"
              aria-selected={minAge === f.value}
              onClick={() => setParams(f.value ? { age: f.value } : {}, { replace: true })}
              className={cn(
                'whitespace-nowrap rounded-lg px-3 py-1.5 text-sm',
                minAge === f.value ? 'bg-blue-50 font-medium text-blue-700' : 'text-slate-600 hover:bg-slate-50',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
        {list.isLoading ? (
          <div className="m-5 h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : list.isError ? (
          <ErrorState onRetry={() => void list.refetch()} message={errorMessage(list.error)} />
        ) : !list.data?.length ? (
          <EmptyState
            icon={HandCoins}
            title={q || minAge ? '没有符合条件的欠款客户' : '目前没有欠款'}
            description={q || minAge ? undefined : '开单时「本次实收」少于应收，差额会记为客户欠款'}
          />
        ) : (
          <>
            {/* 手机：卡片 */}
            <ul className="divide-y divide-slate-100 md:hidden">
              {list.data.map((r) => (
                <li key={r.customerId} className="space-y-2 px-4 py-3">
                  <div className="flex items-start justify-between gap-3">
                    <Link to={`/customers/${r.customerId}`} className="min-w-0">
                      <span className="block truncate font-medium text-slate-900">{r.customerName}</span>
                      <span className="block text-xs text-slate-500">
                        {r.openOrderCount} 张未结清 · 最早 {r.oldestDays} 天前
                      </span>
                    </Link>
                    <span className="shrink-0 text-right font-semibold tabular-nums text-slate-900">{money(r.outstanding)}</span>
                  </div>
                  <RowActions row={r} onPay={() => setPaying(r)} />
                </li>
              ))}
            </ul>
            {/* 桌面：表格 */}
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                  <tr>
                    <th className="px-4 py-2">客户</th>
                    <th className="px-4 py-2 text-right">欠款</th>
                    <th className="px-4 py-2 text-right">未结清</th>
                    <th className="px-4 py-2">最早欠款</th>
                    <th className="px-4 py-2 text-right">超过 30 天</th>
                    <th className="px-4 py-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {list.data.map((r) => {
                    const over30 = Number(r.aging.days31To60) + Number(r.aging.days61To90) + Number(r.aging.over90)
                    return (
                      <tr key={r.customerId}>
                        <td className="px-4 py-2.5">
                          <Link to={`/customers/${r.customerId}`} className="font-medium text-slate-900 hover:text-blue-600">
                            {r.customerName}
                          </Link>
                          {r.phone && <p className="text-xs text-slate-500">{r.phone}</p>}
                        </td>
                        <td className="px-4 py-2.5 text-right font-semibold tabular-nums">{money(r.outstanding)}</td>
                        <td className="px-4 py-2.5 text-right tabular-nums">{r.openOrderCount} 张</td>
                        <td className="whitespace-nowrap px-4 py-2.5 text-slate-600">
                          {formatDateTime(r.oldestOrderDate).slice(0, 10)}
                          <span className={cn('ml-1 text-xs', r.oldestDays > 30 ? 'text-red-600' : 'text-slate-400')}>（{r.oldestDays} 天）</span>
                        </td>
                        <td className={cn('px-4 py-2.5 text-right tabular-nums', over30 > 0 ? 'text-red-600' : 'text-slate-400')}>
                          {over30 > 0 ? money(String(over30.toFixed(2))) : '—'}
                        </td>
                        <td className="px-4 py-2.5">
                          <RowActions row={r} onPay={() => setPaying(r)} />
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Card>

      {paying && (
        <ReceivePaymentDialog customerId={paying.customerId} customerName={paying.customerName} onClose={() => setPaying(null)} />
      )}
    </div>
  )
}

function RowActions({ row, onPay }: { row: ReceivableCustomerRow; onPay: () => void }) {
  return (
    <div className="flex justify-end gap-2">
      <Link to={`/customers/${row.customerId}/statement`} className={buttonVariants({ size: 'sm', variant: 'ghost' })}>
        <FileText aria-hidden />
        对账单
      </Link>
      <Button size="sm" onClick={onPay}>
        <HandCoins aria-hidden />
        收款
      </Button>
    </div>
  )
}

function Stat({ label, value, sub, tone }: { label: string; value?: string; sub?: string; tone?: 'red' }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-sm text-slate-500">{label}</p>
      {value === undefined ? (
        <div className="mt-2 h-7 w-24 animate-pulse rounded bg-slate-100" />
      ) : (
        <p className={cn('mt-1 text-xl font-semibold tabular-nums', tone === 'red' ? 'text-red-600' : 'text-slate-900')}>{value}</p>
      )}
      {sub && <p className="mt-0.5 text-xs text-slate-500">{sub}</p>}
    </div>
  )
}

/** 账龄分布：四段横条（宽度按金额比例），并列出金额。 */
function AgingCard({ aging }: { aging?: Aging }) {
  const money = useMoney()
  const parts = aging
    ? [
        { label: '0–30 天', value: Number(aging.days0To30), raw: aging.days0To30, color: 'bg-blue-500' },
        { label: '31–60 天', value: Number(aging.days31To60), raw: aging.days31To60, color: 'bg-amber-400' },
        { label: '61–90 天', value: Number(aging.days61To90), raw: aging.days61To90, color: 'bg-orange-500' },
        { label: '90 天以上', value: Number(aging.over90), raw: aging.over90, color: 'bg-red-600' },
      ]
    : []
  const total = parts.reduce((s, p) => s + p.value, 0)
  return (
    <div className="col-span-2 rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-1">
      <p className="text-sm text-slate-500">账龄分布</p>
      {!aging ? (
        <div className="mt-2 h-12 animate-pulse rounded bg-slate-100" />
      ) : (
        <>
          <div className="mt-2 flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-slate-100" aria-hidden>
            {total > 0 && parts.filter((p) => p.value > 0).map((p) => <div key={p.label} className={p.color} style={{ width: `${(p.value / total) * 100}%` }} />)}
          </div>
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs lg:grid-cols-1">
            {parts.map((p) => (
              <div key={p.label} className="flex items-center justify-between gap-2">
                <dt className="flex items-center gap-1.5 whitespace-nowrap text-slate-500">
                  <span className={cn('size-2 rounded-full', p.color)} aria-hidden />
                  {p.label}
                </dt>
                <dd className="tabular-nums text-slate-800">{money(p.raw)}</dd>
              </div>
            ))}
          </dl>
        </>
      )}
    </div>
  )
}
