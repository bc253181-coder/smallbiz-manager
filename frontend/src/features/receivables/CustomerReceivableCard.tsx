import { useQuery } from '@tanstack/react-query'
import { FileText, HandCoins } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { useFeature } from '@/features/billing/api'
import { formatDateTime, useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { cn } from '@/lib/utils'
import { receivableApi } from './api'
import { ReceivePaymentDialog } from './ReceivePaymentDialog'

import { tr } from '@/lib/i18n'
/** 客户详情页的欠款卡片（专业版）。 */
export function CustomerReceivableCard({ customerId, customerName }: { customerId: number; customerName: string }) {
  const allowed = useFeature('RECEIVABLES')
  const canManage = useCanManage()
  const money = useMoney()
  const [paying, setPaying] = useState(false)
  const { data, isLoading } = useQuery({
    queryKey: ['customer-receivable', customerId],
    queryFn: () => receivableApi.customer(customerId),
    enabled: allowed,
  })
  if (!allowed) return null

  const owes = data && Number(data.outstanding) > 0
  return (
    <Card>
      <CardHeader
        title={tr('欠款')}
        description={
          isLoading ? tr('加载中…') : owes ? tr('{{length}} 张销售单未结清', { length: data.orders.length }) : tr('没有欠款')
        }
        action={
          <div className="flex flex-wrap gap-2">
            {canManage && (
              <Link to={`/customers/${customerId}/statement`} className={buttonVariants({ size: 'sm', variant: 'secondary' })}>
                <FileText aria-hidden />
                {tr('对账单')}
              </Link>
            )}
            {owes && (
              <Button size="sm" onClick={() => setPaying(true)}>
                <HandCoins aria-hidden />
                {tr('收款')}
              </Button>
            )}
          </div>
        }
      />
      {owes && (
        <>
          <div className="flex items-baseline justify-between px-5 pt-4">
            <span className="text-sm text-slate-500">{tr('当前欠款')}</span>
            <span className="text-2xl font-semibold tabular-nums text-red-600">{money(data.outstanding)}</span>
          </div>
          <ul className="mt-2 divide-y divide-slate-100">
            {data.orders.slice(0, 10).map((o) => (
              <li key={o.id}>
                <Link to={`/sales/${o.id}`} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm hover:bg-slate-50">
                  <span className="min-w-0">
                    <span className="block font-medium text-slate-800">{o.orderNo}</span>
                    <span className="block text-xs text-slate-500">
                      {formatDateTime(o.orderDate)} · <span className={cn(o.ageDays > 30 && 'text-red-600')}>{o.ageDays} {tr('天')}</span>
                    </span>
                  </span>
                  <span className="shrink-0 text-right tabular-nums">
                    {money(o.outstanding)}
                    <span className="block text-xs text-slate-400">{tr('共')} {money(o.totalAmount)}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          {canManage && (
            <Link to={`/receivables/payments?customerId=${customerId}`} className="block border-t border-slate-100 px-5 py-2.5 text-center text-sm font-medium text-blue-600 hover:bg-slate-50">
              {tr('查看收款记录')}
            </Link>
          )}
        </>
      )}
      {paying && <ReceivePaymentDialog customerId={customerId} customerName={customerName} onClose={() => setPaying(false)} />}
    </Card>
  )
}
