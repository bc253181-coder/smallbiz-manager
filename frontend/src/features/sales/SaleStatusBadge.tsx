import { Badge } from '@/components/ui/badge'
import { PAYMENT_STATUS_LABELS, type PaymentStatus, type SaleStatus } from './api'

/** 单据状态；有欠款时再显示付款状态（部分付款 / 未付款）。 */
export function SaleStatusBadge({ status, paymentStatus }: { status: SaleStatus; paymentStatus?: PaymentStatus }) {
  if (status === 'CANCELLED') return <Badge tone="red">已取消</Badge>
  if (paymentStatus && paymentStatus !== 'PAID') {
    return <Badge tone="amber">{PAYMENT_STATUS_LABELS[paymentStatus]}</Badge>
  }
  return <Badge tone="green">已完成</Badge>
}