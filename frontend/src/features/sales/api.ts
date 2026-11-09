import { api } from '@/lib/api'
import type { Page } from '@/features/catalog/types'
import type { PriceTier } from '@/features/partners/api'

import { tr } from '@/lib/i18n'
export type SaleStatus = 'COMPLETED' | 'CANCELLED'
export type PaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID'

export interface SaleSummary {
  id: number
  orderNo: string
  customerId: number | null
  customerName: string | null
  status: SaleStatus
  orderDate: string
  itemCount: number
  totalQuantity: number
  totalAmount: string
  paidAmount: string
  paymentStatus: PaymentStatus
  createdBy: number | null
  createdByName: string | null
}

export interface SaleItem {
  productId: number
  productName: string
  sku: string
  unit: string
  quantity: number
  unitPrice: string
  /** 店员为 null */
  unitCost: string | null
  lineTotal: string
  /** 单价来源 */
  priceSource: PriceSource
}

export type PriceSource = 'RETAIL' | 'WHOLESALE' | 'CONTRACT' | 'MANUAL'

export const PRICE_SOURCE_LABELS: Record<PriceSource, string> = {
  RETAIL: tr('零售价'),
  WHOLESALE: tr('批发价'),
  CONTRACT: tr('协议价'),
  MANUAL: tr('手动改价'),
}

export interface SaleDetail extends SaleSummary {
  priceTier: PriceTier
  warehouseId: number
  warehouseName: string | null
  subtotal: string
  discountAmount: string
  paidAtSale: string
  outstanding: string
  lastPaymentAt: string | null
  payments: SalePaymentLine[]
  totalCost: string | null
  grossProfit: string | null
  notes: string | null
  items: SaleItem[]
  createdByName: string | null
  cancelledAt: string | null
  cancelledByName: string | null
  cancelReason: string | null
}

export interface SalePaymentLine {
  paymentId: number
  paymentNo: string
  receivedAt: string
  method: string
  status: 'VALID' | 'VOIDED'
  amount: string
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PAID: tr('已付清'),
  PARTIAL: tr('部分付款'),
  UNPAID: tr('未付款'),
}

export interface SaleInput {
  customerId: number | null
  priceTier: PriceTier
  discountAmount: string | null
  notes: string
  /** 本次实收；null = 全额收款 */
  paidAmount: string | null
  items: { productId: number; quantity: number; unitPrice: string }[]
  /** 出货仓库；不传 = 默认仓库 */
  warehouseId?: number
}

export interface CustomerStats {
  orderCount: number
  totalSpent: string
  lastOrderAt: string | null
}

export interface SaleQuery {
  q?: string
  status?: SaleStatus
  customerId?: number
  from?: string
  to?: string
  /** 开单人（用户 id） */
  createdBy?: number
  page?: number
  size?: number
}

export const saleApi = {
  list: (q: SaleQuery) =>
    api
      .get<Page<SaleSummary>>('/sales-orders', {
        params: Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined && v !== '')),
      })
      .then((r) => r.data),
  get: (id: number) => api.get<SaleDetail>(`/sales-orders/${id}`).then((r) => r.data),
  /** 开过单的人（开单人筛选，店主/管理员） */
  creators: () => api.get<{ id: number; name: string }[]>('/sales-orders/creators').then((r) => r.data),
  create: (body: SaleInput) => api.post<SaleDetail>('/sales-orders', body).then((r) => r.data),
  cancel: (id: number, reason: string) => api.post<SaleDetail>(`/sales-orders/${id}/cancel`, { reason }).then((r) => r.data),
  customerStats: (customerId: number) => api.get<CustomerStats>(`/customers/${customerId}/stats`).then((r) => r.data),
}
