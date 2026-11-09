import { useMutation, useQuery } from '@tanstack/react-query'
import { FlaskConical } from 'lucide-react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { ErrorState } from '@/components/ui/page'
import { PLAN_LABELS } from '@/features/auth/types'
import { api, errorMessage } from '@/lib/api'
import { formatMoney } from '@/lib/format'
import { tr } from '@/lib/i18n'

interface Summary {
  orderId: number
  orderNo: string
  plan: 'FREE' | 'STANDARD' | 'PRO'
  durationDays: number
  currency: string
  amount: string
  status: string
}

type Outcome = 'SUCCESS' | 'LOST' | 'FAIL'

/**
 * 模拟支付页面（只在开发环境存在，代替真实支付渠道的付款页）。
 * 「付款成功」会像真实渠道一样发送带签名的付款通知；「通知丢失」用来测试对账补开通。
 */
export function MockCheckoutPage() {
  const orderNo = useParams().orderNo ?? ''
  const navigate = useNavigate()
  const summary = useQuery({
    queryKey: ['mock-pay', orderNo],
    queryFn: () => api.get<Summary>(`/payments/mock/${orderNo}`).then((r) => r.data),
  })
  const pay = useMutation({
    mutationFn: (outcome: Outcome) => api.post<Summary>(`/payments/mock/${orderNo}/pay`, { outcome }).then((r) => r.data),
    onSuccess: (s, outcome) => {
      if (outcome === 'FAIL') {
        toast.error(tr('付款失败（模拟）'))
        return
      }
      // 和真实渠道一样：付款后跳回本网站的付款结果页
      navigate(`/settings/billing/pay/${s.orderId}`)
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  return (
    <div className="mx-auto max-w-md py-6">
      <Card>
        <CardHeader title={tr('模拟支付')} description={tr('开发测试用，不会产生真实扣款')} />
        <CardBody className="space-y-4">
          <p className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
            <FlaskConical className="size-4 shrink-0" aria-hidden />
            {tr('正式环境会跳转到真实支付渠道的付款页面')}
          </p>
          {summary.isLoading ? (
            <div className="h-24 animate-pulse rounded-lg bg-slate-100" />
          ) : summary.isError || !summary.data ? (
            <ErrorState onRetry={() => void summary.refetch()} message={tr('付款单不存在或加载失败')} />
          ) : (
            <>
              <div>
                <p className="text-sm text-slate-500">
                  {summary.data.durationDays > 0
                    ? `${PLAN_LABELS[summary.data.plan]} · ${summary.data.durationDays} ${tr('天')}`
                    : tr('高级永久会员')}{' '}
                  · {summary.data.orderNo}
                </p>
                <p className="text-3xl font-semibold tabular-nums text-slate-900">
                  {formatMoney(summary.data.amount, summary.data.currency)}
                </p>
              </div>
              <div className="grid gap-2">
                <Button loading={pay.isPending && pay.variables === 'SUCCESS'} onClick={() => pay.mutate('SUCCESS')}>
                  {tr('模拟付款成功')}
                </Button>
                <Button variant="secondary" loading={pay.isPending && pay.variables === 'LOST'} onClick={() => pay.mutate('LOST')}>
                  {tr('模拟付款成功但通知丢失（测试对账）')}
                </Button>
                <Button variant="ghost" className="text-red-600" loading={pay.isPending && pay.variables === 'FAIL'} onClick={() => pay.mutate('FAIL')}>
                  {tr('模拟付款失败')}
                </Button>
              </div>
            </>
          )}
        </CardBody>
      </Card>
    </div>
  )
}
