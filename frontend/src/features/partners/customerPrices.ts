import { api } from '@/lib/api'

export interface CustomerPrice {
  productId: number
  productName: string
  sku: string
  barcode: string | null
  unit: string
  retailPrice: string
  wholesalePrice: string | null
  costPrice: string
  price: string
  notes: string | null
  /** 协议价低于当前进货成本 */
  belowCost: boolean
  updatedAt: string
}

export interface ProductCustomerPrice {
  customerId: number
  customerName: string
  phone: string | null
  price: string
  notes: string | null
  belowCost: boolean
  updatedAt: string
}

export const customerPriceApi = {
  list: (customerId: number, q?: string) =>
    api.get<CustomerPrice[]>(`/customers/${customerId}/prices`, { params: q ? { q } : {} }).then((r) => r.data),
  set: (customerId: number, productId: number, price: string, notes: string) =>
    api.put<CustomerPrice>(`/customers/${customerId}/prices/${productId}`, { price, notes }).then((r) => r.data),
  remove: (customerId: number, productId: number) => api.delete(`/customers/${customerId}/prices/${productId}`),
  /** 开单取价：只返回有协议价的商品；店铺不是专业版时为空 */
  quote: (customerId: number, productIds: number[]) =>
    api
      .get<{ productId: number; price: string }[]>(`/customers/${customerId}/prices/quote`, {
        params: { productIds: productIds.join(',') },
      })
      .then((r) => r.data),
  byProduct: (productId: number) =>
    api.get<ProductCustomerPrice[]>(`/products/${productId}/customer-prices`).then((r) => r.data),
}
