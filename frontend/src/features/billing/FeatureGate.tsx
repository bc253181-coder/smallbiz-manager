import { Lock, Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, Outlet } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useAuth } from '@/features/auth/AuthProvider'
import { cn } from '@/lib/utils'
import { formatMoney } from '@/lib/format'
import { FEATURE_LABELS, useLifetimePrice, usePlan, type Feature } from './api'

import { tr } from '@/lib/i18n'
const FEATURE_DESCRIPTIONS: Partial<Record<Feature, string>> = {
  CUSTOMERS: tr('记录客户资料和默认价格，开单时自动带出，查看每位客户的消费记录和欠款。'),
  SUPPLIERS: tr('管理进货渠道，记录每个供应商的采购历史和进货价格。'),
  PURCHASING: tr('下采购单、到货入库，库存和进货成本自动更新。'),
  RECEIVABLES: tr('客户可以先拿货后付款：记录赊账和收款，查看谁欠多少、欠了多久，打印对账单给客户核对。'),
}

/** 套餐未包含的页面：显示升级说明代替页面内容（数据不会被删除）。 */
export function UpgradePrompt({ feature, className }: { feature: Feature; className?: string }) {
  const { session } = useAuth()
  const isOwner = session?.currentTenant?.role === 'OWNER'
  const price = useLifetimePrice()
  return (
    <Card className={cn('mx-auto max-w-lg', className)}>
      <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
        <span className="flex size-12 items-center justify-center rounded-full bg-amber-50 text-amber-600">
          <Lock className="size-6" aria-hidden />
        </span>
        {/* 代替整个页面显示，因此作为页面主标题 */}
        <h1 className="text-lg font-semibold text-slate-900">
          {tr('「{{name}}」是高级会员功能', { name: FEATURE_LABELS[feature] })}
        </h1>
        {FEATURE_DESCRIPTIONS[feature] && <p className="text-sm text-slate-600">{FEATURE_DESCRIPTIONS[feature]}</p>}
        <p className="text-sm text-slate-500">
          {price
            ? tr('高级永久会员 {{price}}，一次性付款，永久解锁全部功能，无需自动续费。', { price: formatMoney(price.amount, price.currency) })
            : tr('升级高级永久会员，一次性付款，永久解锁全部功能。')}
          {tr('已有数据会保留。')}
        </p>        {isOwner ? (
          <Link to="/settings/billing?redeem=1" className={cn(buttonVariants(), 'mt-2')}>
            <Sparkles aria-hidden />
            {tr('立即升级')}
          </Link>
        ) : (
          <p className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">{tr('请联系店主升级高级永久会员')}</p>
        )}
      </div>
    </Card>
  )
}

/** 路由守卫：功能未开通 → 显示升级说明。 */
export function RequireFeature({ feature, children }: { feature: Feature; children?: ReactNode }) {
  const { data, isLoading } = usePlan()
  // 等套餐信息加载完再渲染，避免页面先发出会被拒绝的请求
  if (isLoading) return <div className="h-64 animate-pulse rounded-xl bg-slate-100" aria-label={tr('加载中')} />
  if (data && !data.features[feature]) return <UpgradePrompt feature={feature} className="mt-10" />
  return children ?? <Outlet />
}
