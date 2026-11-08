import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageUp } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Input, Textarea } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { PLAN_LABELS } from '@/features/auth/types'
import { PERIOD_LABELS, type MethodView, type PriceView } from '@/features/billing/api'
import { errorMessage } from '@/lib/api'
import { adminApi } from '../api'

export function AdminConfigPage() {
  const prices = useQuery({ queryKey: ['admin', 'prices'], queryFn: adminApi.prices })
  const methods = useQuery({ queryKey: ['admin', 'methods'], queryFn: adminApi.methods })

  return (
    <div className="space-y-6">
      <PageHeader title="价格与收款" description="改价只影响之后新建的付款单；已创建的付款单按原价格" />
      <Card>
        <CardHeader title="套餐价格" description="停用的价格不会在商户端显示（专业版暂未开放）" />
        {prices.isLoading ? (
          <div className="m-5 h-40 animate-pulse rounded-lg bg-slate-100" />
        ) : prices.isError ? (
          <ErrorState onRetry={() => void prices.refetch()} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-sm">
              <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
                <tr>
                  <th className="px-5 py-2">币种</th>
                  <th className="px-5 py-2">套餐</th>
                  <th className="px-5 py-2">天数</th>
                  <th className="px-5 py-2">价格</th>
                  <th className="px-5 py-2">启用</th>
                  <th className="px-5 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {prices.data?.map((p) => <PriceRow key={p.id} price={p} />)}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        {methods.isLoading ? (
          <div className="h-64 animate-pulse rounded-xl bg-slate-100" />
        ) : methods.isError ? (
          <Card>
            <ErrorState onRetry={() => void methods.refetch()} />
          </Card>
        ) : (
          methods.data?.map((m) => <MethodCard key={m.id} method={m} />)
        )}
      </div>
    </div>
  )
}

function PriceRow({ price }: { price: PriceView }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(price.amount)
  const [enabled, setEnabled] = useState(price.enabled)
  const valid = /^\d{1,8}(\.\d{1,2})?$/.test(amount) && Number(amount) > 0
  const dirty = amount !== price.amount || enabled !== price.enabled
  const save = useMutation({
    mutationFn: () => adminApi.updatePrice(price.id, { amount, enabled }),
    onSuccess: (p) => {
      queryClient.setQueryData<PriceView[]>(['admin', 'prices'], (list) => list?.map((x) => (x.id === p.id ? p : x)))
      void queryClient.invalidateQueries({ queryKey: ['billing', 'prices'] })
      setAmount(p.amount)
      toast.success('价格已保存')
    },
    onError: (e) => toast.error(errorMessage(e, '保存失败')),
  })
  return (
    <tr>
      <td className="px-5 py-2">{price.currency}</td>
      <td className="px-5 py-2">
        {PLAN_LABELS[price.plan]} · {PERIOD_LABELS[price.period]}
      </td>
      <td className="px-5 py-2 tabular-nums">{price.durationDays}</td>
      <td className="px-5 py-2">
        <Input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" className="h-9 w-28" invalid={!valid} aria-label="价格" />
      </td>
      <td className="px-5 py-2">
        <input type="checkbox" className="size-4 accent-blue-600" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} aria-label="启用" />
      </td>
      <td className="px-5 py-2 text-right">
        <Button size="sm" variant="secondary" disabled={!dirty || !valid} loading={save.isPending} onClick={() => save.mutate()}>
          保存
        </Button>
      </td>
    </tr>
  )
}

function MethodCard({ method }: { method: MethodView }) {
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(method.displayName)
  const [payeeName, setPayeeName] = useState(method.payeeName ?? '')
  const [instructions, setInstructions] = useState(method.instructions ?? '')
  const [enabled, setEnabled] = useState(method.enabled)
  const [qr, setQr] = useState(method.qrImageUrl)

  const updated = (m: MethodView) => {
    queryClient.setQueryData<MethodView[]>(['admin', 'methods'], (list) => list?.map((x) => (x.id === m.id ? m : x)))
    void queryClient.invalidateQueries({ queryKey: ['billing', 'methods'] })
  }
  const save = useMutation({
    mutationFn: () => adminApi.updateMethod(method.id, { displayName, payeeName, instructions, enabled }),
    onSuccess: (m) => {
      updated(m)
      toast.success('已保存')
    },
    onError: (e) => toast.error(errorMessage(e, '保存失败')),
  })
  const upload = useMutation({
    mutationFn: (f: File) => adminApi.uploadQr(method.id, f),
    onSuccess: (m) => {
      updated(m)
      setQr(m.qrImageUrl)
      toast.success('收款码已更新')
    },
    onError: (e) => toast.error(errorMessage(e, '上传失败')),
  })

  return (
    <Card>
      <CardHeader title={`${method.displayName}（${method.currency}）`} description={`代码 ${method.code}`} />
      <CardBody className="grid gap-5 sm:grid-cols-[10rem_1fr] [&>*]:min-w-0">
        <div className="space-y-2">
          {qr ? (
            <img src={qr} alt="收款码" className="w-40 rounded-lg border border-slate-200" />
          ) : (
            <div className="flex h-40 w-40 items-center justify-center rounded-lg bg-slate-100 text-xs text-slate-500">未上传收款码</div>
          )}
          <label className="inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium text-blue-600 hover:underline">
            <ImageUp className="size-4" aria-hidden />
            {upload.isPending ? '上传中…' : '更换收款码'}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              disabled={upload.isPending}
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) upload.mutate(f)
                e.target.value = ''
              }}
            />
          </label>
        </div>
        <form
          className="space-y-3"
          noValidate
          onSubmit={(e) => {
            e.preventDefault()
            if (displayName.trim()) save.mutate()
          }}
        >
          <Field label="显示名称" htmlFor={`m-name-${method.id}`} error={displayName.trim() ? undefined : '请填写名称'}>
            <Input id={`m-name-${method.id}`} value={displayName} maxLength={50} onChange={(e) => setDisplayName(e.target.value)} />
          </Field>
          <Field label="收款人" htmlFor={`m-payee-${method.id}`} hint="显示在付款页，方便商户核对">
            <Input id={`m-payee-${method.id}`} value={payeeName} maxLength={100} onChange={(e) => setPayeeName(e.target.value)} />
          </Field>
          <Field label="付款说明" htmlFor={`m-ins-${method.id}`}>
            <Textarea id={`m-ins-${method.id}`} rows={3} value={instructions} maxLength={500} onChange={(e) => setInstructions(e.target.value)} />
          </Field>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" className="size-4 accent-blue-600" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            启用
          </label>
          <div className="flex justify-end">
            <Button type="submit" size="sm" loading={save.isPending}>
              保存
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  )
}
