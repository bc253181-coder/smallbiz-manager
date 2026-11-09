import type { QueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { MovementType, Page } from '@/features/catalog/types'

export interface StockResult {
  movementId: number
  productId: number
  qtyChange: number
  stockQty: number
  costPrice: string | null
  warehouseId: number
  /** 本次操作仓库的库存 */
  warehouseQty: number
}

export interface MovementRow {
  id: number
  productId: number
  productName: string
  productSku: string | null
  productUnit: string | null
  warehouseId: number
  warehouseName: string | null
  type: MovementType
  qtyChange: number
  qtyBefore: number
  qtyAfter: number
  unitCost: string | null
  refType: string | null
  refId: number | null
  reason: string | null
  operatorName: string | null
  createdAt: string
}

export interface InventorySummary {
  productCount: number
  totalQty: number
  /** 店员为 null */
  inventoryValue: string | null
  lowStockCount: number
  outOfStockCount: number
}

export interface LowStockItem {
  id: number
  sku: string
  name: string
  unit: string
  imageUrl: string | null
  stockQty: number
  lowStockThreshold: number
}

export interface MovementQuery {
  productId?: number
  warehouseId?: number
  type?: MovementType
  from?: string
  to?: string
  page?: number
  size?: number
}

export const inventoryApi = {
  stockIn: (body: { productId: number; quantity: number; unitCost: string | null; reason: string; warehouseId?: number }) =>
    api.post<StockResult>('/inventory/stock-in', body).then((r) => r.data),

  stockOut: (body: { productId: number; quantity: number; reason: string; warehouseId?: number }) =>
    api.post<StockResult>('/inventory/stock-out', body).then((r) => r.data),

  adjust: (body: { productId: number; actualQty: number; reason: string; warehouseId?: number }) =>
    api.post<StockResult>('/inventory/adjust', body).then((r) => r.data),

  movements: (q: MovementQuery) =>
    api
      .get<Page<MovementRow>>('/inventory/movements', {
        params: Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined && v !== '')),
      })
      .then((r) => r.data),

  summary: () => api.get<InventorySummary>('/inventory/summary').then((r) => r.data),

  lowStock: (limit = 20) =>
    api.get<{ count: number; items: LowStockItem[] }>('/inventory/low-stock', { params: { limit } }).then((r) => r.data),
}

/** 库存变化后，刷新所有可能受影响的数据。 */
export async function invalidateStock(queryClient: QueryClient, productId: number) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['products'] }),
    queryClient.invalidateQueries({ queryKey: ['product', productId] }),
    queryClient.invalidateQueries({ queryKey: ['product-movements', productId] }),
    queryClient.invalidateQueries({ queryKey: ['inventory'] }),
    queryClient.invalidateQueries({ queryKey: ['warehouses'] }),
    queryClient.invalidateQueries({ queryKey: ['warehouse-stocks'] }),
    queryClient.invalidateQueries({ queryKey: ['product-stocks'] }),
  ])
}
