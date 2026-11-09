import { api } from '@/lib/api'
import type { Page } from '@/features/catalog/types'
import type { PriceTier } from '@/features/partners/api'

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
}

export interface SaleDetail extends SaleSummary {
  priceTier: PriceTier
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
  PAID: '已付清',
  PARTIAL: '部分付款',
  UNPAID: '未付款',
}

export interface SaleInput {
  customerId: number | null
  priceTier: PriceTier
  discountAmount: string | null
  notes: string
  /** 本次实收；null = 全额收款 */
  paidAmount: string | null
  items: { productId: number; quantity: number; unitPrice: string }[]
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
  create: (body: SaleInput) => api.post<SaleDetail>('/sales-orders', body).then((r) => r.data),
  cancel: (id: number, reason: string) => api.post<SaleDetail>(`/sales-orders/${id}/cancel`, { reason }).then((r) => r.data),
  customerStats: (customerId: number) => api.get<CustomerStats>(`/customers/${customerId}/stats`).then((r) => r.data),
}
