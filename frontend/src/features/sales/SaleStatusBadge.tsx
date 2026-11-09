import { Badge } from '@/components/ui/badge'
import { PAYMENT_STATUS_LABELS, type PaymentStatus, type SaleStatus } from './api'

import { tr } from '@/lib/i18n'
/** 单据状态；有欠款时再显示付款状态（部分付款 / 未付款）。 */
export function SaleStatusBadge({ status, paymentStatus }: { status: SaleStatus; paymentStatus?: PaymentStatus }) {
  if (status === 'CANCELLED') return <Badge tone="red">{tr('已取消')}</Badge>
  if (paymentStatus && paymentStatus !== 'PAID') {
    return <Badge tone="amber">{PAYMENT_STATUS_LABELS[paymentStatus]}</Badge>
  }
  return <Badge tone="green">{tr('已完成')}</Badge>
}