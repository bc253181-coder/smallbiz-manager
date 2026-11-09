import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/AuthProvider'
import type { Page } from '@/features/catalog/types'
import { api } from '@/lib/api'

export interface Warehouse {
  id: number
  name: string
  address: string | null
  notes: string | null
  isDefault: boolean
  active: boolean
  /** 当前套餐下能否在这里开单 / 入库 / 盘点（专业版到期后非默认仓库只能调出） */
  usable: boolean
  productCount: number
  totalQty: number
  /** 店员为 null */
  stockValue: string | null
  version: number
}

export interface WarehouseList {
  warehouses: Warehouse[]
  max: number
  allowed: boolean
}

export interface StockLine {
  warehouseId: number
  warehouseName: string
  qty: number
}

export interface TransferItem {
  productId: number
  productName: string
  sku: string
  unit: string
  quantity: number
}

export interface Transfer {
  id: number
  transferNo: string
  fromWarehouseId: number
  fromWarehouseName: string
  toWarehouseId: number
  toWarehouseName: string
  notes: string | null
  itemCount: number
  totalQuantity: number
  createdByName: string | null
  createdAt: string
  items: TransferItem[]
}

export const warehouseApi = {
  list: () => api.get<WarehouseList>('/warehouses').then((r) => r.data),
  create: (body: { name: string; address: string; notes: string }) =>
    api.post<{ id: number }>('/warehouses', body).then((r) => r.data),
  update: (id: number, body: { name: string; address: string; notes: string }) => api.put(`/warehouses/${id}`, body),
  deactivate: (id: number) => api.post(`/warehouses/${id}/deactivate`),
  /** 某仓库中这些商品的库存：productId → 数量 */
  stocks: async (warehouseId: number, productIds: number[]) => {
    if (productIds.length === 0) return new Map<number, number>()
    const r = await api.get<{ productId: number; qty: number }[]>(`/warehouses/${warehouseId}/stocks`, {
      params: { productIds: productIds.join(',') },
    })
    return new Map(r.data.map((x) => [x.productId, x.qty]))
  },
  productStocks: (productId: number) => api.get<StockLine[]>(`/products/${productId}/stocks`).then((r) => r.data),
  transfers: (q: { warehouseId?: number; page?: number; size?: number }) =>
    api
      .get<Page<Transfer>>('/stock-transfers', { params: Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined)) })
      .then((r) => r.data),
  transfer: (id: number) => api.get<Transfer>(`/stock-transfers/${id}`).then((r) => r.data),
  createTransfer: (body: { fromWarehouseId: number; toWarehouseId: number; notes: string; items: { productId: number; quantity: number }[] }) =>
    api.post<Transfer>('/stock-transfers', body).then((r) => r.data),
}

/**
 * 当前店铺的仓库。multi = 启用的仓库超过 1 个：只有这时页面才显示仓库选择，单仓库的店界面不变。
 */
export function useWarehouses() {
  const { session } = useAuth()
  const tenantId = session?.currentTenant?.id
  const q = useQuery({ queryKey: ['warehouses', tenantId], queryFn: warehouseApi.list, enabled: !!tenantId, staleTime: 30_000 })
  const all = q.data?.warehouses ?? []
  const active = all.filter((w) => w.active)
  return {
    ...q,
    all,
    active,
    /** 能开单 / 入库 / 盘点的仓库 */
    usable: active.filter((w) => w.usable),
    multi: active.length > 1,
    defaultWarehouse: all.find((w) => w.isDefault),
  }
}

/** 记住每家店上次开单用的仓库（只是方便，取不到就用默认仓库）。 */
export function lastWarehouseKey(tenantId: number | undefined) {
  return `sb:lastWarehouse:${tenantId ?? ''}`
}

export function readLastWarehouse(tenantId: number | undefined): number | null {
  try {
    const v = localStorage.getItem(lastWarehouseKey(tenantId))
    return v ? Number(v) : null
  } catch {
    return null
  }
}

export function saveLastWarehouse(tenantId: number | undefined, id: number) {
  try {
    localStorage.setItem(lastWarehouseKey(tenantId), String(id))
  } catch {
    // 无痕模式等：忽略
  }
}
