import { useQuery } from '@tanstack/react-query'
import { ChartColumn, CheckCircle2, Circle, Package, ReceiptText } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { tenantApi } from '@/features/auth/api'
import { productApi } from '@/features/catalog/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { BUSINESS_TYPE_LABELS, PLAN_LABELS, ROLE_LABELS } from '@/features/auth/types'
import { formatDate } from '@/lib/utils'

function greeting(): string {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

export function DashboardPage() {
  const { session } = useAuth()
  const tenant = session?.currentTenant
  const { data: detail } = useQuery({ queryKey: ['tenant'], queryFn: tenantApi.current })
  const { data: allProducts } = useQuery({
    queryKey: ['products', 'dashboard-count'],
    queryFn: () => productApi.list({ size: 1 }),
  })
  const { data: lowStockProducts } = useQuery({
    queryKey: ['products', 'dashboard-low'],
    queryFn: () => productApi.list({ size: 1, lowStock: true, status: 'ACTIVE' }),
  })
  if (!session || !tenant) return null

  const profileDone = !!(detail?.contactPhone || detail?.address)
  const productCount = allProducts?.totalItems
  const lowCount = lowStockProducts?.totalItems

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">
          {greeting()}，{session.user.displayName}
        </h1>
        <p className="mt-1 text-sm text-slate-500">这里是「{tenant.name}」的经营概览</p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="商品总数" value={productCount} to="/products" />
        <StatCard
          label="低库存商品"
          value={lowCount}
          to="/products?lowStock=true&status=ACTIVE"
          tone={lowCount ? 'amber' : undefined}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader title="开始使用" description="完成以下步骤，开始用 SmallBiz Manager 管理生意" />
          <CardBody className="p-0">
            <ol className="divide-y divide-slate-100">
              <Step done title="创建店铺" description={`「${tenant.name}」已创建`} />
              <Step
                done={profileDone}
                title="完善店铺资料"
                description="填写联系电话和地址，以后会显示在打印的单据上"
                action={
                  <Link to="/settings" className="text-sm font-medium text-blue-600 hover:underline">
                    {profileDone ? '查看' : '去填写'}
                  </Link>
                }
              />
              <Step
                done={(productCount ?? 0) > 0}
                title="添加第一个商品"
                description="录入商品名称、SKU、价格和库存"
                action={
                  <Link to={(productCount ?? 0) > 0 ? '/products' : '/products/new'} className="text-sm font-medium text-blue-600 hover:underline">
                    {(productCount ?? 0) > 0 ? '查看' : '去添加'}
                  </Link>
                }
              />
              <Step title="记录第一笔销售" description="开单后自动扣减库存、统计销售额" comingSoon />
            </ol>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="店铺信息" />
          <CardBody className="space-y-3 text-sm">
            <InfoRow label="店铺名称" value={tenant.name} />
            <InfoRow label="经营类型" value={BUSINESS_TYPE_LABELS[tenant.businessType]} />
            <InfoRow label="我的角色" value={ROLE_LABELS[tenant.role]} />
            <InfoRow
              label="当前套餐"
              value={
                <span className="inline-flex items-center gap-2">
                  <Badge tone={tenant.plan === 'FREE' ? 'slate' : 'blue'}>{PLAN_LABELS[tenant.plan]}</Badge>
                  {tenant.planExpiresAt && tenant.plan !== 'FREE' && (
                    <span className="text-xs text-slate-500">至 {formatDate(tenant.planExpiresAt)}</span>
                  )}
                </span>
              }
            />
            <InfoRow label="时区" value={tenant.timezone} />
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardBody className="flex flex-col items-center gap-3 py-12 text-center">
          <div className="flex gap-3 text-slate-300">
            <Package className="size-8" aria-hidden />
            <ReceiptText className="size-8" aria-hidden />
            <ChartColumn className="size-8" aria-hidden />
          </div>
          <h2 className="text-base font-semibold text-slate-900">还没有经营数据</h2>
          <p className="max-w-md text-sm text-slate-500">
            录入商品、记录销售和采购后，这里会显示今日销售额、本月销售额、库存价值、低库存提醒和销售趋势图。
          </p>
        </CardBody>
      </Card>
    </div>
  )
}

function Step({
  title,
  description,
  done,
  comingSoon,
  action,
}: {
  title: string
  description: string
  done?: boolean
  comingSoon?: boolean
  action?: ReactNode
}) {
  return (
    <li className="flex items-center gap-3 px-5 py-4">
      {done ? (
        <CheckCircle2 className="size-5 shrink-0 text-emerald-500" aria-label="已完成" />
      ) : (
        <Circle className="size-5 shrink-0 text-slate-300" aria-label="未完成" />
      )}
      <div className="min-w-0 flex-1">
        <p className={done ? 'text-sm font-medium text-slate-500 line-through' : 'text-sm font-medium text-slate-900'}>
          {title}
        </p>
        <p className="text-xs text-slate-500">{description}</p>
      </div>
      {comingSoon ? <Badge>即将上线</Badge> : action}
    </li>
  )
}

function StatCard({ label, value, to, tone }: { label: string; value: number | undefined; to: string; tone?: 'amber' }) {
  return (
    <Link to={to} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-colors hover:border-blue-200">
      <p className="text-sm text-slate-500">{label}</p>
      {value === undefined ? (
        <div className="mt-2 h-8 w-16 animate-pulse rounded bg-slate-100" />
      ) : (
        <p className={tone === 'amber' ? 'mt-1 text-2xl font-semibold text-amber-700' : 'mt-1 text-2xl font-semibold text-slate-900'}>
          {value.toLocaleString('zh-CN')}
        </p>
      )}
    </Link>
  )
}

function InfoRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="shrink-0 whitespace-nowrap text-slate-500">{label}</span>
      <span className="min-w-0 break-words text-right font-medium text-slate-900">{value}</span>
    </div>
  )
}
