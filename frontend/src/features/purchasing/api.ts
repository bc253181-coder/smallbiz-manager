import { api } from '@/lib/api'
import type { Page } from '@/features/catalog/types'

import { tr } from '@/lib/i18n'
export type PurchaseStatus = 'DRAFT' | 'ORDERED' | 'RECEIVED' | 'CANCELLED'

export interface PurchaseSummary {
  id: number
  orderNo: string
  supplierId: number
  supplierName: string
  status: PurchaseStatus
  orderDate: string
  expectedDate: string | null
  itemCount: number
  totalQuantity: number
  totalAmount: string
}

export interface PurchaseItem {
  id: number
  productId: number
  productName: string
  sku: string
  unit: string
  quantity: number
  receivedQty: number
  unitCost: string
  lineTotal: string
}

export interface PurchaseDetail extends PurchaseSummary {
  notes: string | null
  items: PurchaseItem[]
  createdByName: string | null
  submittedAt: string | null
  receivedAt: string | null
  receivedByName: string | null
  cancelledAt: string | null
  cancelledByName: string | null
  cancelReason: string | null
  warehouseId: number | null
  warehouseName: string | null
  version: number
}

export interface PurchaseInput {
  supplierId: number
  expectedDate: string | null
  notes: string
  items: { productId: number; quantity: number; unitCost: string }[]
  submit: boolean
}

export interface SuppliedProduct {
  productId: number
  productName: string
  sku: string
  unit: string
  totalQuantity: number
  lastReceivedAt: string | null
  orderCount: number
}

export const PURCHASE_STATUS_LABELS: Record<PurchaseStatus, string> = {
  DRAFT: tr('草稿'),
  ORDERED: tr('已下单'),
  RECEIVED: tr('已收货'),
  CANCELLED: tr('已取消'),
}

export const PURCHASE_STATUS_TONES = {
  DRAFT: 'slate',
  ORDERED: 'blue',
  RECEIVED: 'green',
  CANCELLED: 'red',
} as const

export interface PurchaseQuery {
  q?: string
  status?: PurchaseStatus
  supplierId?: number
  from?: string
  to?: string
  page?: number
  size?: number
}

export const purchaseApi = {
  list: (q: PurchaseQuery) =>
    api
      .get<Page<PurchaseSummary>>('/purchase-orders', {
        params: Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined && v !== '')),
      })
      .then((r) => r.data),
  get: (id: number) => api.get<PurchaseDetail>(`/purchase-orders/${id}`).then((r) => r.data),
  create: (body: PurchaseInput) => api.post<PurchaseDetail>('/purchase-orders', body).then((r) => r.data),
  update: (id: number, body: PurchaseInput & { version: number }) =>
    api.put<PurchaseDetail>(`/purchase-orders/${id}`, body).then((r) => r.data),
  submit: (id: number) => api.post<PurchaseDetail>(`/purchase-orders/${id}/submit`).then((r) => r.data),
  receive: (id: number, warehouseId?: number) =>
    api.post<PurchaseDetail>(`/purchase-orders/${id}/receive`, warehouseId ? { warehouseId } : {}).then((r) => r.data),
  cancel: (id: number, reason: string) =>
    api.post<PurchaseDetail>(`/purchase-orders/${id}/cancel`, { reason }).then((r) => r.data),
  remove: (id: number) => api.delete(`/purchase-orders/${id}`),
  suppliedProducts: (supplierId: number) =>
    api.get<SuppliedProduct[]>(`/suppliers/${supplierId}/products`).then((r) => r.data),
}
