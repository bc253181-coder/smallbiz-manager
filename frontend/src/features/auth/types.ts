import { tr } from '@/lib/i18n'
export type MemberRole = 'OWNER' | 'ADMIN' | 'STAFF'
export type Plan = 'FREE' | 'STANDARD' | 'PRO'
export type BusinessType = 'WHOLESALE' | 'RETAIL' | 'TRADING' | 'ECOMMERCE' | 'OTHER'

export interface UserView {
  id: number
  email: string
  displayName: string
  phone: string | null
  platformAdmin: boolean
  /** 界面语言（zh-CN / en），为空表示按浏览器语言 */
  locale: string | null
}

export interface TenantSummary {
  id: number
  name: string
  role: MemberRole
  /** false：员工所在店铺的专业版已到期，暂时不能进入 */
  accessible: boolean
}

export interface CurrentTenant {
  id: number
  name: string
  businessType: BusinessType
  role: MemberRole
  plan: Plan
  planExpiresAt: string | null
  currency: string
  timezone: string
}

export interface SessionResponse {
  accessToken: string | null
  expiresIn: number
  user: UserView
  currentTenant: CurrentTenant | null
  tenants: TenantSummary[]
}

export interface TenantView {
  id: number
  name: string
  businessType: BusinessType
  currency: string
  timezone: string
  allowNegativeStock: boolean
  plan: Plan
  planExpiresAt: string | null
  contactPhone: string | null
  address: string | null
  /** 已有业务数据且币种已确认：不能再改币种 */
  currencyLocked: boolean
  currencyConfirmed: boolean
}

export const BUSINESS_TYPE_LABELS: Record<BusinessType, string> = {
  RETAIL: tr('零售店'),
  WHOLESALE: tr('批发商'),
  TRADING: tr('贸易公司'),
  ECOMMERCE: tr('电商/网店'),
  OTHER: tr('其他'),
}

export const ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: tr('店主'),
  ADMIN: tr('管理员'),
  STAFF: tr('店员'),
}

export const PLAN_LABELS: Record<Plan, string> = {
  FREE: tr('普通会员'),
  STANDARD: tr('标准版'),
  PRO: tr('专业版'),
}
