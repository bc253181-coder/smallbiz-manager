import type { Plan } from '@/features/auth/types'
import type { FreeLimits, MethodView, PaymentOrder, PaymentStatus, PriceView } from '@/features/billing/api'
import { api } from '@/lib/api'

export interface AdminStats {
  tenantCount: number
  paidTenantCount: number
  newTenantsThisMonth: number
  pendingPayments: number
}

export interface TenantRow {
  id: number
  name: string
  plan: Plan
  rawPlan: Plan
  planExpiresAt: string | null
  trialUsed: boolean
  ownerEmail: string | null
  ownerName: string | null
  ownerId: number | null
  ownerMembership: 'FREE' | 'LIFETIME' | null
  createdAt: string
}

export interface BatchRow {
  id: number
  name: string
  plan: Plan
  durationDays: number
  trial: boolean
  lifetime: boolean
  quantity: number
  redeemed: number
  voided: number
  expiresAt: string | null
  createdAt: string
}

export interface BatchInput {
  name: string
  plan: Plan
  durationDays: number
  trial: boolean
  /** 高级永久会员激活码（忽略套餐和天数） */
  lifetime: boolean
  quantity: number
  expiresOn: string | null
}

export interface FaqRow {
  id: number
  category: string
  question: string
  keywords: string
  answer: string
  actionLabel: string | null
  actionPath: string | null
  quickReply: boolean
  sortOrder: number
  enabled: boolean
  helpfulCount: number
  unhelpfulCount: number
}

export type FaqInput = Omit<FaqRow, 'id' | 'helpfulCount' | 'unhelpfulCount'>

export interface UnmatchedRow {
  id: number
  queryText: string
  occurrences: number
  lastSeenAt: string
}

export interface AiUsage {
  calls: number
  inputTokens: number
  cacheReadTokens: number
  outputTokens: number
  estimatedUsd: number
}

export interface AiStats {
  enabled: boolean
  model: string
  dailyLimit: number
  perIpDailyLimit: number
  today: AiUsage
  month: AiUsage
}

export interface AiLogRow {
  id: number
  question: string
  answer: string | null
  status: 'ANSWERED' | 'HANDOFF' | 'INVALID'
  faqIds: number[]
  lang: string
  model: string | null
  inputTokens: number
  cacheReadTokens: number
  outputTokens: number
  latencyMs: number
  helpful: boolean | null
  flagged: boolean
  createdAt: string
}

export interface AiLogPage {
  items: AiLogRow[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface OnlineStatus {
  channels: { channel: string; displayName: string }[]
  lastReconcileAt: string | null
  lastChecked: number | null
  lastFixed: number | null
  openAnomalies: number
}

export interface DispensedCode {
  id: number
  code: string
  hint: string
}

export interface DispenserRow {
  id: number
  hint: string
  batchName: string
  state: 'UNUSED' | 'DISPENSED' | 'REDEEMED' | 'VOIDED'
  hasRaw: boolean
  orderRef: string | null
  dispensedAt: string | null
  dispensedByName: string | null
  redeemedAt: string | null
  redeemedTenantName: string | null
}

export interface DispenserStock {
  keyConfigured: boolean
  available: number
  lowStockThreshold: number
  low: boolean
}

export const adminApi = {
  dispenserStock: () => api.get<DispenserStock>('/admin/code-dispenser/stock').then((r) => r.data),
  dispense: (orderRef: string, quantity: number, confirmRepeat: boolean) =>
    api.post<DispensedCode[]>('/admin/code-dispenser/dispense', { orderRef, quantity, confirmRepeat }).then((r) => r.data),
  dispenserCodes: (q: string) => api.get<DispenserRow[]>('/admin/code-dispenser/codes', { params: { q: q || undefined } }).then((r) => r.data),
  revealCode: (id: number) => api.post<DispensedCode>(`/admin/code-dispenser/codes/${id}/reveal`).then((r) => r.data),
  voidOrder: (orderRef: string) =>
    api.post<{ voided: number; alreadyRedeemed: string[] }>('/admin/code-dispenser/void-order', { orderRef }).then((r) => r.data),
  stats: () => api.get<AdminStats>('/admin/stats').then((r) => r.data),
  tenants: (q: string) => api.get<TenantRow[]>('/admin/tenants', { params: { q: q || undefined } }).then((r) => r.data),
  setPlan: (id: number, body: { plan: Plan; expiresOn: string | null; note: string }) =>
    api.post<TenantRow>(`/admin/tenants/${id}/plan`, body).then((r) => r.data),

  batches: () => api.get<BatchRow[]>('/admin/code-batches').then((r) => r.data),
  membershipConfig: () => api.get<FreeLimits>('/admin/membership/config').then((r) => r.data),
  updateMembershipConfig: (body: FreeLimits) => api.put<FreeLimits>('/admin/membership/config', body).then((r) => r.data),
  setMembership: (userId: number, membership: 'FREE' | 'LIFETIME', note: string) =>
    api.post(`/admin/users/${userId}/membership`, { membership, note }).then((r) => r.data),
  createBatch: (body: BatchInput) =>
    api.post<{ batch: BatchRow; codes: string[] }>('/admin/code-batches', body).then((r) => r.data),
  voidCode: (code: string) => api.post<{ status: string; hint: string }>('/admin/codes/void', { code }).then((r) => r.data),

  payments: (status: PaymentStatus | '' | 'ANOMALY') =>
    api
      .get<PaymentOrder[]>('/admin/payment-orders', {
        params: status === 'ANOMALY' ? { anomaly: true } : { status: status || undefined },
      })
      .then((r) => r.data),
  resolveAnomaly: (id: number) => api.post<PaymentOrder>(`/admin/payment-orders/${id}/resolve-anomaly`).then((r) => r.data),
  onlineStatus: () => api.get<OnlineStatus>('/admin/online-payments/status').then((r) => r.data),
  reconcileNow: () => api.post<OnlineStatus>('/admin/online-payments/reconcile').then((r) => r.data),
  approve: (id: number) => api.post<PaymentOrder>(`/admin/payment-orders/${id}/approve`).then((r) => r.data),
  reject: (id: number, reason: string) =>
    api.post<PaymentOrder>(`/admin/payment-orders/${id}/reject`, { reason }).then((r) => r.data),

  prices: () => api.get<PriceView[]>('/admin/plan-prices').then((r) => r.data),
  updatePrice: (id: number, body: { amount: string; enabled: boolean }) =>
    api.put<PriceView>(`/admin/plan-prices/${id}`, body).then((r) => r.data),
  methods: () => api.get<MethodView[]>('/admin/payment-methods').then((r) => r.data),
  updateMethod: (id: number, body: { displayName: string; payeeName: string; instructions: string; enabled: boolean }) =>
    api.put<MethodView>(`/admin/payment-methods/${id}`, body).then((r) => r.data),
  uploadQr: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<MethodView>(`/admin/payment-methods/${id}/qr`, form).then((r) => r.data)
  },

  faqs: () => api.get<FaqRow[]>('/admin/support/faqs').then((r) => r.data),
  createFaq: (body: FaqInput) => api.post<FaqRow>('/admin/support/faqs', body).then((r) => r.data),
  updateFaq: (id: number, body: FaqInput) => api.put<FaqRow>(`/admin/support/faqs/${id}`, body).then((r) => r.data),
  deleteFaq: (id: number) => api.delete(`/admin/support/faqs/${id}`),
  unmatched: () => api.get<UnmatchedRow[]>('/admin/support/unmatched').then((r) => r.data),
  resolveUnmatched: (id: number) => api.post(`/admin/support/unmatched/${id}/resolve`),
  aiStats: () => api.get<AiStats>('/admin/support/ai/stats').then((r) => r.data),
  aiLogs: (flagged: boolean, page: number) =>
    api.get<AiLogPage>('/admin/support/ai/logs', { params: { flagged, page } }).then((r) => r.data),
  flagAiLog: (id: number, flagged: boolean) => api.post(`/admin/support/ai/logs/${id}/flag`, { flagged }),
}
