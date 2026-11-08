import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorMessage } from '@/lib/api'
import { useMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import { downloadCsv, reportApi, type GroupBy } from '../api'
import { TrendChart } from '../TrendChart'

type Tab = 'sales' | 'products' | 'customers' | 'purchases' | 'inventory'
const TABS: { value: Tab; label: string }[] = [
  { value: 'sales', label: '销售' },
  { value: 'products', label: '商品排行' },
  { value: 'customers', label: '客户排行' },
  { value: 'purchases', label: '采购' },
  { value: 'inventory', label: '库存' },
]

/** 本地日期 → YYYY-MM-DD（避免 toISOString 的 UTC 偏移）。 */
function ymd(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function presets() {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth()
  const daysAgo = (n: number) => new Date(y, m, now.getDate() - n)
  return [
    { key: 'month', label: '本月', from: ymd(new Date(y, m, 1)), to: ymd(now) },
    { key: 'last-month', label: '上月', from: ymd(new Date(y, m - 1, 1)), to: ymd(new Date(y, m, 0)) },
    { key: '7d', label: '近 7 天', from: ymd(daysAgo(6)), to: ymd(now) },
    { key: '30d', label: '近 30 天', from: ymd(daysAgo(29)), to: ymd(now) },
    { key: 'year', label: '今年', from: ymd(new Date(y, 0, 1)), to: ymd(now) },
  ]
}

export function ReportsPage() {
  const [params, setParams] = useSearchParams()
  const all = presets()
  const tab = (params.get('tab') as Tab | null) ?? 'sales'
  const from = params.get('from') ?? all[0].from
  const to = params.get('to') ?? all[0].to
  const invalid = from > to

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
      <PageHeader title="经营报表" description="已取消的销售单和未收货的采购单不计入统计；日期按店铺时区计算" />

      <Card className="mb-6">
        <CardBody className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap gap-1.5" aria-label="快捷日期">
            {all.map((p) => (
              <button
                key={p.key}
                type="button"
                aria-pressed={from === p.from && to === p.to}
                onClick={() => update({ from: p.from, to: p.to })}
                className={cn(
                  'rounded-lg border px-3 py-1.5 text-sm',
                  from === p.from && to === p.to ? 'border-blue-600 bg-blue-50 font-medium text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50',
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Input type="date" aria-label="开始日期" value={from} max={to} onChange={(e) => e.target.value && update({ from: e.target.value })} className="w-40" />
            <span className="text-slate-400">至</span>
            <Input type="date" aria-label="结束日期" value={to} min={from} onChange={(e) => e.target.value && update({ to: e.target.value })} className="w-40" />
          </div>
        </CardBody>
      </Card>

      <div className="-mx-1 mb-4 flex gap-1 overflow-x-auto px-1" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            role="tab"
            aria-selected={tab === t.value}
            onClick={() => update({ tab: t.value === 'sales' ? null : t.value })}
            className={cn('whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium', tab === t.value ? 'bg-white text-blue-700 shadow-sm ring-1 ring-slate-200' : 'text-slate-600 hover:bg-white/60')}
          >
            {t.label}
          </button>
        ))}
      </div>

      {invalid ? (
        <Card>
          <p className="p-6 text-center text-sm text-red-600">开始日期不能晚于结束日期</p>
        </Card>
      ) : tab === 'sales' ? (
        <SalesTab from={from} to={to} />
      ) : tab === 'products' ? (
        <ProductsTab from={from} to={to} />
      ) : tab === 'customers' ? (
        <CustomersTab from={from} to={to} />
      ) : tab === 'purchases' ? (
        <PurchasesTab from={from} to={to} />
      ) : (
        <InventoryTab />
      )}
    </div>
  )
}

// ---------------- 通用 ----------------

function ExportButton({ path, params, name }: { path: string; params: Record<string, string>; name: string }) {
  const [busy, setBusy] = useState(false)
  return (
    <Button
      size="sm"
      variant="secondary"
      loading={busy}
      onClick={async () => {
        setBusy(true)
        try {
          await downloadCsv(path, params, name)
        } catch (e) {
          toast.error(errorMessage(e, '导出失败'))
        } finally {
          setBusy(false)
        }
      }}
    >
      <Download aria-hidden />
      导出 CSV
    </Button>
  )
}

function GroupToggle({ value, onChange }: { value: GroupBy; onChange: (g: GroupBy) => void }) {
  return (
    <div className="inline-flex rounded-lg border border-slate-200 p-0.5">
      {(['DAY', 'MONTH'] as GroupBy[]).map((g) => (
        <button
          key={g}
          type="button"
          aria-pressed={value === g}
          onClick={() => onChange(g)}
          className={cn('whitespace-nowrap rounded-md px-3 py-1 text-sm', value === g ? 'bg-blue-50 font-medium text-blue-700' : 'text-slate-600')}
        >
          {g === 'DAY' ? '按日' : '按月'}
        </button>
      ))}
    </div>
  )
}

function Kpi({ label, value, tone }: { label: string; value: string; tone?: 'green' | 'red' }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={cn('mt-1 text-xl font-semibold tabular-nums', tone === 'green' ? 'text-emerald-700' : tone === 'red' ? 'text-red-600' : 'text-slate-900')}>
        {value}
      </p>
    </div>
  )
}

function Table({ head, children, minWidth = 520 }: { head: ReactNode; children: ReactNode; minWidth?: number }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm" style={{ minWidth }}>
        <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">{head}</thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  )
}

function Loading() {
  return <div className="h-64 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
}

function percent(part: string | null, whole: string) {
  const w = Number(whole)
  if (!part || !w) return '—'
  return `${((Number(part) / w) * 100).toFixed(1)}%`
}

// ---------------- 销售 ----------------

function SalesTab({ from, to }: { from: string; to: string }) {
  const money = useMoney()
  const [groupBy, setGroupBy] = useState<GroupBy>('DAY')
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['sales', 'report', from, to, groupBy],
    queryFn: () => reportApi.sales({ from, to, groupBy }),
  })
  if (isLoading) return <Loading />
  if (isError || !data) return <Card><ErrorState onRetry={() => void refetch()} message={errorMessage(error)} /></Card>
  const t = data.total
  const rows = data.points.filter((p) => groupBy === 'MONTH' || p.orderCount > 0)

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi label="销售额" value={money(t.amount)} />
        <Kpi label="订单数" value={String(t.orderCount)} />
        <Kpi label="毛利" value={money(t.grossProfit)} tone={Number(t.grossProfit) < 0 ? 'red' : 'green'} />
        <Kpi label="毛利率" value={percent(t.grossProfit, t.amount)} />
      </div>
      <Card>
        <CardHeader
          title="销售额趋势"
          action={
            <div className="flex flex-wrap items-center gap-2">
              <GroupToggle value={groupBy} onChange={setGroupBy} />
              <ExportButton path="/reports/sales/export" params={{ from, to, groupBy }} name="销售报表.csv" />
            </div>
          }
        />
        <CardBody>
          <TrendChart data={data.points} label="销售额" height={260} />
        </CardBody>
        <Table
          head={
            <tr>
              <th className="px-4 py-3">{groupBy === 'DAY' ? '日期' : '月份'}</th>
              <th className="px-4 py-3 text-right">订单数</th>
              <th className="px-4 py-3 text-right">销售额</th>
              <th className="px-4 py-3 text-right">成本</th>
              <th className="px-4 py-3 text-right">毛利</th>
            </tr>
          }
        >
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-4 py-8 text-center text-slate-500">这段时间没有销售</td>
            </tr>
          ) : (
            [...rows].reverse().map((p) => (
              <tr key={p.period}>
                <td className="whitespace-nowrap px-4 py-2.5 text-slate-700">{p.period}</td>
                <td className="px-4 py-2.5 text-right tabular-nums">{p.orderCount}</td>
                <td className="px-4 py-2.5 text-right tabular-nums">{money(p.amount)}</td>
                <td className="px-4 py-2.5 text-right tabular-nums text-slate-600">{money(p.cost)}</td>
                <td className="px-4 py-2.5 text-right font-medium tabular-nums">{money(p.grossProfit)}</td>
              </tr>
            ))
          )}
        </Table>
        {groupBy === 'DAY' && rows.length < data.points.length && (
          <p className="border-t border-slate-100 px-4 py-2 text-xs text-slate-500">表格只列出有销售的日期；图表包含每一天。</p>
        )}
      </Card>
    </div>
  )
}

// ---------------- 商品排行 ----------------

function ProductsTab({ from, to }: { from: string; to: string }) {
  const money = useMoney()
  const [by, setBy] = useState<'amount' | 'qty'>('amount')
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['sales', 'report-products', from, to, by],
    queryFn: () => reportApi.topProducts({ from, to, by }),
  })
  if (isLoading) return <Loading />
  if (isError || !data) return <Card><ErrorState onRetry={() => void refetch()} message={errorMessage(error)} /></Card>
  const max = Math.max(...data.map((p) => (by === 'qty' ? p.quantity : Number(p.amount))), 1)

  return (
    <Card>
      <CardHeader
        title="商品销售排行"
        description="销售额为商品小计，未分摊整单优惠"
        action={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-lg border border-slate-200 p-0.5">
              {(['amount', 'qty'] as const).map((k) => (
                <button key={k} type="button" aria-pressed={by === k} onClick={() => setBy(k)} className={cn('whitespace-nowrap rounded-md px-3 py-1 text-sm', by === k ? 'bg-blue-50 font-medium text-blue-700' : 'text-slate-600')}>
                  {k === 'amount' ? '按销售额' : '按销量'}
                </button>
              ))}
            </div>
            <ExportButton path="/reports/top-products/export" params={{ from, to, by }} name="商品销售排行.csv" />
          </div>
        }
      />
      <Table
        minWidth={600}
        head={
          <tr>
            <th className="w-12 px-4 py-3">#</th>
            <th className="px-4 py-3">商品</th>
            <th className="px-4 py-3 text-right">销量</th>
            <th className="px-4 py-3 text-right">销售额</th>
            <th className="px-4 py-3 text-right">毛利</th>
          </tr>
        }
      >
        {data.length === 0 ? (
          <tr>
            <td colSpan={5} className="px-4 py-8 text-center text-slate-500">这段时间没有销售</td>
          </tr>
        ) : (
          data.map((p, i) => (
            <tr key={p.productId}>
              <td className="px-4 py-2.5 tabular-nums text-slate-500">{i + 1}</td>
              <td className="px-4 py-2.5">
                <Link to={`/products/${p.productId}`} className="font-medium text-slate-900 hover:text-blue-600">
                  {p.productName}
                </Link>
                {/* 行内比例条：一种颜色，长度对应当前排序指标 */}
                <div className="mt-1 h-1.5 rounded-full bg-slate-100">
                  <div className="h-1.5 rounded-full bg-blue-600" style={{ width: `${((by === 'qty' ? p.quantity : Number(p.amount)) / max) * 100}%` }} />
                </div>
              </td>
              <td className="whitespace-nowrap px-4 py-2.5 text-right tabular-nums">
                {p.quantity} {p.unit}
              </td>
              <td className="px-4 py-2.5 text-right tabular-nums">{money(p.amount)}</td>
              <td className="px-4 py-2.5 text-right font-medium tabular-nums">{money(p.grossProfit)}</td>
            </tr>
          ))
        )}
      </Table>
    </Card>
  )
}

// ---------------- 客户排行 ----------------

function CustomersTab({ from, to }: { from: string; to: string }) {
  const money = useMoney()
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['sales', 'report-customers', from, to],
    queryFn: () => reportApi.topCustomers({ from, to }),
  })
  if (isLoading) return <Loading />
  if (isError || !data) return <Card><ErrorState onRetry={() => void refetch()} message={errorMessage(error)} /></Card>
  return (
    <Card>
      <CardHeader
        title="客户消费排行"
        description="按实收金额排序，不含散客"
        action={<ExportButton path="/reports/top-customers/export" params={{ from, to }} name="客户消费排行.csv" />}
      />
      <Table
        head={
          <tr>
            <th className="w-12 px-4 py-3">#</th>
            <th className="px-4 py-3">客户</th>
            <th className="px-4 py-3 text-right">订单数</th>
            <th className="px-4 py-3 text-right">消费金额</th>
          </tr>
        }
      >
        {data.length === 0 ? (
          <tr>
            <td colSpan={4} className="px-4 py-8 text-center text-slate-500">这段时间没有记名客户的订单</td>
          </tr>
        ) : (
          data.map((c, i) => (
            <tr key={c.customerId}>
              <td className="px-4 py-2.5 tabular-nums text-slate-500">{i + 1}</td>
              <td className="px-4 py-2.5">
                <Link to={`/customers/${c.customerId}`} className="font-medium text-slate-900 hover:text-blue-600">
                  {c.customerName}
                </Link>
              </td>
              <td className="px-4 py-2.5 text-right tabular-nums">{c.orderCount}</td>
              <td className="px-4 py-2.5 text-right font-medium tabular-nums">{money(c.amount)}</td>
            </tr>
          ))
        )}
      </Table>
    </Card>
  )
}

// ---------------- 采购 ----------------

function PurchasesTab({ from, to }: { from: string; to: string }) {
  const money = useMoney()
  const [groupBy, setGroupBy] = useState<GroupBy>('DAY')
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['purchases', 'report', from, to, groupBy],
    queryFn: () => reportApi.purchases({ from, to, groupBy }),
  })
  if (isLoading) return <Loading />
  if (isError || !data) return <Card><ErrorState onRetry={() => void refetch()} message={errorMessage(error)} /></Card>
  const rows = data.points.filter((p) => groupBy === 'MONTH' || p.orderCount > 0)
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi label="采购金额" value={money(data.total.amount)} />
        <Kpi label="收货单数" value={String(data.total.orderCount)} />
      </div>
      <Card>
        <CardHeader title="采购金额趋势" description="按收货日期统计已收货的采购单" action={<GroupToggle value={groupBy} onChange={setGroupBy} />} />
        <CardBody>
          <TrendChart data={data.points} label="采购金额" countLabel="收货" height={240} />
        </CardBody>
        <Table
          head={
            <tr>
              <th className="px-4 py-3">{groupBy === 'DAY' ? '日期' : '月份'}</th>
              <th className="px-4 py-3 text-right">收货单数</th>
              <th className="px-4 py-3 text-right">采购金额</th>
            </tr>
          }
        >
          {rows.length === 0 ? (
            <tr>
              <td colSpan={3} className="px-4 py-8 text-center text-slate-500">这段时间没有收货</td>
            </tr>
          ) : (
            [...rows].reverse().map((p) => (
              <tr key={p.period}>
                <td className="px-4 py-2.5 text-slate-700">{p.period}</td>
                <td className="px-4 py-2.5 text-right tabular-nums">{p.orderCount}</td>
                <td className="px-4 py-2.5 text-right font-medium tabular-nums">{money(p.amount)}</td>
              </tr>
            ))
          )}
        </Table>
      </Card>
    </div>
  )
}

// ---------------- 库存 ----------------

function InventoryTab() {
  const money = useMoney()
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ['inventory', 'report'], queryFn: reportApi.inventory })
  if (isLoading) return <Loading />
  if (isError || !data) return <Card><ErrorState onRetry={() => void refetch()} message={errorMessage(error)} /></Card>
  const total = data.reduce((s, c) => s + Number(c.value), 0)
  return (
    <Card>
      <CardHeader title="按分类的库存" description="当前库存（不受日期范围影响），价值按进货成本计算" />
      <Table
        head={
          <tr>
            <th className="px-4 py-3">分类</th>
            <th className="px-4 py-3 text-right">商品数</th>
            <th className="px-4 py-3 text-right">库存数量</th>
            <th className="px-4 py-3 text-right">库存价值</th>
            <th className="px-4 py-3 text-right">占比</th>
          </tr>
        }
      >
        {data.length === 0 ? (
          <tr>
            <td colSpan={5} className="px-4 py-8 text-center text-slate-500">还没有商品</td>
          </tr>
        ) : (
          data.map((c) => (
            <tr key={c.categoryId ?? 'none'}>
              <td className="px-4 py-2.5 text-slate-700">{c.categoryName}</td>
              <td className="px-4 py-2.5 text-right tabular-nums">{c.productCount}</td>
              <td className="px-4 py-2.5 text-right tabular-nums">{c.quantity}</td>
              <td className="px-4 py-2.5 text-right font-medium tabular-nums">{money(c.value)}</td>
              <td className="px-4 py-2.5 text-right tabular-nums text-slate-600">{total ? `${((Number(c.value) / total) * 100).toFixed(1)}%` : '—'}</td>
            </tr>
          ))
        )}
      </Table>
    </Card>
  )
}
