import { api } from '@/lib/api'
import { downloadFile } from '@/lib/download'

export type GroupBy = 'DAY' | 'MONTH'

export interface DashboardSummary {
  todaySales: string
  todayOrders: number
  monthSales: string
  monthOrders: number
  /** 店员为 null */
  monthGrossProfit: string | null
  monthPurchases: string | null
  today: string
}

export interface SalesPoint {
  period: string
  orderCount: number
  amount: string
  cost: string | null
  grossProfit: string | null
}

export interface SalesReport {
  from: string
  to: string
  groupBy: GroupBy
  points: SalesPoint[]
  total: SalesPoint
}

export interface PurchasePoint {
  period: string
  orderCount: number
  amount: string
}

export interface PurchaseReport {
  from: string
  to: string
  groupBy: GroupBy
  points: PurchasePoint[]
  total: PurchasePoint
}

export interface ProductRank {
  productId: number
  productName: string
  sku: string
  unit: string
  quantity: number
  amount: string
  grossProfit: string
}

export interface CustomerRank {
  customerId: number
  customerName: string
  orderCount: number
  amount: string
}

export interface CategoryStock {
  categoryId: number | null
  categoryName: string
  productCount: number
  quantity: number
  value: string
}

export interface RangeQuery {
  from: string
  to: string
}

export const reportApi = {
  dashboard: () => api.get<DashboardSummary>('/dashboard/summary').then((r) => r.data),
  trend: (days = 30) => api.get<SalesReport>('/dashboard/sales-trend', { params: { days } }).then((r) => r.data),
  sales: (q: RangeQuery & { groupBy: GroupBy }) => api.get<SalesReport>('/reports/sales', { params: q }).then((r) => r.data),
  purchases: (q: RangeQuery & { groupBy: GroupBy }) =>
    api.get<PurchaseReport>('/reports/purchases', { params: q }).then((r) => r.data),
  topProducts: (q: RangeQuery & { by: 'amount' | 'qty' }) =>
    api.get<ProductRank[]>('/reports/top-products', { params: { ...q, limit: 20 } }).then((r) => r.data),
  topCustomers: (q: RangeQuery) =>
    api.get<CustomerRank[]>('/reports/top-customers', { params: { ...q, limit: 20 } }).then((r) => r.data),
  inventory: () => api.get<CategoryStock[]>('/reports/inventory').then((r) => r.data),
}

/** 下载 CSV（需要带登录令牌，因此用 axios 取回再触发浏览器下载）。 */
export async function downloadCsv(path: string, params: Record<string, string>, fallbackName: string) {
  await downloadFile(path, params, fallbackName)
}
