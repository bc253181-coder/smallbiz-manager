import { useQuery } from '@tanstack/react-query'
import { Card, CardHeader } from '@/components/ui/card'
import { formatQty } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useWarehouses, warehouseApi } from './api'

import { tr } from '@/lib/i18n'
import { warehouseName } from '@/lib/systemText'
/** 商品详情：各仓库的库存（只有一个仓库时不显示）。 */
export function ProductWarehouseStocks({ productId, unit }: { productId: number; unit: string }) {
  const wh = useWarehouses()
  const { data } = useQuery({
    queryKey: ['product-stocks', productId],
    queryFn: () => warehouseApi.productStocks(productId),
    enabled: wh.all.length > 1,
  })
  if (wh.all.length <= 1 || !data) return null
  return (
    <Card>
      <CardHeader title={tr('各仓库库存')} />
      <ul className="divide-y divide-slate-100">
        {data.map((s) => (
          <li key={s.warehouseId} className="flex items-center justify-between px-5 py-2.5 text-sm">
            <span className="text-slate-700">{warehouseName(s.warehouseName)}</span>
            <span className={cn('font-semibold tabular-nums', s.qty <= 0 ? 'text-red-600' : 'text-slate-900')}>
              {formatQty(s.qty)} <span className="text-xs font-normal text-slate-500">{unit}</span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  )
}
