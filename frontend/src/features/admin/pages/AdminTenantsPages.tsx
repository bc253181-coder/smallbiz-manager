import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Search } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { FormDialog } from '@/components/ui/form-dialog'
import { Field, Input, Select } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { PLAN_LABELS, type Plan } from '@/features/auth/types'
import { errorMessage, fieldErrors } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { useDebouncedValue } from '@/lib/hooks'
import { DispenserStockNotice } from './AdminDispenserPage'
import { adminApi, type TenantRow } from '../api'

export function AdminOverviewPage() {
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['admin', 'stats'], queryFn: adminApi.stats })
  const tiles = data
    ? [
        { label: '待审核付款', value: data.pendingPayments, to: '/admin/payments', highlight: data.pendingPayments > 0 },
        { label: '店铺总数', value: data.tenantCount, to: '/admin/tenants' },
        { label: '付费店铺', value: data.paidTenantCount, to: '/admin/tenants' },
        { label: '本月新增店铺', value: data.newTenantsThisMonth, to: '/admin/tenants' },
      ]
    : []
  return (
    <div>
      <PageHeader title="概况" description="平台后台只显示店铺的套餐信息和店主联系方式，不查看店铺的经营数据" />
      <DispenserStockNotice />
      {isLoading ? (
        <div className="h-28 animate-pulse rounded-xl bg-slate-100" />
      ) : isError ? (
        <Card>
          <ErrorState onRetry={() => void refetch()} />
        </Card>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4 [&>*]:min-w-0">
          {tiles.map((t) => (
            <Link
              key={t.label}
              to={t.to}
              className={
                'rounded-xl border bg-white p-5 shadow-sm hover:border-slate-300 ' +
                (t.highlight ? 'border-amber-300 ring-1 ring-amber-200' : 'border-slate-200')
              }
            >
              <p className="text-sm text-slate-500">{t.label}</p>
              <p className="mt-1 flex items-center justify-between text-3xl font-semibold tabular-nums text-slate-900">
                {t.value}
                <ChevronRight className="size-5 text-slate-300" aria-hidden />
              </p>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

export function AdminTenantsPage() {
  const [q, setQ] = useState('')
  const query = useDebouncedValue(q.trim())
  const [editing, setEditing] = useState<TenantRow | null>(null)
  const [membershipFor, setMembershipFor] = useState<TenantRow | null>(null)
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin', 'tenants', query],
    queryFn: () => adminApi.tenants(query),
  })

  return (
    <div>
      <PageHeader title="店铺" description="会员跟着店主账号：设为高级永久会员后，店主名下所有店铺都是高级会员。调整会记录原因" />
      <Card>
        <CardHeader
          title={data ? `共 ${data.length} 家` : '店铺列表'}
          action={
            <div className="relative w-full sm:w-72">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden />
              <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="店铺名称或店主邮箱" className="pl-9" aria-label="搜索店铺" />
            </div>
          }
        />
        {isLoading ? (
          <div className="m-5 h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : !data?.length ? (
          <p className="p-6 text-center text-sm text-slate-500">没有找到店铺</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-5 py-2">店铺</th>
                  <th className="px-5 py-2">店主</th>
                  <th className="px-5 py-2">套餐</th>
                  <th className="px-5 py-2">到期时间</th>
                  <th className="px-5 py-2">注册时间</th>
                  <th className="px-5 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((t) => (
                  <tr key={t.id}>
                    <td className="px-5 py-2.5">
                      <p className="font-medium text-slate-800">{t.name}</p>
                      <p className="text-xs text-slate-400">ID {t.id}</p>
                    </td>
                    <td className="px-5 py-2.5">
                      <p className="text-slate-700">{t.ownerName ?? '—'}</p>
                      <p className="text-xs text-slate-500">{t.ownerEmail ?? '—'}</p>
                      {t.ownerMembership === 'LIFETIME' && <Badge tone="amber" className="mt-1">高级永久会员</Badge>}
                    </td>
                    <td className="px-5 py-2.5">
                      <Badge tone={t.plan === 'FREE' ? 'slate' : 'green'}>{t.plan !== 'FREE' && !t.planExpiresAt ? '高级永久会员' : PLAN_LABELS[t.plan]}</Badge>
                      {t.rawPlan !== t.plan && <span className="ml-1 text-xs text-slate-400">（{PLAN_LABELS[t.rawPlan]}已过期）</span>}
                      {t.trialUsed && <span className="ml-1 text-xs text-slate-400">已体验</span>}
                    </td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">{formatDateTime(t.planExpiresAt)}</td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">{formatDateTime(t.createdAt)}</td>
                    <td className="space-x-1 whitespace-nowrap px-5 py-2.5 text-right">
                      {t.ownerId && (
                        <Button size="sm" variant="secondary" onClick={() => setMembershipFor(t)}>
                          {t.ownerMembership === 'LIFETIME' ? '取消永久会员' : '设为永久会员'}
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" onClick={() => setEditing(t)}>
                        调整套餐
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {editing && <SetPlanDialog tenant={editing} onClose={() => setEditing(null)} />}
      {membershipFor && <MembershipDialog tenant={membershipFor} onClose={() => setMembershipFor(null)} />}
    </div>
  )
}

function ymd(iso: string | null) {
  if (!iso) return ''
  const d = new Date(new Date(iso).getTime() - 86_400_000) // 到期时间是"次日 0 点"，显示为最后可用日
  return d.toISOString().slice(0, 10)
}

function SetPlanDialog({ tenant, onClose }: { tenant: TenantRow; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [plan, setPlan] = useState<Plan>(tenant.rawPlan === 'FREE' ? 'STANDARD' : tenant.rawPlan)
  const [expiresOn, setExpiresOn] = useState(ymd(tenant.planExpiresAt))
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const save = useMutation({
    mutationFn: () => adminApi.setPlan(tenant.id, { plan, expiresOn: plan === 'FREE' ? null : expiresOn || null, note }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] })
      toast.success('套餐已更新')
      onClose()
    },
    onError: (e) => {
      setErrors(fieldErrors(e))
      toast.error(errorMessage(e, '保存失败'))
    },
  })

  return (
    <FormDialog
      open
      onClose={onClose}
      title={`调整套餐：${tenant.name}`}
      description="用于补偿、退款或线下收款等特殊情况"
      submitText="保存"
      saving={save.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        const errs: Record<string, string> = {}
        if (!note.trim()) errs.note = '请填写原因'
        if (plan !== 'FREE' && !expiresOn) errs.expiresOn = '请选择到期日期'
        setErrors(errs)
        if (Object.keys(errs).length === 0) save.mutate()
      }}
    >
      <Field label="套餐" htmlFor="sp-plan">
        <Select id="sp-plan" value={plan} onChange={(e) => setPlan(e.target.value as Plan)}>
          <option value="FREE">免费版</option>
          <option value="STANDARD">标准版</option>
          <option value="PRO">专业版</option>
        </Select>
      </Field>
      {plan !== 'FREE' && (
        <Field label="有效期至（含当天）" htmlFor="sp-exp" error={errors.expiresOn}>
          <Input id="sp-exp" type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} invalid={!!errors.expiresOn} />
        </Field>
      )}
      <Field label="原因" htmlFor="sp-note" error={errors.note} required>
        <Input id="sp-note" value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} invalid={!!errors.note} placeholder="例如：线下收款 / 故障补偿 7 天" />
      </Field>
    </FormDialog>
  )
}

/** 设置 / 取消店主的高级永久会员（必须填写原因，记录在会员变更记录中）。 */
function MembershipDialog({ tenant, onClose }: { tenant: TenantRow; onClose: () => void }) {
  const queryClient = useQueryClient()
  const target = tenant.ownerMembership === 'LIFETIME' ? 'FREE' : 'LIFETIME'
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => adminApi.setMembership(tenant.ownerId!, target, note.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin'] })
      toast.success(target === 'LIFETIME' ? '已设为高级永久会员' : '已取消高级永久会员')
      onClose()
    },
    onError: (e) => toast.error(errorMessage(e, '操作失败')),
  })
  return (
    <FormDialog
      open
      onClose={onClose}
      title={target === 'LIFETIME' ? '设为高级永久会员' : '取消高级永久会员'}
      description={`${tenant.ownerName ?? ''}（${tenant.ownerEmail ?? ''}）名下所有店铺都会${target === 'LIFETIME' ? '成为高级会员' : '回到普通会员，数据全部保留'}`}
      submitText="确认"
      saving={save.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        if (!note.trim()) {
          setError('请填写原因')
          return
        }
        save.mutate()
      }}
    >
      <Field label="原因" htmlFor="ms-note" error={error ?? undefined} required>
        <Input
          id="ms-note"
          value={note}
          maxLength={200}
          placeholder={target === 'LIFETIME' ? '例如：线下付款 / 补偿' : '例如：退款'}
          onChange={(e) => {
            setNote(e.target.value)
            setError(null)
          }}
        />
      </Field>
    </FormDialog>
  )
}
