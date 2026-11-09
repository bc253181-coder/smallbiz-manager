import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Crown, KeyRound, Lock } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { useAuth } from '@/features/auth/AuthProvider'
import { PLAN_LABELS } from '@/features/auth/types'
import { errorMessage, refreshSession } from '@/lib/api'
import { formatDateTime, formatMoney } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  billingApi,
  FEATURE_LABELS,
  membershipLabel,
  orderTitle,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONES,
  SOURCE_LABELS,
  useLifetimePrice,
  usePlan,
  type CurrentPlan,
  type Feature,
} from '../api'

import { tr } from '@/lib/i18n'
export function BillingPage() {
  const { session } = useAuth()
  const role = session?.currentTenant?.role
  const isOwner = role === 'OWNER'
  const plan = usePlan()

  return (
    <div className="space-y-6">
      <PageHeader
        title={tr('会员')}
        description={tr('普通会员免费使用；高级永久会员一次性购买，解锁全部功能。已有数据不会因为会员等级被删除或锁定')}
        back={{ to: '/settings', label: tr('设置') }}
      />
      {plan.isLoading ? (
        <div className="h-48 animate-pulse rounded-xl bg-slate-100" />
      ) : plan.isError || !plan.data ? (
        <Card>
          <ErrorState onRetry={() => void plan.refetch()} />
        </Card>
      ) : (
        <>
          <MembershipCard data={plan.data} />
          {plan.data.membership.membership === 'LIFETIME' ? (
            <LifetimeThanks data={plan.data} />
          ) : isOwner ? (
            <RedeemCard />
          ) : (
            <Card>
              <CardBody className="text-sm text-slate-600">{tr('店铺的会员等级由店主决定。如需升级高级永久会员，请联系店主。')}</CardBody>
            </Card>
          )}
          {isOwner && <PaymentsCard />}
          {(role === 'OWNER' || role === 'ADMIN') && <HistoryCard />}
        </>
      )}
    </div>
  )
}
// ---------------- 当前会员与用量 ----------------

function daysLeft(iso: string) {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000))
}

function UsageBar({ label, used, max }: { label: string; used: number; max: number | null }) {
  const pct = max ? Math.min(100, (used / max) * 100) : 0
  return (
    <div>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
        {used}
        <span className="text-base font-normal text-slate-500"> / {max ?? tr('不限')}</span>
      </p>
      {max !== null && (
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-valuenow={used} aria-valuemax={max} aria-label={label}>
          <div className={cn('h-full rounded-full', pct >= 100 ? 'bg-red-500' : pct >= 66 ? 'bg-amber-500' : 'bg-blue-600')} style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  )
}

function MembershipCard({ data }: { data: CurrentPlan }) {
  const m = data.membership
  const legacy = data.plan !== 'FREE' && !!data.expiresAt // 以前的标准版 / 专业版订阅
  const left = data.expiresAt ? daysLeft(data.expiresAt) : null
  return (
    <Card>
      <CardBody className="grid gap-6 md:grid-cols-3 [&>*]:min-w-0">
        <div>
          <p className="text-sm text-slate-500">{tr('当前会员')}</p>
          <div className="mt-1 flex items-center gap-2">
            {data.lifetime && <Crown className="size-5 text-amber-500" aria-hidden />}
            <span className="text-2xl font-semibold text-slate-900" data-testid="membership-level">
              {membershipLabel(data.plan, data.expiresAt)}
            </span>
            {legacy && left !== null && left <= 7 && <Badge tone="amber">{tr('即将到期')}</Badge>}
          </div>
          <p className="mt-1 text-sm text-slate-600">
            {data.lifetime
              ? tr('永久有效')
              : legacy && data.expiresAt
                ? tr('以前购买的订阅，有效期至 {{v0}}（剩 {{v1}} 天），到期后回到普通会员', { v0: formatDateTime(data.expiresAt), v1: left ?? 0 })
                : tr('免费使用，按次数限制')}
          </p>
        </div>
        <div className="grid gap-4">
          <UsageBar label={tr('已创建店铺')} used={m.storesUsed} max={m.storesMax} />
          <UsageBar label={tr('已新建商品')} used={m.productsUsed} max={m.productsMax} />
          {m.productsMax !== null && <p className="text-xs text-slate-500">{tr('次数按账号累计，删除商品或店铺不返还')}</p>}
        </div>
        <ul className="space-y-1.5 text-sm">
          {(Object.keys(FEATURE_LABELS) as Feature[]).map((f) => (
            <li key={f} className={cn('flex items-center gap-2', data.features[f] ? 'text-slate-700' : 'text-slate-400')}>
              {data.features[f] ? <Check className="size-4 text-emerald-600" aria-label={tr('已开通')} /> : <Lock className="size-4" aria-label={tr('未开通')} />}
              {FEATURE_LABELS[f]}
            </li>
          ))}
          {m.reportDays !== null && (
            <li className="flex items-center gap-2 text-slate-500">
              <Lock className="size-4" aria-hidden />
              {tr('报表只能查看最近 {{days}} 天', { days: m.reportDays })}
            </li>
          )}
        </ul>
      </CardBody>
    </Card>
  )
}

function LifetimeThanks({ data }: { data: CurrentPlan }) {
  return (
    <Card>
      <CardBody className="flex gap-4">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-600">
          <Crown className="size-5" aria-hidden />
        </span>
        <div className="text-sm text-slate-600">
          <h2 className="text-base font-semibold text-slate-900">{tr('你是高级永久会员')}</h2>
          <p className="mt-1">{tr('全部功能已解锁，你名下所有店铺（包括以后新开的）都是高级会员，永久有效，不会自动扣费。')}</p>
          {data.membership.lifetimeSince && (
            <p className="mt-1 text-xs text-slate-500">{tr('开通时间：{{v0}}', { v0: formatDateTime(data.membership.lifetimeSince) })}</p>
          )}
        </div>
      </CardBody>
    </Card>
  )
}

// ---------------- 激活码开通高级永久会员 ----------------

/** 开通高级永久会员：只用激活码（在小红书购买），兑换正确立即开通。 */
function RedeemCard() {
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const plan = usePlan()
  const price = useLifetimePrice()
  const free = plan.data?.membership.freeLimits
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  const redeem = useMutation({
    mutationFn: () => billingApi.redeem(code),
    onSuccess: async (current) => {
      setCode('')
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['billing'] })
      await refreshSession()
      toast.success(
        current.lifetime
          ? tr('已开通高级永久会员')
          : tr('已开通{{v0}}，有效期至 {{expiresAt}}', { v0: PLAN_LABELS[current.plan], expiresAt: formatDateTime(current.expiresAt) }),
      )
    },
    onError: (e) => setError(errorMessage(e, tr('兑换失败'))),
  })

  return (
    <Card>
      <CardHeader title={tr('开通高级永久会员')} description={tr('一次性购买，永久享有高级会员权益，无需自动续费')} />
      <CardBody className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <div className="space-y-4">
          {/* 两种会员对比（数字来自平台配置） */}
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-xl border border-slate-200 p-4 text-sm">
              <p className="font-semibold text-slate-900">{tr('普通会员')}</p>
              <p className="text-xs text-slate-500">{tr('免费')}</p>
              {free && (
                <ul className="mt-3 space-y-1 text-slate-600">
                  <li>{tr('最多开 {{n}} 家店', { n: free.maxStores })}</li>
                  <li>{tr('最多新建 {{n}} 个商品', { n: free.maxProducts })}</li>
                  <li>{tr('开单、库存管理、扫码')}</li>
                  <li>{tr('报表最近 {{n}} 天', { n: free.reportDays })}</li>
                  {!free.advancedTools && <li className="text-slate-400">{tr('不含高级工具（客户、供应商、采购、赊账、员工、多仓库等）')}</li>}
                  {!free.export && <li className="text-slate-400">{tr('不能导出')}</li>}
                </ul>
              )}
            </div>
            <div className="rounded-xl border-2 border-amber-400 bg-amber-50/40 p-4 text-sm">
              <p className="flex items-center gap-1.5 font-semibold text-slate-900">
                <Crown className="size-4 text-amber-500" aria-hidden />
                {tr('高级永久会员')}
              </p>
              <p className="text-2xl font-semibold tabular-nums text-slate-900" data-testid="lifetime-price">
                {price ? formatMoney(price.amount, price.currency) : '—'}
              </p>
              <ul className="mt-2 space-y-1 text-slate-700">
                <li>{tr('店铺、商品不限量（合理使用）')}</li>
                <li>{tr('全部高级工具和模板')}</li>
                <li>{tr('完整报表、高级数据分析、导出')}</li>
                <li>{tr('名下所有店铺都有效，包括以后新开的')}</li>
                <li>{tr('以后新增的高级功能')}</li>
              </ul>
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <ol className="space-y-2 text-sm text-slate-600">
            <li className="flex gap-2">
              <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-700">1</span>
              {tr('在小红书购买「SmallBiz 高级永久会员」激活码')}
            </li>
            <li className="flex gap-2">
              <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-700">2</span>
              {tr('收到激活码后，在下面输入并点「兑换并开通」')}
            </li>
            <li className="flex gap-2">
              <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-semibold text-blue-700">3</span>
              {tr('激活码正确即刻开通，你名下所有店铺都成为高级会员')}
            </li>
          </ol>
          <form
            className="space-y-3"
            noValidate
            onSubmit={(e) => {
              e.preventDefault()
              if (!code.trim()) {
                setError(tr('请输入激活码'))
                return
              }
              redeem.mutate()
            }}
          >
            <Field label={tr('激活码')} htmlFor="redeem-code" error={error ?? undefined} hint={tr('格式如 SBZ-XXXX-XXXX-XXXX，不区分大小写')}>
              <Input
                id="redeem-code"
                value={code}
                autoComplete="off"
                spellCheck={false}
                autoFocus={params.has('redeem')}
                placeholder="SBZ-"
                className="font-mono uppercase"
                invalid={!!error}
                onChange={(e) => {
                  setCode(e.target.value)
                  setError(null)
                }}
              />
            </Field>
            <Button type="submit" size="lg" className="w-full" loading={redeem.isPending}>
              <KeyRound aria-hidden />
              {tr('兑换并开通')}
            </Button>
          </form>
          <p className="text-center text-xs text-slate-500">{tr('一次性购买，永久享有高级会员权益，无需自动续费。每个激活码只能使用一次。')}</p>
        </div>
      </CardBody>
    </Card>
  )
}
// ---------------- 付款记录 ----------------

function PaymentsCard() {
  const { data, isLoading } = useQuery({ queryKey: ['billing', 'payments'], queryFn: billingApi.payments })
  // 以前的付款记录（现在只用激活码开通）：没有记录时不显示
  if (!isLoading && !data?.length) return null
  return (
    <Card>
      <CardHeader title={tr('付款记录')} />
      {isLoading ? (
        <CardBody>
          <div className="h-16 animate-pulse rounded-lg bg-slate-100" />
        </CardBody>
      ) : !data?.length ? null : (
        <ul className="divide-y divide-slate-100">
          {data.slice(0, 10).map((o) => (
            <li key={o.id}>
              <Link to={`/settings/billing/pay/${o.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-800">
                    {orderTitle(o)} · {formatMoney(o.amount, o.currency)}
                  </p>
                  <p className="text-xs text-slate-500">
                    {formatDateTime(o.createdAt)} · {o.channel ? tr('在线支付') : (o.method?.displayName ?? tr('扫码付款'))}
                  </p>
                </div>
                <Badge tone={PAYMENT_STATUS_TONES[o.status]}>{PAYMENT_STATUS_LABELS[o.status]}</Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function HistoryCard() {
  const { data, isLoading } = useQuery({ queryKey: ['billing', 'history'], queryFn: billingApi.history })
  return (
    <Card>
      <CardHeader title={tr('会员变更记录')} />
      {isLoading ? (
        <CardBody>
          <div className="h-16 animate-pulse rounded-lg bg-slate-100" />
        </CardBody>
      ) : !data?.length ? (
        <CardBody className="text-sm text-slate-500">{tr('还没有变更记录')}</CardBody>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-5 py-2">{tr('时间')}</th>
                <th className="px-5 py-2">{tr('方式')}</th>
                <th className="px-5 py-2">{tr('变更')}</th>
                <th className="px-5 py-2">{tr('有效期至')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((l) => (
                <tr key={l.id}>
                  <td className="px-5 py-2.5 whitespace-nowrap text-slate-600">{formatDateTime(l.createdAt)}</td>
                  <td className="px-5 py-2.5">{SOURCE_LABELS[l.source]}</td>
                  <td className="px-5 py-2.5">
                    {membershipLabel(l.oldPlan, l.oldExpiresAt)} → {membershipLabel(l.newPlan, l.newExpiresAt)}
                  </td>
                  <td className="px-5 py-2.5 whitespace-nowrap text-slate-600">{l.newPlan !== 'FREE' && !l.newExpiresAt ? tr('永久') : formatDateTime(l.newExpiresAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
