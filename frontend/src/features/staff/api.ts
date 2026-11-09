import { api } from '@/lib/api'
import type { MemberRole } from '@/features/auth/types'

import { tr } from '@/lib/i18n'
export type AssignableRole = Exclude<MemberRole, 'OWNER'>

export interface Member {
  id: number
  userId: number
  displayName: string
  email: string
  role: MemberRole
  joinedAt: string
  lastLoginAt: string | null
  self: boolean
}

export interface Invitation {
  id: number
  role: AssignableRole
  note: string | null
  expiresAt: string
  createdAt: string
}

export interface MembersResponse {
  members: Member[]
  invitations: Invitation[]
  /** 在职员工 + 待接受的邀请 */
  used: number
  max: number
  /** 当前套餐能否邀请员工 */
  allowed: boolean
}

export interface InvitationPreview {
  tenantName: string
  role: AssignableRole
  inviterName: string
  expiresAt: string
}

/** 各角色能做什么（显示在邀请弹窗和员工列表中）。 */
export const ROLE_DESCRIPTIONS: Record<AssignableRole, string> = {
  STAFF: tr('开单、查看商品和库存、登记客户和收款；看不到进货成本和利润，不能取消订单、不能采购'),
  ADMIN: tr('除店铺设置、员工管理、套餐续费外的全部操作，包括看成本利润、采购、取消订单'),
}

export function inviteLink(token: string) {
  return `${window.location.origin}/invite/${token}`
}

export const staffApi = {
  list: () => api.get<MembersResponse>('/members').then((r) => r.data),
  invite: (role: AssignableRole, note: string) =>
    api.post<{ invitation: Invitation; token: string }>('/invitations', { role, note }).then((r) => r.data),
  revoke: (id: number) => api.post(`/invitations/${id}/revoke`),
  changeRole: (id: number, role: AssignableRole) => api.put<Member>(`/members/${id}/role`, { role }).then((r) => r.data),
  remove: (id: number) => api.post(`/members/${id}/remove`),
  preview: (token: string) =>
    api.get<InvitationPreview>('/invitations/preview', { params: { token } }).then((r) => r.data),
}
