import { Badge } from '@/components/ui/badge'
import { PURCHASE_STATUS_LABELS, PURCHASE_STATUS_TONES, type PurchaseStatus } from './api'

export function PurchaseStatusBadge({ status }: { status: PurchaseStatus }) {
  return <Badge tone={PURCHASE_STATUS_TONES[status]}>{PURCHASE_STATUS_LABELS[status]}</Badge>
}
