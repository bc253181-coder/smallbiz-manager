import { tr } from '@/lib/i18n'
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
  ACTIVE: tr('在售'),
  INACTIVE: tr('已下架'),
}

export const MOVEMENT_LABELS: Record<MovementType, string> = {
  INITIAL: tr('初始库存'),
  STOCK_IN: tr('入库'),
  STOCK_OUT: tr('出库'),
  ADJUSTMENT: tr('盘点调整'),
  SALE: tr('销售'),
  SALE_CANCEL: tr('销售取消'),
  PURCHASE_RECEIVE: tr('采购收货'),
  TRANSFER_OUT: tr('调拨调出'),
  TRANSFER_IN: tr('调拨调入'),
}

export const COMMON_UNITS = [tr('件'), tr('个'), tr('台'), tr('套'), tr('盒'), tr('箱'), tr('包'), tr('双'), tr('条'), tr('瓶'), tr('支'), tr('袋'), tr('张'), tr('本'), tr('米'), tr('公斤')]
