import { api } from '@/lib/api'
import type { CategoryNode, Movement, Page, Product, ProductInput, ProductStatus } from './types'

export interface ProductQuery {
  q?: string
  categoryId?: number
  status?: ProductStatus
  lowStock?: boolean
  page?: number
  size?: number
  sort?: string
}

function clean<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== '' && v !== null && v !== false),
  ) as Partial<T>
}

export const productApi = {
  list: (query: ProductQuery) => api.get<Page<Product>>('/products', { params: clean(query) }).then((r) => r.data),

  get: (id: number) => api.get<Product>(`/products/${id}`).then((r) => r.data),

  movements: (id: number, page = 0) =>
    api.get<Page<Movement>>(`/products/${id}/movements`, { params: { page, size: 10 } }).then((r) => r.data),

  create: (body: ProductInput & { initialStock: number }) => api.post<Product>('/products', body).then((r) => r.data),

  update: (id: number, body: ProductInput & { version: number }) =>
    api.put<Product>(`/products/${id}`, body).then((r) => r.data),

  remove: (id: number) => api.delete(`/products/${id}`),

  uploadImage: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<Product>(`/products/${id}/image`, form).then((r) => r.data)
  },

  deleteImage: (id: number) => api.delete<Product>(`/products/${id}/image`).then((r) => r.data),
}

export const categoryApi = {
  tree: () => api.get<CategoryNode[]>('/categories').then((r) => r.data),
  create: (body: { name: string; parentId?: number | null }) =>
    api.post<CategoryNode>('/categories', body).then((r) => r.data),
  rename: (id: number, name: string) => api.put(`/categories/${id}`, { name }),
  remove: (id: number) => api.delete(`/categories/${id}`),
}

/** 把分类树展开为下拉选项（子分类缩进显示）。 */
export function flattenCategories(tree: CategoryNode[]): { id: number; label: string }[] {
  const out: { id: number; label: string }[] = []
  for (const root of tree) {
    out.push({ id: root.id, label: root.name })
    for (const child of root.children) {
      out.push({ id: child.id, label: `　└ ${child.name}` })
    }
  }
  return out
}
