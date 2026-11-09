import type { Page } from '@/features/catalog/types'
import type { PaymentStatus } from '@/features/sales/api'
import { api } from '@/lib/api'

export type PaymentMethod = 'CASH' | 'WECHAT' | 'ALIPAY' | 'BANK' | 'TNG' | 'DUITNOW' | 'OTHER'

export const METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: '现金',
  WECHAT: '微信',
  ALIPAY: '支付宝',
  BANK: '银行转账',
  TNG: "Touch 'n Go",
  DUITNOW: 'DuitNow',
  OTHER: '其他',
}

export interface Aging {
  days0To30: string
  days31To60: string
  days61To90: string
  over90: string
}

export interface ReceivableSummary {
  totalOutstanding: string
  customerCount: number
  openOrderCount: number
  overdue30: string
  aging: Aging
  collectedThisMonth: string
}

export interface ReceivableCustomerRow {
  customerId: number
  customerName: string
  phone: string | null
  outstanding: string
  openOrderCount: number
  oldestOrderDate: string
  oldestDays: number
  aging: Aging
}

export interface OpenOrder {
  id: number
  orderNo: string
  orderDate: string
  totalAmount: string
  paidAmount: string
  outstanding: string
  paymentStatus: PaymentStatus
  ageDays: number
}

export interface CustomerReceivable {
  customerId: number
  customerName: string
  outstanding: string
  aging: Aging
  orders: OpenOrder[]
}

export interface CustomerPayment {
  id: number
  paymentNo: string
  customerId: number
  customerName: string | null
  amount: string
  method: PaymentMethod
  receivedAt: string
  notes: string | null
  status: 'VALID' | 'VOIDED'
  voidedAt: string | null
  voidedByName: string | null
  voidReason: string | null
  createdByName: string | null
  createdAt: string
  allocations: { salesOrderId: number; orderNo: string | null; orderDate: string | null; amount: string }[]
}

export interface PaymentInput {
  customerId: number
  amount: string
  method: PaymentMethod
  receivedAt: string | null
  notes: string
  salesOrderId: number | null
}

export interface StatementLine {
  date: string
  type: 'SALE' | 'PAYMENT'
  refId: number
  docNo: string
  description: string
  amount: string | null
  paidAtSale: string | null
  debit: string
  credit: string
  balance: string
}

export interface Statement {
  customerId: number
  customerName: string
  customerPhone: string | null
  storeName: string
  storePhone: string | null
  storeAddress: string | null
  currency: string
  from: string
  to: string
  openingBalance: string
  totalSales: string
  totalDebit: string
  totalCredit: string
  closingBalance: string
  lines: StatementLine[]
  generatedAt: string
}

const clean = (q: object) => Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined && v !== '' && v !== null))

export const receivableApi = {
  summary: () => api.get<ReceivableSummary>('/receivables/summary').then((r) => r.data),
  customers: (q: { q?: string; minAgeDays?: number }) =>
    api.get<ReceivableCustomerRow[]>('/receivables/customers', { params: clean(q) }).then((r) => r.data),
  customer: (id: number) => api.get<CustomerReceivable>(`/receivables/customers/${id}`).then((r) => r.data),
  receive: (body: PaymentInput) => api.post<CustomerPayment>('/customer-payments', body).then((r) => r.data),
  payments: (q: { customerId?: number; status?: string; from?: string; to?: string; page?: number; size?: number }) =>
    api.get<Page<CustomerPayment>>('/customer-payments', { params: clean(q) }).then((r) => r.data),
  voidPayment: (id: number, reason: string) =>
    api.post<CustomerPayment>(`/customer-payments/${id}/void`, { reason }).then((r) => r.data),
  statement: (customerId: number, from: string, to: string) =>
    api.get<Statement>(`/customers/${customerId}/statement`, { params: { from, to } }).then((r) => r.data),
}

/** 收款后需要刷新的缓存。 */
export const RECEIVABLE_QUERY_KEYS = [['receivables'], ['customer-payments'], ['sale'], ['sales'], ['customer-receivable']]
