import { useQuery } from '@tanstack/react-query'
import { ArrowRight, CheckCircle2, ChevronRight, Circle, HandCoins, Lock, PackageCheck, Plus } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { tenantApi } from '@/features/auth/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { ProductThumb } from '@/features/catalog/components/ProductThumb'
import { inventoryApi } from '@/features/inventory/api'
import { reportApi } from '@/features/reports/api'
import { TrendChart } from '@/features/reports/TrendChart'
import { saleApi } from '@/features/sales/api'
import { SaleStatusBadge } from '@/features/sales/SaleStatusBadge'
import { useFeature } from '@/features/billing/api'
import { receivableApi } from '@/features/receivables/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { cn } from '@/lib/utils'

import { tr } from '@/lib/i18n'
function greeting(): string {
  const h = new Date().getHours()
  if (h < 6) return tr('夜深了')
  if (h < 12) return tr('早上好')
  if (h < 14) return tr('中午好')
  if (h < 18) return tr('下午好')
  return tr('晚上好')
}

export function DashboardPage() {
  const { session } = useAuth()
  const money = useMoney()
  const canManage = useCanManage()
  const tenant = session?.currentTenant

  const summary = useQuery({ queryKey: ['sales', 'dashboard'], queryFn: reportApi.dashboard })
  // 销售趋势属于高级数据分析：普通会员默认不加载，显示升级提示
  const analytics = useFeature('FULL_REPORTS')
  const trend = useQuery({ queryKey: ['sales', 'trend', 30], queryFn: () => reportApi.trend(30), enabled: analytics })
  const inventory = useQuery({ queryKey: ['inventory', 'summary'], queryFn: inventoryApi.summary })
  const lowStock = useQuery({ queryKey: ['inventory', 'low-stock', 5], queryFn: () => inventoryApi.lowStock(5) })
  const recent = useQuery({ queryKey: ['sales', { recent: true }], queryFn: () => saleApi.list({ size: 5 }) })
  const detail = useQuery({ queryKey: ['tenant'], queryFn: tenantApi.current })
  const canCredit = useFeature('RECEIVABLES')
  const receivables = useQuery({ queryKey: ['receivables', 'summary'], queryFn: receivableApi.summary, enabled: canManage && canCredit })
  if (!session || !tenant) return null

  const s = summary.data
  const inv = inventory.data
  const hasSales = (recent.data?.totalItems ?? 0) > 0
  const hasProducts = (inv?.productCount ?? 0) > 0
  const profileDone = !!(detail.data?.contactPhone || detail.data?.address)
  const showGuide = recent.isSuccess && inventory.isSuccess && detail.isSuccess && !(hasSales && hasProducts && profileDone)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">
            {greeting()}，{session.user.displayName}
          </h1>
          <p className="mt-1 text-sm text-slate-500">「{tenant.name}{tr('」经营概览')}{s ? ` · ${s.today.replaceAll('-', '/')}` : ''}</p>
        </div>
        <Link to="/sales/new" className={cn(buttonVariants(), 'hidden sm:inline-flex')}>
          <Plus aria-hidden />
          {tr('开单')}
        </Link>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3 xl:grid-cols-6">
        <Stat label={tr('今日销售额')} value={s ? money(s.todaySales) : undefined} sub={s ? tr('{{todayOrders}} 笔订单', { todayOrders: s.todayOrders }) : undefined} to="/sales" />
        <Stat label={tr('本月销售额')} value={s ? money(s.monthSales) : undefined} sub={s ? tr('{{monthOrders}} 笔订单', { monthOrders: s.monthOrders }) : undefined} to="/sales" />
        {canManage ? (
          <>
            <Stat label={tr('本月毛利')} value={s?.monthGrossProfit != null ? money(s.monthGrossProfit) : undefined} sub={tr('销售额 − 成本')} to="/reports" />
            <Stat label={tr('本月采购')} value={s?.monthPurchases != null ? money(s.monthPurchases) : undefined} sub={tr('已收货的采购')} to="/purchases" />
            <Stat label={tr('库存价值')} value={inv?.inventoryValue != null ? money(inv.inventoryValue) : undefined} sub={inv ? tr('{{productCount}} 种商品', { productCount: inv.productCount }) : undefined} to="/inventory" />
          </>
        ) : (
          <Stat label={tr('商品数')} value={inv ? String(inv.productCount) : undefined} sub={inv ? tr('库存 {{totalQty}} 件', { totalQty: inv.totalQty }) : undefined} to="/products" />
        )}
        <Stat
          label={tr('低库存商品')}
          value={inv ? String(inv.lowStockCount) : undefined}
          sub={inv ? (inv.outOfStockCount ? tr('其中缺货 {{outOfStockCount}}', { outOfStockCount: inv.outOfStockCount }) : inv.lowStockCount ? tr('需要补货') : tr('库存充足')) : undefined}
          to="/inventory?lowStock=true"
          tone={inv?.lowStockCount ? 'amber' : undefined}
        />
      </div>

      {/* 以前开店时不能选币种：时区在马来西亚 / 新加坡但仍是人民币的店铺，提醒店主确认 */}
      {tenant.role === 'OWNER' &&
        detail.data &&
        !detail.data.currencyConfirmed &&
        detail.data.currency === 'CNY' &&
        ['Asia/Kuala_Lumpur', 'Asia/Kuching', 'Asia/Singapore'].includes(detail.data.timezone) && (
          <Link
            to="/settings"
            className="flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 px-5 py-3 text-sm text-blue-900 hover:bg-blue-100"
          >
            <span className="flex-1">{tr('店铺币种目前是人民币（¥）。如果你用马币或新币收款，请到「设置 → 店铺资料」修改币种（只改显示符号，不换算金额）。')}</span>
            <ChevronRight className="size-4 shrink-0" aria-hidden />
          </Link>
        )}
      {receivables.data && Number(receivables.data.totalOutstanding) > 0 && (
        <Link
          to="/receivables"
          className="flex flex-wrap items-center gap-x-6 gap-y-1 rounded-xl border border-amber-200 bg-amber-50 px-5 py-3 text-sm text-amber-900 hover:bg-amber-100"
        >
          <span className="flex items-center gap-2 font-medium">
            <HandCoins className="size-4" aria-hidden />
            {tr('应收账款')} <span className="tabular-nums">{money(receivables.data.totalOutstanding)}</span>
          </span>
          <span className="text-amber-800">{receivables.data.customerCount} {tr('位客户欠款')}</span>
          {Number(receivables.data.overdue30) > 0 && (
            <span className="text-red-700">{tr('逾期 30 天以上')} {money(receivables.data.overdue30)}</span>
          )}
          <ChevronRight className="ml-auto size-4" aria-hidden />
        </Link>
      )}
      {showGuide && (
        <Card>
          <CardHeader title={tr('开始使用')} description={tr('完成以下步骤，开始用 SmallBiz Manager 管理生意')} />
          <ol className="grid divide-y divide-slate-100 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <Step done={profileDone} title={tr('完善店铺资料')} to="/settings" />
            <Step done={hasProducts} title={tr('添加第一个商品')} to="/products/new" />
            <Step done={hasSales} title={tr('记录第一笔销售')} to="/sales/new" />
          </ol>
        </Card>
      )}

      <div className="grid gap-6 xl:grid-cols-3 [&>*]:min-w-0">
        <Card className="xl:col-span-2">
          <CardHeader
            title={tr('近 30 天销售额')}
            description={trend.data ? tr('合计 {{amount}} · {{orderCount}} 笔订单', { amount: money(trend.data.total.amount), orderCount: trend.data.total.orderCount }) : undefined}
            action={
              canManage && (
                <Link to="/reports" className="inline-flex items-center gap-1 whitespace-nowrap text-sm font-medium text-blue-600 hover:underline">
                  {tr('查看报表')}
                  <ArrowRight className="size-4" aria-hidden />
                </Link>
              )
            }
          />
          <CardBody>
            {!analytics ? (
              <div className="flex h-60 flex-col items-center justify-center gap-2 rounded-lg bg-slate-50 text-center text-sm text-slate-500">
                <Lock className="size-5 text-slate-400" aria-hidden />
                {tr('销售趋势是高级会员功能')}
                <Link to="/settings/billing" className="font-medium text-blue-600 hover:underline">
                  {tr('立即升级')}
                </Link>
              </div>
            ) : trend.data ? (
              <TrendChart data={trend.data.points} label={tr('销售额')} />
            ) : (
              <div className="h-60 animate-pulse rounded-lg bg-slate-50" />
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader
            title={tr('需要补货')}
            action={
              <Link to="/inventory?lowStock=true" className="whitespace-nowrap text-sm font-medium text-blue-600 hover:underline">
                {tr('全部')}
              </Link>
            }
          />
          {lowStock.data && lowStock.data.items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-5 py-10 text-sm text-slate-500">
              <PackageCheck className="size-8 text-emerald-500" aria-hidden />
              {tr('库存都很充足')}
            </div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {lowStock.data?.items.map((p) => (
                <li key={p.id}>
                  <Link to={`/products/${p.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-slate-50">
                    <ProductThumb url={p.imageUrl} name={p.name} className="size-9" />
                    <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-900">{p.name}</span>
                    <span className={cn('whitespace-nowrap text-sm font-semibold tabular-nums', p.stockQty <= 0 ? 'text-red-600' : 'text-amber-700')}>
                      {p.stockQty <= 0 ? tr('缺货') : tr('剩 {{stockQty}} {{unit}}', { stockQty: p.stockQty, unit: p.unit })}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {canManage && (lowStock.data?.count ?? 0) > 0 && (
            <div className="border-t border-slate-100 px-5 py-3">
              <Link to="/purchases/new" className="text-sm font-medium text-blue-600 hover:underline">
                {tr('新建采购单补货 →')}
              </Link>
            </div>
          )}
        </Card>
      </div>

      <Card>
        <CardHeader
          title={tr('最近销售')}
          action={
            <Link to="/sales" className="whitespace-nowrap text-sm font-medium text-blue-600 hover:underline">
              {tr('全部')}
            </Link>
          }
        />
        {recent.data && recent.data.items.length === 0 ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">{tr('还没有销售记录')}</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {recent.data?.items.map((o) => (
              <li key={o.id}>
                <Link to={`/sales/${o.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-900">{o.customerName ?? tr('散客')}</p>
                    <p className="text-xs text-slate-500">
                      {o.orderNo} · {formatDateTime(o.orderDate)}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={cn('text-sm font-semibold tabular-nums', o.status === 'CANCELLED' && 'text-slate-400 line-through')}>
                      {money(o.totalAmount)}
                    </span>
                    <SaleStatusBadge status={o.status} paymentStatus={o.paymentStatus} />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

function Stat({ label, value, sub, to, tone }: { label: string; value?: string; sub?: string; to: string; tone?: 'amber' }) {
  return (
    <Link to={to} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:border-blue-200">
      <p className="text-sm text-slate-500">{label}</p>
      {value === undefined ? (
        <div className="mt-2 h-7 w-20 animate-pulse rounded bg-slate-100" />
      ) : (
        <p className={cn('mt-1 truncate text-xl font-semibold tabular-nums', tone === 'amber' ? 'text-amber-700' : 'text-slate-900')}>{value}</p>
      )}
      {sub && <p className="mt-0.5 truncate text-xs text-slate-500">{sub}</p>}
    </Link>
  )
}

function Step({ done, title, to }: { done: boolean; title: string; to: string }): ReactNode {
  return (
    <li>
      <Link to={to} className="flex items-center gap-3 px-5 py-4 hover:bg-slate-50">
        {done ? (
          <CheckCircle2 className="size-5 shrink-0 text-emerald-500" aria-label={tr('已完成')} />
        ) : (
          <Circle className="size-5 shrink-0 text-slate-300" aria-label={tr('未完成')} />
        )}
        <span className={done ? 'text-sm text-slate-500 line-through' : 'text-sm font-medium text-slate-900'}>{title}</span>
      </Link>
    </li>
  )
}
