import { api } from '@/lib/api'
import type { Page } from '@/features/catalog/types'

export type CustomerType = 'INDIVIDUAL' | 'BUSINESS'
export type PriceTier = 'RETAIL' | 'WHOLESALE'

export interface Customer {
  id: number
  name: string
  phone: string | null
  email: string | null
  address: string | null
  customerType: CustomerType
  priceTier: PriceTier
  notes: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface CustomerInput {
  name: string
  phone: string
  email: string
  address: string
  customerType: CustomerType
  priceTier: PriceTier
  notes: string
}

export interface Supplier {
  id: number
  name: string
  contactPerson: string | null
  phone: string | null
  email: string | null
  address: string | null
  suppliedProductsNote: string | null
  notes: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface SupplierInput {
  name: string
  contactPerson: string
  phone: string
  email: string
  address: string
  suppliedProductsNote: string
  notes: string
}

export const CUSTOMER_TYPE_LABELS: Record<CustomerType, string> = { INDIVIDUAL: '个人', BUSINESS: '企业/商户' }
export const PRICE_TIER_LABELS: Record<PriceTier, string> = { RETAIL: '零售价', WHOLESALE: '批发价' }

function clean(params: object) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== ''))
}

export const customerApi = {
  list: (q: { q?: string; type?: CustomerType; priceTier?: PriceTier; page?: number; size?: number; sort?: string }) =>
    api.get<Page<Customer>>('/customers', { params: clean(q) }).then((r) => r.data),
  get: (id: number) => api.get<Customer>(`/customers/${id}`).then((r) => r.data),
  create: (body: CustomerInput) => api.post<Customer>('/customers', body).then((r) => r.data),
  update: (id: number, body: CustomerInput & { version: number }) =>
    api.put<Customer>(`/customers/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/customers/${id}`),
}

export const supplierApi = {
  list: (q: { q?: string; page?: number; size?: number; sort?: string }) =>
    api.get<Page<Supplier>>('/suppliers', { params: clean(q) }).then((r) => r.data),
  get: (id: number) => api.get<Supplier>(`/suppliers/${id}`).then((r) => r.data),
  create: (body: SupplierInput) => api.post<Supplier>('/suppliers', body).then((r) => r.data),
  update: (id: number, body: SupplierInput & { version: number }) =>
    api.put<Supplier>(`/suppliers/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/suppliers/${id}`),
}
