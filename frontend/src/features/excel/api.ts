import { api } from '@/lib/api'
import { downloadFile } from '@/lib/download'

export type ImportType = 'products' | 'customers' | 'suppliers'
export type ImportMode = 'CREATE_ONLY' | 'UPSERT'

export interface RowError {
  /** 表格行号（表头是第 1 行）；0 表示整个文件的问题 */
  row: number
  column: string | null
  message: string
}

export interface ImportResult {
  dryRun: boolean
  imported: boolean
  total: number
  created: number
  updated: number
  skipped: number
  errors: RowError[]
  newCategories: string[]
  notes: string[]
}

export const IMPORT_LABELS: Record<ImportType, string> = {
  products: '商品',
  customers: '客户',
  suppliers: '供应商',
}

/** 判断重复的依据（显示在导入弹窗中）。 */
export const DUPLICATE_RULES: Record<ImportType, string> = {
  products: '按 SKU 判断（没有 SKU 时按条码）',
  customers: '按电话判断（没有电话时按客户名称）',
  suppliers: '按供应商名称判断',
}

/** 导入后需要刷新的数据。 */
export const IMPORT_QUERY_KEYS: Record<ImportType, string[][]> = {
  products: [['products'], ['categories'], ['inventory'], ['billing']],
  customers: [['customers']],
  suppliers: [['suppliers']],
}

export const importApi = {
  template: (type: ImportType) => downloadFile(`/imports/${type}/template`, {}, `${IMPORT_LABELS[type]}导入模板.xlsx`),
  upload: (type: ImportType, file: File, mode: ImportMode, dryRun: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post<ImportResult>(`/imports/${type}`, form, { params: { mode, dryRun }, timeout: 120_000 })
      .then((r) => r.data)
  },
}
