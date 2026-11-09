import type { Plan } from '@/features/auth/types'
import type { MethodView, PaymentOrder, PaymentStatus, PriceView } from '@/features/billing/api'
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
  createdAt: string
}

export interface BatchRow {
  id: number
  name: string
  plan: Plan
  durationDays: number
  trial: boolean
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

export const adminApi = {
  stats: () => api.get<AdminStats>('/admin/stats').then((r) => r.data),
  tenants: (q: string) => api.get<TenantRow[]>('/admin/tenants', { params: { q: q || undefined } }).then((r) => r.data),
  setPlan: (id: number, body: { plan: Plan; expiresOn: string | null; note: string }) =>
    api.post<TenantRow>(`/admin/tenants/${id}/plan`, body).then((r) => r.data),

  batches: () => api.get<BatchRow[]>('/admin/code-batches').then((r) => r.data),
  createBatch: (body: BatchInput) =>
    api.post<{ batch: BatchRow; codes: string[] }>('/admin/code-batches', body).then((r) => r.data),
  voidCode: (code: string) => api.post<{ status: string; hint: string }>('/admin/codes/void', { code }).then((r) => r.data),

  payments: (status: PaymentStatus | '') =>
    api.get<PaymentOrder[]>('/admin/payment-orders', { params: { status: status || undefined } }).then((r) => r.data),
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
