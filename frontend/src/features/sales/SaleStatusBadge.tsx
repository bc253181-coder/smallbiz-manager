import { Badge } from '@/components/ui/badge'
import type { SaleStatus } from './api'

export function SaleStatusBadge({ status }: { status: SaleStatus }) {
  return status === 'COMPLETED' ? <Badge tone="green">已完成</Badge> : <Badge tone="red">已取消</Badge>
}
