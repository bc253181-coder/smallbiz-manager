export type MemberRole = 'OWNER' | 'ADMIN' | 'STAFF'
export type Plan = 'FREE' | 'STANDARD' | 'PRO'
export type BusinessType = 'WHOLESALE' | 'RETAIL' | 'TRADING' | 'ECOMMERCE' | 'OTHER'

export interface UserView {
  id: number
  email: string
  displayName: string
  phone: string | null
  platformAdmin: boolean
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
}

export const BUSINESS_TYPE_LABELS: Record<BusinessType, string> = {
  RETAIL: '零售店',
  WHOLESALE: '批发商',
  TRADING: '贸易公司',
  ECOMMERCE: '电商/网店',
  OTHER: '其他',
}

export const ROLE_LABELS: Record<MemberRole, string> = {
  OWNER: '店主',
  ADMIN: '管理员',
  STAFF: '店员',
}

export const PLAN_LABELS: Record<Plan, string> = {
  FREE: '免费版',
  STANDARD: '标准版',
  PRO: '专业版',
}
