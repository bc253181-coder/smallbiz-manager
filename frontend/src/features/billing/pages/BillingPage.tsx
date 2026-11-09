import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronRight, KeyRound, Lock, QrCode } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Input } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { useAuth } from '@/features/auth/AuthProvider'
import { PLAN_LABELS } from '@/features/auth/types'
import { errorCode, errorMessage, refreshSession } from '@/lib/api'
import { formatDateTime, formatMoney } from '@/lib/format'
import { fromCents, toCents } from '@/lib/money'
import { cn } from '@/lib/utils'
import {
  billingApi,
  FEATURE_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_STATUS_TONES,
  PERIOD_LABELS,
  SOURCE_LABELS,
  usePlan,
  type Feature,
  type PriceView,
} from '../api'

export function BillingPage() {
  const { session } = useAuth()
  const role = session?.currentTenant?.role
  const isOwner = role === 'OWNER'
  const plan = usePlan()

  return (
    <div className="space-y-6">
      <PageHeader title="套餐与续费" description="升级后立即生效；到期后自动回到免费版，已有数据不会删除" back={{ to: '/settings', label: '设置' }} />
      {plan.isLoading ? (
        <div className="h-48 animate-pulse rounded-xl bg-slate-100" />
      ) : plan.isError || !plan.data ? (
        <Card>
          <ErrorState onRetry={() => void plan.refetch()} />
        </Card>
      ) : (
        <>
          <CurrentPlanCard />
          {isOwner ? (
            <div className="grid gap-6 lg:grid-cols-5 [&>*]:min-w-0">
              <div className="space-y-6 lg:col-span-3">
                <BuyCard />
              </div>
              <div className="space-y-6 lg:col-span-2">
                <RedeemCard />
                <PaymentsCard />
              </div>
            </div>
          ) : (
            <Card>
              <CardBody className="text-sm text-slate-600">只有店主可以升级或续费套餐。如需开通标准版，请联系店主。</CardBody>
            </Card>
          )}
          {(role === 'OWNER' || role === 'ADMIN') && <HistoryCard />}
        </>
      )}
    </div>
  )
}

// ---------------- 当前套餐 ----------------

function daysLeft(iso: string) {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000))
}

function CurrentPlanCard() {
  const { data } = usePlan()
  if (!data) return null
  const paid = data.plan !== 'FREE'
  const left = data.expiresAt ? daysLeft(data.expiresAt) : null
  const usage = data.maxProducts ? Math.min(100, (data.productCount / data.maxProducts) * 100) : 0

  return (
    <Card>
      <CardBody className="grid gap-6 md:grid-cols-3 [&>*]:min-w-0">
        <div>
          <p className="text-sm text-slate-500">当前套餐</p>
          <div className="mt-1 flex items-center gap-2">
            <span className="text-2xl font-semibold text-slate-900">{PLAN_LABELS[data.plan]}</span>
            {paid && left !== null && left <= 7 && <Badge tone="amber">即将到期</Badge>}
          </div>
          <p className="mt-1 text-sm text-slate-600">
            {paid && data.expiresAt ? (
              <>
                有效期至 {formatDateTime(data.expiresAt)}（剩 {left} 天）
              </>
            ) : (
              '永久免费'
            )}
          </p>
        </div>
        <div>
          <p className="text-sm text-slate-500">商品数量</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">
            {data.productCount}
            <span className="text-base font-normal text-slate-500"> / {data.maxProducts ?? '不限'}</span>
          </p>
          {data.maxProducts && (
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-valuenow={data.productCount} aria-valuemax={data.maxProducts} aria-label="商品数量">
              <div className={cn('h-full rounded-full', usage >= 90 ? 'bg-amber-500' : 'bg-blue-600')} style={{ width: `${usage}%` }} />
            </div>
          )}
        </div>
        <ul className="space-y-1.5 text-sm">
          {(Object.keys(FEATURE_LABELS) as Feature[]).map((f) => (
            <li key={f} className={cn('flex items-center gap-2', data.features[f] ? 'text-slate-700' : 'text-slate-400')}>
              {data.features[f] ? <Check className="size-4 text-emerald-600" aria-label="已开通" /> : <Lock className="size-4" aria-label="未开通" />}
              {FEATURE_LABELS[f]}
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  )
}

// ---------------- 购买 ----------------

function priceTitle(p: Pick<PriceView, 'plan' | 'period'>) {
  return `${PLAN_LABELS[p.plan]} · ${PERIOD_LABELS[p.period]}`
}

function BuyCard() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const plan = usePlan()
  const [currency, setCurrency] = useState(session?.currentTenant?.currency === 'MYR' ? 'MYR' : 'CNY')
  const [priceId, setPriceId] = useState<number | null>(null)
  const [methodId, setMethodId] = useState<number | null>(null)
  const isPro = plan.data?.plan === 'PRO'
  const [planTab, setPlanTab] = useState<'STANDARD' | 'PRO'>(isPro ? 'PRO' : 'STANDARD')
  // 专业版有效期内只能续费专业版
  const tab = isPro ? 'PRO' : planTab

  const prices = useQuery({ queryKey: ['billing', 'prices', currency], queryFn: () => billingApi.prices(currency) })
  const methods = useQuery({ queryKey: ['billing', 'methods'], queryFn: billingApi.methods })
  const payments = useQuery({ queryKey: ['billing', 'payments'], queryFn: billingApi.payments })
  const open = payments.data?.find((p) => p.status === 'AWAITING_PAYMENT' || p.status === 'PENDING_REVIEW')

  const options = (prices.data ?? []).filter((p) => p.plan === tab && (p.period !== 'TRIAL_MONTH' || !plan.data?.trialUsed))
  const monthly = prices.data?.find((p) => p.plan === tab && p.period === 'MONTH')
  const hasPro = (prices.data ?? []).some((p) => p.plan === 'PRO')
  const bonusDays = plan.data?.proUpgradeBonusDays ?? 0
  const currencyMethods = (methods.data ?? []).filter((m) => m.currency === currency)
  const selected = options.find((p) => p.id === priceId) ?? options[0]
  const method = currencyMethods.find((m) => m.id === methodId) ?? currencyMethods[0]

  const create = useMutation({
    mutationFn: () => billingApi.createPayment(selected!.id, method!.id),
    onSuccess: (order) => {
      void queryClient.invalidateQueries({ queryKey: ['billing', 'payments'] })
      navigate(`/settings/billing/pay/${order.id}`)
    },
    onError: (e) => {
      toast.error(errorMessage(e, '创建付款单失败'))
      if (errorCode(e) === 'PAYMENT_IN_PROGRESS') void payments.refetch()
    },
  })

  return (
    <Card>
      <CardHeader
        title="升级 / 续费"
        description="扫码付款后上传截图，人工核对后开通（通常几小时内）"
        action={
          <div className="inline-flex shrink-0 self-start rounded-lg border border-slate-200 p-0.5" role="radiogroup" aria-label="币种">
            {[
              { v: 'CNY', label: '人民币' },
              { v: 'MYR', label: '马币 RM' },
            ].map((c) => (
              <button
                key={c.v}
                type="button"
                role="radio"
                aria-checked={currency === c.v}
                onClick={() => {
                  setCurrency(c.v)
                  setPriceId(null)
                  setMethodId(null)
                }}
                className={cn('rounded-md px-3 py-1 text-sm', currency === c.v ? 'bg-blue-50 font-medium text-blue-700' : 'text-slate-600')}
              >
                {c.label}
              </button>
            ))}
          </div>
        }
      />
      <CardBody className="space-y-5">
        {open && (
          <Link
            to={`/settings/billing/pay/${open.id}`}
            className="flex items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800 hover:bg-blue-100"
          >
            <span>
              你有一张{PAYMENT_STATUS_LABELS[open.status]}的付款单（{priceTitle(open)}{' '}
              {formatMoney(open.amount, open.currency)}）
            </span>
            <ChevronRight className="size-4 shrink-0" aria-hidden />
          </Link>
        )}

        {hasPro && (
          <fieldset>
            <legend className="mb-2 text-sm font-medium text-slate-700">选择套餐</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {(['STANDARD', 'PRO'] as const).map((p) => {
                const disabled = p === 'STANDARD' && isPro
                return (
                  <button
                    key={p}
                    type="button"
                    aria-pressed={tab === p}
                    disabled={disabled}
                    onClick={() => {
                      setPlanTab(p)
                      setPriceId(null)
                    }}
                    className={cn(
                      'rounded-xl border p-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50',
                      tab === p ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600' : 'border-slate-200 hover:bg-slate-50',
                    )}
                  >
                    <span className="block font-semibold text-slate-900">{PLAN_LABELS[p]}</span>
                    <span className="block text-xs text-slate-500">
                      {p === 'STANDARD' ? '客户、供应商、采购、完整报表、导出' : '标准版全部 + 应收账款（赊账、收款、对账单）'}
                    </span>
                  </button>
                )
              })}
            </div>
            {isPro && <p className="mt-2 text-xs text-slate-500">你当前是专业版，有效期内只能续费专业版。</p>}
            {tab === 'PRO' && bonusDays > 0 && (
              <p className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-800">
                你的标准版还有剩余时间，升级后会折算为 <span className="font-semibold">{bonusDays} 天</span>专业版，加在购买的天数后面。
              </p>
            )}
          </fieldset>
        )}

        {prices.isLoading ? (
          <div className="h-32 animate-pulse rounded-lg bg-slate-100" />
        ) : prices.isError ? (
          <ErrorState onRetry={() => void prices.refetch()} />
        ) : options.length === 0 ? (
          <p className="text-sm text-slate-500">暂无可购买的套餐</p>
        ) : (
          <fieldset>
            <legend className="mb-2 text-sm font-medium text-slate-700">选择时长</legend>
            <div className="grid gap-2 sm:grid-cols-3">
              {options.map((p) => {
                const active = selected?.id === p.id
                // 年付相比 12 个月月付节省的金额（按分计算，避免浮点误差）
                const savingCents =
                  p.period === 'YEAR' && monthly ? (toCents(monthly.amount) ?? 0) * 12 - (toCents(p.amount) ?? 0) : 0
                return (
                  <button
                    key={p.id}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setPriceId(p.id)}
                    className={cn(
                      'relative rounded-xl border p-4 text-left transition-colors',
                      active ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600' : 'border-slate-200 hover:bg-slate-50',
                    )}
                  >
                    {p.period === 'TRIAL_MONTH' && <Badge tone="amber" className="absolute right-2 top-2">每店限一次</Badge>}
                    {savingCents > 0 && <Badge tone="green" className="absolute right-2 top-2">省 {formatMoney(fromCents(savingCents), currency)}</Badge>}
                    <p className="text-sm font-medium text-slate-700">{PERIOD_LABELS[p.period]}</p>
                    <p className="mt-1 text-xl font-semibold tabular-nums text-slate-900">{formatMoney(p.amount, p.currency)}</p>
                    <p className="text-xs text-slate-500">{p.durationDays} 天</p>
                  </button>
                )
              })}
            </div>
            {plan.data?.plan === tab && (
              <p className="mt-2 text-xs text-slate-500">续费会从当前到期日顺延，不会浪费剩余天数。</p>
            )}
          </fieldset>
        )}

        <fieldset>
          <legend className="mb-2 text-sm font-medium text-slate-700">付款方式</legend>
          {currencyMethods.length === 0 ? (
            <p className="text-sm text-slate-500">该币种暂无可用的付款方式</p>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {currencyMethods.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  aria-pressed={method?.id === m.id}
                  onClick={() => setMethodId(m.id)}
                  className={cn(
                    'flex items-center gap-3 rounded-xl border p-3 text-left text-sm',
                    method?.id === m.id ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600' : 'border-slate-200 hover:bg-slate-50',
                  )}
                >
                  <QrCode className="size-5 shrink-0 text-slate-500" aria-hidden />
                  <span className="font-medium text-slate-800">{m.displayName}</span>
                </button>
              ))}
            </div>
          )}
        </fieldset>

        <Button
          size="lg"
          className="w-full"
          disabled={!selected || !method || !!open}
          loading={create.isPending}
          onClick={() => create.mutate()}
        >
          {selected ? `去付款 ${formatMoney(selected.amount, selected.currency)}` : '去付款'}
        </Button>

        <div className="rounded-lg bg-slate-50 p-4 text-sm">
          <p className="font-medium text-slate-700">套餐对比</p>
          <ul className="mt-2 space-y-1 text-slate-600">
            <li>免费版：最多 100 个商品；开单（散客零售价）、库存管理；近 30 天 / 本月报表</li>
            <li>标准版：商品不限；客户、供应商、采购入库、批发价开单、完整报表、导出 CSV</li>
            <li>专业版：标准版全部功能 + 应收账款（赊账开单、收款记录、欠款账龄、客户对账单）</li>
          </ul>
        </div>
      </CardBody>
    </Card>
  )
}

// ---------------- 激活码 ----------------

function RedeemCard() {
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  const redeem = useMutation({
    mutationFn: () => billingApi.redeem(code),
    onSuccess: async (current) => {
      setCode('')
      setError(null)
      await queryClient.invalidateQueries({ queryKey: ['billing'] })
      await refreshSession()
      toast.success(`已开通${PLAN_LABELS[current.plan]}，有效期至 ${formatDateTime(current.expiresAt)}`)
    },
    onError: (e) => setError(errorMessage(e, '兑换失败')),
  })

  return (
    <Card>
      <CardHeader title="使用激活码" description="在小红书店铺购买的激活码在这里兑换" />
      <CardBody>
        <form
          className="space-y-3"
          noValidate
          onSubmit={(e) => {
            e.preventDefault()
            if (!code.trim()) {
              setError('请输入激活码')
              return
            }
            redeem.mutate()
          }}
        >
          <Field label="激活码" htmlFor="redeem-code" error={error ?? undefined} hint="格式如 SBZ-XXXX-XXXX-XXXX，不区分大小写">
            <Input
              id="redeem-code"
              value={code}
              autoComplete="off"
              spellCheck={false}
              placeholder="SBZ-"
              className="font-mono uppercase"
              invalid={!!error}
              onChange={(e) => {
                setCode(e.target.value)
                setError(null)
              }}
            />
          </Field>
          <Button type="submit" variant="secondary" className="w-full" loading={redeem.isPending}>
            <KeyRound aria-hidden />
            兑换
          </Button>
        </form>
      </CardBody>
    </Card>
  )
}

// ---------------- 付款记录 ----------------

function PaymentsCard() {
  const { data, isLoading } = useQuery({ queryKey: ['billing', 'payments'], queryFn: billingApi.payments })
  return (
    <Card>
      <CardHeader title="付款记录" />
      {isLoading ? (
        <CardBody>
          <div className="h-16 animate-pulse rounded-lg bg-slate-100" />
        </CardBody>
      ) : !data?.length ? (
        <CardBody className="text-sm text-slate-500">还没有付款记录</CardBody>
      ) : (
        <ul className="divide-y divide-slate-100">
          {data.slice(0, 10).map((o) => (
            <li key={o.id}>
              <Link to={`/settings/billing/pay/${o.id}`} className="flex items-center gap-3 px-5 py-3 hover:bg-slate-50">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-800">
                    {PLAN_LABELS[o.plan]} · {PERIOD_LABELS[o.period]} · {formatMoney(o.amount, o.currency)}
                  </p>
                  <p className="text-xs text-slate-500">{formatDateTime(o.createdAt)}</p>
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
      <CardHeader title="套餐变更记录" />
      {isLoading ? (
        <CardBody>
          <div className="h-16 animate-pulse rounded-lg bg-slate-100" />
        </CardBody>
      ) : !data?.length ? (
        <CardBody className="text-sm text-slate-500">还没有变更记录</CardBody>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
              <tr>
                <th className="px-5 py-2">时间</th>
                <th className="px-5 py-2">方式</th>
                <th className="px-5 py-2">变更</th>
                <th className="px-5 py-2">新到期时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((l) => (
                <tr key={l.id}>
                  <td className="px-5 py-2.5 whitespace-nowrap text-slate-600">{formatDateTime(l.createdAt)}</td>
                  <td className="px-5 py-2.5">{SOURCE_LABELS[l.source]}</td>
                  <td className="px-5 py-2.5">
                    {PLAN_LABELS[l.oldPlan]} → {PLAN_LABELS[l.newPlan]}
                  </td>
                  <td className="px-5 py-2.5 whitespace-nowrap text-slate-600">{formatDateTime(l.newExpiresAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
