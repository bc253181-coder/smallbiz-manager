import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/AuthProvider'
import type { Plan } from '@/features/auth/types'
import { api } from '@/lib/api'

export type Feature =
  | 'CUSTOMERS'
  | 'SUPPLIERS'
  | 'PURCHASING'
  | 'WHOLESALE_PRICE'
  | 'FULL_REPORTS'
  | 'EXPORT'
  | 'RECEIVABLES'
  | 'STAFF_ACCOUNTS'
  | 'EXCEL_EXPORT'
export type Period = 'TRIAL_MONTH' | 'MONTH' | 'YEAR'
export type PaymentStatus = 'AWAITING_PAYMENT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED'
export type PlanSource = 'ACTIVATION_CODE' | 'MANUAL_PAYMENT' | 'ADMIN_MANUAL'

export interface CurrentPlan {
  plan: Plan
  expiresAt: string | null
  trialUsed: boolean
  productCount: number
  maxProducts: number | null
  features: Record<Feature, boolean>
  /** 现在升级专业版时，标准版剩余时间可折算的专业版天数 */
  proUpgradeBonusDays: number
}

export interface PriceView {
  id: number
  plan: Plan
  period: Period
  durationDays: number
  currency: string
  amount: string
  enabled: boolean
}

export interface MethodView {
  id: number
  code: string
  displayName: string
  currency: string
  qrImageUrl: string | null
  payeeName: string | null
  instructions: string | null
  enabled: boolean
}

export interface PaymentOrder {
  id: number
  orderNo: string
  referenceCode: string
  plan: Plan
  period: Period
  durationDays: number
  currency: string
  amount: string
  status: PaymentStatus
  method: MethodView | null
  expiresAt: string
  submittedAt: string | null
  reviewedAt: string | null
  rejectReason: string | null
  transactionRef: string | null
  payerNote: string | null
  hasProof: boolean
  createdAt: string
  tenantId: number
  tenantName: string | null
}

export interface PlanLog {
  id: number
  source: PlanSource
  oldPlan: Plan
  newPlan: Plan
  oldExpiresAt: string | null
  newExpiresAt: string | null
  note: string | null
  createdAt: string
}

export const FEATURE_LABELS: Record<Feature, string> = {
  CUSTOMERS: '客户管理',
  SUPPLIERS: '供应商管理',
  PURCHASING: '采购入库',
  WHOLESALE_PRICE: '批发价开单',
  FULL_REPORTS: '完整报表（不限时间范围）',
  EXPORT: '导出 Excel / CSV',
  RECEIVABLES: '应收账款（赊账、收款、对账单）',
  STAFF_ACCOUNTS: '员工账号',
  EXCEL_EXPORT: '导出 Excel 表格',
}

/** 开通各功能所需的最低套餐（与后端 PlanPolicy 一致）。 */
export const FEATURE_PLAN: Record<Feature, Plan> = {
  CUSTOMERS: 'STANDARD',
  SUPPLIERS: 'STANDARD',
  PURCHASING: 'STANDARD',
  WHOLESALE_PRICE: 'STANDARD',
  FULL_REPORTS: 'STANDARD',
  EXPORT: 'STANDARD',
  RECEIVABLES: 'PRO',
  STAFF_ACCOUNTS: 'PRO',
  EXCEL_EXPORT: 'PRO',
}

export const PERIOD_LABELS: Record<Period, string> = {
  TRIAL_MONTH: '首月体验',
  MONTH: '月付',
  YEAR: '年付',
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  AWAITING_PAYMENT: '待付款',
  PENDING_REVIEW: '审核中',
  APPROVED: '已开通',
  REJECTED: '未通过',
  EXPIRED: '已过期',
  CANCELLED: '已取消',
}

export const PAYMENT_STATUS_TONES: Record<PaymentStatus, 'slate' | 'blue' | 'green' | 'amber' | 'red'> = {
  AWAITING_PAYMENT: 'amber',
  PENDING_REVIEW: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  EXPIRED: 'slate',
  CANCELLED: 'slate',
}

export const SOURCE_LABELS: Record<PlanSource, string> = {
  ACTIVATION_CODE: '激活码',
  MANUAL_PAYMENT: '扫码付款',
  ADMIN_MANUAL: '平台调整',
}

export const billingApi = {
  current: () => api.get<CurrentPlan>('/billing/current').then((r) => r.data),
  prices: (currency: string) => api.get<PriceView[]>('/billing/prices', { params: { currency } }).then((r) => r.data),
  methods: () => api.get<MethodView[]>('/billing/payment-methods').then((r) => r.data),
  history: () => api.get<PlanLog[]>('/billing/history').then((r) => r.data),
  redeem: (code: string) => api.post<CurrentPlan>('/billing/redeem', { code }).then((r) => r.data),
  createPayment: (priceId: number, paymentMethodId: number) =>
    api.post<PaymentOrder>('/billing/payment-orders', { priceId, paymentMethodId }).then((r) => r.data),
  payments: () => api.get<PaymentOrder[]>('/billing/payment-orders').then((r) => r.data),
  payment: (id: number) => api.get<PaymentOrder>(`/billing/payment-orders/${id}`).then((r) => r.data),
  submitProof: (id: number, file: File, transactionRef: string, note: string) => {
    const form = new FormData()
    form.append('file', file)
    if (transactionRef.trim()) form.append('transactionRef', transactionRef.trim())
    if (note.trim()) form.append('note', note.trim())
    return api.post<PaymentOrder>(`/billing/payment-orders/${id}/proof`, form).then((r) => r.data)
  },
  cancel: (id: number) => api.post<PaymentOrder>(`/billing/payment-orders/${id}/cancel`).then((r) => r.data),
}

/** 需要登录才能读取的图片（付款截图）→ 对象 URL。 */
export async function fetchImageBlob(path: string): Promise<string> {
  const res = await api.get<Blob>(path, { responseType: 'blob' })
  return URL.createObjectURL(res.data)
}

/** 当前店铺的有效套餐（以服务器为准，套餐到期会自动回到免费版）。 */
export function usePlan() {
  const { session } = useAuth()
  const tenantId = session?.currentTenant?.id
  return useQuery({
    queryKey: ['billing', 'current', tenantId],
    queryFn: billingApi.current,
    enabled: !!tenantId,
    staleTime: 60_000,
  })
}

/**
 * 某功能当前是否可用。套餐信息还没加载出来时视为可用（避免闪烁"已锁定"），
 * 真正的限制由后端保证。
 */
export function useFeature(feature: Feature): boolean {
  const { data } = usePlan()
  return data ? data.features[feature] : true
}
