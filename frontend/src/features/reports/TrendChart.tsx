import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useMoney } from '@/lib/format'

import { tr } from '@/lib/i18n'
export interface TrendDatum {
  period: string
  amount: string
  orderCount?: number
}

const compact = new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 })

/** 横轴标签：日 → 11/09；月 → 2026-11 */
function shortLabel(period: string) {
  return period.length === 10 ? `${period.slice(5, 7)}/${period.slice(8)}` : period
}

/**
 * 单序列柱状图（金额随时间）。一种颜色，无需图例（标题说明含义）；
 * 悬停显示精确金额和订单数；横向网格线淡化。
 */
export function TrendChart({
  data,
  label,
  height = 240,
  countLabel = tr('订单'),
}: {
  data: TrendDatum[]
  label: string
  height?: number
  countLabel?: string
}) {
  const money = useMoney()
  const rows = data.map((d) => ({ ...d, value: Number(d.amount) }))
  const total = rows.reduce((s, r) => s + r.value, 0)

  return (
    <div role="img" aria-label={tr('{{label}}：共 {{length}} 个时间段，合计 {{v2}}', { label: label, length: rows.length, v2: money(total.toFixed(2)) })} style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap={2}>
          <CartesianGrid vertical={false} stroke="#e2e8f0" strokeDasharray="0" />
          <XAxis
            dataKey="period"
            tickFormatter={shortLabel}
            tick={{ fontSize: 11, fill: '#64748b' }}
            tickLine={false}
            axisLine={{ stroke: '#cbd5e1' }}
            interval="preserveStartEnd"
            minTickGap={16}
          />
          <YAxis
            tickFormatter={(v: number) => compact.format(v)}
            tick={{ fontSize: 11, fill: '#64748b' }}
            tickLine={false}
            axisLine={false}
            width={44}
            allowDecimals={false}
          />
          <Tooltip
            cursor={{ fill: '#eff6ff' }}
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              const d = payload[0].payload as TrendDatum & { value: number }
              return (
                <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
                  <p className="font-medium text-slate-900">{d.period}</p>
                  <p className="mt-0.5 text-slate-700">
                    {label}：<span className="font-semibold tabular-nums">{money(d.amount)}</span>
                  </p>
                  {d.orderCount !== undefined && (
                    <p className="text-slate-500">
                      {countLabel} {d.orderCount} {tr('笔')}
                    </p>
                  )}
                </div>
              )
            }}
          />
          <Bar dataKey="value" fill="#2563eb" radius={[4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
