import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, Download, Plus } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { FormDialog } from '@/components/ui/form-dialog'
import { Field, Input, Select } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { PLAN_LABELS, type Plan } from '@/features/auth/types'
import { errorMessage, fieldErrors } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { adminApi, type BatchRow } from '../api'

function downloadCodes(batch: BatchRow, codes: string[]) {
  const rows = ['激活码,套餐,天数,首月体验', ...codes.map((c) => `${c},${PLAN_LABELS[batch.plan]},${batch.durationDays},${batch.trial ? '是' : '否'}`)]
  const blob = new Blob(['﻿' + rows.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `激活码-${batch.name}-${batch.id}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

export function AdminCodesPage() {
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<{ batch: BatchRow; codes: string[] } | null>(null)
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['admin', 'batches'], queryFn: adminApi.batches })

  return (
    <div className="space-y-6">
      <PageHeader
        title="激活码"
        description="激活码只在生成时显示一次（数据库只保存无法还原的摘要），请立即下载保存"
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden />
            生成激活码
          </Button>
        }
      />

      {created && (
        <Card className="border-emerald-300 ring-1 ring-emerald-200">
          <CardHeader
            title={`已生成 ${created.codes.length} 个激活码：${created.batch.name}`}
            description="离开页面后将无法再次查看，请先下载"
            action={
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={async () => {
                    try {
                      await navigator.clipboard.writeText(created.codes.join('\n'))
                      toast.success('已复制全部激活码')
                    } catch {
                      toast.error('复制失败')
                    }
                  }}
                >
                  <Copy aria-hidden />
                  复制全部
                </Button>
                <Button size="sm" onClick={() => downloadCodes(created.batch, created.codes)}>
                  <Download aria-hidden />
                  下载 CSV
                </Button>
              </div>
            }
          />
          <CardBody>
            <ul className="grid max-h-64 gap-1 overflow-y-auto font-mono text-sm sm:grid-cols-2 lg:grid-cols-3">
              {created.codes.map((c) => (
                <li key={c} className="rounded bg-slate-50 px-2 py-1">
                  {c}
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      )}

      <Card>
        <CardHeader title="批次" />
        {isLoading ? (
          <div className="m-5 h-32 animate-pulse rounded-lg bg-slate-100" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : !data?.length ? (
          <p className="p-6 text-center text-sm text-slate-500">还没有生成过激活码</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-5 py-2">批次</th>
                  <th className="px-5 py-2">套餐</th>
                  <th className="px-5 py-2 text-right">数量</th>
                  <th className="px-5 py-2 text-right">已兑换</th>
                  <th className="px-5 py-2 text-right">已作废</th>
                  <th className="px-5 py-2">兑换截止</th>
                  <th className="px-5 py-2">生成时间</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.map((b) => (
                  <tr key={b.id}>
                    <td className="px-5 py-2.5 font-medium text-slate-800">{b.name}</td>
                    <td className="px-5 py-2.5">
                      {PLAN_LABELS[b.plan]} {b.durationDays} 天 {b.trial && <Badge tone="amber">首月体验</Badge>}
                    </td>
                    <td className="px-5 py-2.5 text-right tabular-nums">{b.quantity}</td>
                    <td className="px-5 py-2.5 text-right tabular-nums">{b.redeemed}</td>
                    <td className="px-5 py-2.5 text-right tabular-nums">{b.voided}</td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">{b.expiresAt ? formatDateTime(b.expiresAt) : '长期有效'}</td>
                    <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">{formatDateTime(b.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <VoidCodeCard />

      {creating && (
        <CreateBatchDialog
          onClose={() => setCreating(false)}
          onCreated={(r) => {
            setCreated(r)
            setCreating(false)
          }}
        />
      )}
    </div>
  )
}

function CreateBatchDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: (r: { batch: BatchRow; codes: string[] }) => void
}) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [preset, setPreset] = useState<'trial' | 'month' | 'year' | 'custom'>('month')
  const [plan, setPlan] = useState<Plan>('STANDARD')
  const [days, setDays] = useState('30')
  const [quantity, setQuantity] = useState('10')
  const [expiresOn, setExpiresOn] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const trial = preset === 'trial'
  const duration = preset === 'year' ? 365 : preset === 'custom' ? Number(days) : 30

  const create = useMutation({
    mutationFn: () =>
      adminApi.createBatch({
        name: name.trim(),
        plan,
        durationDays: duration,
        trial,
        quantity: Number(quantity),
        expiresOn: expiresOn || null,
      }),
    onSuccess: (r) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'batches'] })
      onCreated(r)
    },
    onError: (e) => {
      setErrors(fieldErrors(e))
      toast.error(errorMessage(e, '生成失败'))
    },
  })

  return (
    <FormDialog
      open
      onClose={onClose}
      title="生成激活码"
      description="用于小红书店铺发货：每个激活码只能兑换一次"
      submitText="生成"
      saving={create.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        const errs: Record<string, string> = {}
        if (!name.trim()) errs.name = '请填写批次名称'
        const q = Number(quantity)
        if (!Number.isInteger(q) || q < 1 || q > 1000) errs.quantity = '数量为 1–1000'
        if (!Number.isInteger(duration) || duration < 1 || duration > 3660) errs.durationDays = '天数为 1–3660'
        setErrors(errs)
        if (Object.keys(errs).length === 0) create.mutate()
      }}
    >
      <Field label="批次名称" htmlFor="cb-name" error={errors.name} required hint="例如：小红书 11 月首月体验">
        <Input id="cb-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} invalid={!!errors.name} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="套餐" htmlFor="cb-plan">
          <Select
            id="cb-plan"
            value={plan}
            onChange={(e) => {
              const next = e.target.value as Plan
              setPlan(next)
              if (next !== 'STANDARD' && preset === 'trial') setPreset('month')
            }}
          >
            <option value="STANDARD">标准版</option>
            <option value="PRO">专业版</option>
          </Select>
        </Field>
        <Field label="类型" htmlFor="cb-preset">
          <Select id="cb-preset" value={preset} onChange={(e) => setPreset(e.target.value as typeof preset)}>
            {plan === 'STANDARD' && <option value="trial">首月体验（30 天，每店限一次）</option>}
            <option value="month">月卡（30 天）</option>
            <option value="year">年卡（365 天）</option>
            <option value="custom">自定义天数</option>
          </Select>
        </Field>
      </div>
      {preset === 'custom' && (
        <Field label="天数" htmlFor="cb-days" error={errors.durationDays}>
          <Input id="cb-days" inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} invalid={!!errors.durationDays} />
        </Field>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="数量" htmlFor="cb-qty" error={errors.quantity} required>
          <Input id="cb-qty" inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} invalid={!!errors.quantity} />
        </Field>
        <Field label="兑换截止日期" htmlFor="cb-exp" hint="留空为长期有效">
          <Input id="cb-exp" type="date" value={expiresOn} onChange={(e) => setExpiresOn(e.target.value)} />
        </Field>
      </div>
    </FormDialog>
  )
}

function VoidCodeCard() {
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const voidIt = useMutation({
    mutationFn: () => adminApi.voidCode(code),
    onSuccess: (r) => {
      setCode('')
      void queryClient.invalidateQueries({ queryKey: ['admin', 'batches'] })
      toast.success(`激活码 ${r.hint} 已作废`)
    },
    onError: (e) => toast.error(errorMessage(e, '作废失败')),
  })
  return (
    <Card>
      <CardHeader title="作废激活码" description="订单退款或激活码泄露时使用；已兑换的激活码不能作废" />
      <CardBody>
        <form
          className="flex flex-col gap-2 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault()
            if (code.trim()) voidIt.mutate()
          }}
        >
          <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="SBZ-XXXX-XXXX-XXXX" className="font-mono uppercase sm:max-w-sm" aria-label="要作废的激活码" />
          <Button type="submit" variant="danger" loading={voidIt.isPending} disabled={!code.trim()}>
            作废
          </Button>
        </form>
      </CardBody>
    </Card>
  )
}
