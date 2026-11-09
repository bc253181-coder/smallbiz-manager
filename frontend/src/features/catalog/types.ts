export type ProductStatus = 'ACTIVE' | 'INACTIVE'

export type MovementType =
  | 'INITIAL'
  | 'STOCK_IN'
  | 'STOCK_OUT'
  | 'ADJUSTMENT'
  | 'SALE'
  | 'SALE_CANCEL'
  | 'PURCHASE_RECEIVE'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'

export interface Product {
  id: number
  sku: string
  name: string
  description: string | null
  imageUrl: string | null
  unit: string
  barcode: string | null
  categoryId: number | null
  categoryName: string | null
  /** 店员看不到成本价时为 null */
  costPrice: string | null
  retailPrice: string
  wholesalePrice: string | null
  stockQty: number
  lowStockThreshold: number
  lowStock: boolean
  status: ProductStatus
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProductInput {
  name: string
  sku: string
  categoryId: number | null
  description: string
  unit: string
  barcode: string
  costPrice: string
  retailPrice: string
  wholesalePrice: string | null
  lowStockThreshold: number
  status: ProductStatus
}

export interface Movement {
  id: number
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

export interface CategoryNode {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  productCount: number
  children: CategoryNode[]
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export const STATUS_LABELS: Record<ProductStatus, string> = {
  ACTIVE: '在售',
  INACTIVE: '已下架',
}

export const MOVEMENT_LABELS: Record<MovementType, string> = {
  INITIAL: '初始库存',
  STOCK_IN: '入库',
  STOCK_OUT: '出库',
  ADJUSTMENT: '盘点调整',
  SALE: '销售',
  SALE_CANCEL: '销售取消',
  PURCHASE_RECEIVE: '采购收货',
  TRANSFER_OUT: '调拨调出',
  TRANSFER_IN: '调拨调入',
}

export const COMMON_UNITS = ['件', '个', '台', '套', '盒', '箱', '包', '双', '条', '瓶', '支', '袋', '张', '本', '米', '公斤']
