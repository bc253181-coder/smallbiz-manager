import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { Link } from 'react-router'
import { Card, CardHeader } from '@/components/ui/card'
import { useFeature } from '@/features/billing/api'
import { useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { customerPriceApi } from './customerPrices'

import { tr } from '@/lib/i18n'
/** 商品详情页：哪些客户对这个商品有协议价（店主/管理员；没有协议价时不显示）。 */
export function ProductCustomerPricesCard({ productId }: { productId: number }) {
  const canManage = useCanManage()
  const canCustomers = useFeature('CUSTOMERS')
  const money = useMoney()
  const { data } = useQuery({
    queryKey: ['product-customer-prices', productId],
    queryFn: () => customerPriceApi.byProduct(productId),
    enabled: canManage && canCustomers,
  })
  if (!data?.length) return null
  return (
    <Card>
      <CardHeader title={tr('客户协议价')} description={tr('{{length}} 位客户有专门价格', { length: data.length })} />
      <ul className="divide-y divide-slate-100">
        {data.map((r) => (
          <li key={r.customerId} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm">
            <Link to={`/customers/${r.customerId}`} className="min-w-0 truncate font-medium text-slate-800 hover:text-blue-600">
              {r.customerName}
              {r.phone && <span className="ml-2 text-xs font-normal text-slate-500">{r.phone}</span>}
            </Link>
            <span className="flex shrink-0 items-center gap-1.5 font-semibold tabular-nums text-violet-700">
              {r.belowCost && <AlertTriangle className="size-3.5 text-red-600" aria-label={tr('低于成本')} />}
              {money(r.price)}
            </span>
          </li>
        ))}
      </ul>
    </Card>
  )
}
