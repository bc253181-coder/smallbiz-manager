import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/AuthProvider'
import { PLAN_LABELS, type Plan } from '@/features/auth/types'
import { api } from '@/lib/api'

import { tr } from '@/lib/i18n'
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
  | 'LABEL_PRINTING'
  | 'CUSTOMER_PRICES'
  | 'WAREHOUSES'
export type Period = 'TRIAL_MONTH' | 'MONTH' | 'YEAR' | 'LIFETIME'
export type PaymentStatus = 'AWAITING_PAYMENT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED'
export type PlanSource = 'ACTIVATION_CODE' | 'MANUAL_PAYMENT' | 'ONLINE_PAYMENT' | 'ADMIN_MANUAL'

export interface CurrentPlan {
  plan: Plan
  expiresAt: string | null
  trialUsed: boolean
  productCount: number
  maxProducts: number | null
  features: Record<Feature, boolean>
  /** 现在升级专业版时，标准版剩余时间可折算的专业版天数 */
  proUpgradeBonusDays: number
  /** 店铺属于高级永久会员（永久有效） */
  lifetime: boolean
  /** 当前账号的会员等级和普通会员用量（max 为 null 表示不限） */
  membership: MembershipUsage
}

export interface FreeLimits {
  maxStores: number
  maxProducts: number
  reportDays: number
  advancedTools: boolean
  advancedTemplates: boolean
  export: boolean
  advancedAnalytics: boolean
}

export interface MembershipUsage {
  membership: 'FREE' | 'LIFETIME'
  lifetimeSince: string | null
  storesUsed: number
  storesMax: number | null
  productsUsed: number
  productsMax: number | null
  reportDays: number | null
  freeLimits: FreeLimits
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
  /** 在线支付渠道（扫码付款时为空）。 */
  channel: string | null
  paidAt: string | null
  providerTxnId: string | null
  /** 继续付款的地址（只在待付款的在线付款单上有）。 */
  checkoutUrl: string | null
  anomaly: string | null
  anomalyResolvedAt: string | null
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
  CUSTOMERS: tr('客户管理'),
  SUPPLIERS: tr('供应商管理'),
  PURCHASING: tr('采购入库'),
  WHOLESALE_PRICE: tr('批发价开单'),
  FULL_REPORTS: tr('高级数据分析（趋势、排行、毛利）'),
  EXPORT: tr('导出 Excel / CSV'),
  RECEIVABLES: tr('应收账款（赊账、收款、对账单）'),
  STAFF_ACCOUNTS: tr('员工账号'),
  EXCEL_EXPORT: tr('导出 Excel 表格'),
  LABEL_PRINTING: tr('打印条码标签'),
  CUSTOMER_PRICES: tr('客户协议价'),
  WAREHOUSES: tr('多仓库与调拨'),
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
  LABEL_PRINTING: 'STANDARD',
  CUSTOMER_PRICES: 'PRO',
  WAREHOUSES: 'PRO',
}

export const PERIOD_LABELS: Record<Period, string> = {
  TRIAL_MONTH: tr('首月体验'),
  MONTH: tr('月付'),
  YEAR: tr('年付'),
  LIFETIME: tr('永久'),
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  AWAITING_PAYMENT: tr('待付款'),
  PENDING_REVIEW: tr('审核中'),
  APPROVED: tr('已开通'),
  REJECTED: tr('未通过'),
  EXPIRED: tr('已过期'),
  CANCELLED: tr('已取消'),
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
  ACTIVATION_CODE: tr('激活码'),
  MANUAL_PAYMENT: tr('扫码付款'),
  ONLINE_PAYMENT: tr('在线支付'),
  ADMIN_MANUAL: tr('平台调整'),
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
  onlineChannels: (currency: string) =>
    api.get<OnlineChannel[]>('/billing/online-channels', { params: { currency } }).then((r) => r.data),
  startOnline: (priceId: number, channel: string) =>
    api.post<{ order: PaymentOrder; checkoutUrl: string }>('/billing/online-payments', { priceId, channel }).then((r) => r.data),
}

export interface OnlineChannel {
  channel: string
  displayName: string
}

/** 支付渠道名称（后端名称为中文，这里统一翻译）。 */
export function channelLabel(channel: string | null): string {
  if (!channel) return ''
  return channel === 'MOCK' ? tr('模拟支付（测试）') : channel
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

/** 店铺的会员显示名称：没有到期时间的付费店铺 = 高级永久会员；以前的订阅显示标准版 / 专业版。 */
export function membershipLabel(plan: Plan, expiresAt: string | null | undefined): string {
  if (plan === 'FREE') return tr('普通会员')
  return expiresAt ? PLAN_LABELS[plan] : tr('高级永久会员')
}

/** 付款单 / 价格的名称。 */
export function orderTitle(o: { plan: Plan; period: Period }): string {
  return o.period === 'LIFETIME' ? tr('高级永久会员') : `${PLAN_LABELS[o.plan]} · ${PERIOD_LABELS[o.period]}`
}

/** 高级永久会员的价格（按店铺币种；没有该币种时用人民币）。 */
export function useLifetimePrice() {
  const { session } = useAuth()
  const currency = session?.currentTenant?.currency === 'MYR' ? 'MYR' : 'CNY'
  const q = useQuery({
    queryKey: ['billing', 'prices', currency],
    queryFn: () => billingApi.prices(currency),
    enabled: !!session?.currentTenant,
    staleTime: 300_000,
  })
  return q.data?.find((p) => p.period === 'LIFETIME') ?? null
}