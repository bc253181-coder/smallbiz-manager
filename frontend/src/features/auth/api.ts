import { api } from '@/lib/api'
import type { BusinessType, SessionResponse, TenantView, UserView } from './types'

export const authApi = {
  register: (body: { email: string; password: string; displayName: string; agreeTerms: boolean }) =>
    api.post<SessionResponse>('/auth/register', body).then((r) => r.data),

  login: (body: { email: string; password: string }) =>
    api.post<SessionResponse>('/auth/login', body).then((r) => r.data),

  logout: () => api.post('/auth/logout'),

  updateProfile: (body: { displayName: string; phone: string }) =>
    api.put<UserView>('/auth/me', body).then((r) => r.data),

  changePassword: (body: { oldPassword: string; newPassword: string }) => api.put('/auth/me/password', body),
}

export const tenantApi = {
  create: (body: { name: string; businessType: BusinessType }) =>
    api.post<SessionResponse>('/tenants', body).then((r) => r.data),

  switchTo: (tenantId: number) => api.post<SessionResponse>(`/tenants/${tenantId}/switch`).then((r) => r.data),

  current: () => api.get<TenantView>('/tenant').then((r) => r.data),

  update: (body: Omit<TenantView, 'id' | 'currency' | 'plan' | 'planExpiresAt'>) =>
    api.put<TenantView>('/tenant', body).then((r) => r.data),
}
