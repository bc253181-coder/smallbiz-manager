import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Plus, Trash2 } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, FormError, Input, Select, Textarea } from '@/components/ui/form'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { productApi } from '@/features/catalog/api'
import { ProductPicker } from '@/features/catalog/components/ProductPicker'
import type { Product } from '@/features/catalog/types'
import { supplierApi } from '@/features/partners/api'
import { SupplierFormDialog } from '@/features/partners/SupplierFormDialog'
import { errorCode, errorMessage } from '@/lib/api'
import { useMoney } from '@/lib/format'
import { fromCents, isMoney, toCents } from '@/lib/money'
import { cn } from '@/lib/utils'
import { purchaseApi, type PurchaseDetail } from '../api'

interface Line {
  productId: number
  name: string
  sku: string
  unit: string
  stockQty: number
  quantity: string
  unitCost: string
}

export function PurchaseFormPage() {
  const { id } = useParams()
  const editing = id !== undefined
  const query = useQuery({ queryKey: ['purchase', Number(id)], queryFn: () => purchaseApi.get(Number(id)), enabled: editing })

  if (editing && query.isLoading) return <div className="h-96 animate-pulse rounded-xl bg-slate-100" />
  if (editing && (query.isError || !query.data)) {
    return <ErrorState onRetry={() => void query.refetch()} message={errorMessage(query.error, '采购单不存在')} />
  }
  if (editing && query.data && query.data.status !== 'DRAFT' && query.data.status !== 'ORDERED') {
    return <ErrorState onRetry={() => void query.refetch()} message="该采购单已收货或已取消，不能再修改" />
  }
  return <PurchaseForm order={query.data} />
}

function PurchaseForm({ order }: { order?: PurchaseDetail }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const money = useMoney()
  const [params] = useSearchParams()

  const [supplierId, setSupplierId] = useState<string>(order ? String(order.supplierId) : (params.get('supplierId') ?? ''))
  const [expectedDate, setExpectedDate] = useState(order?.expectedDate ?? '')
  const [notes, setNotes] = useState(order?.notes ?? '')
  const [lines, setLines] = useState<Line[]>(
    order?.items.map((i) => ({
      productId: i.productId,
      name: i.productName,
      sku: i.sku,
      unit: i.unit,
      stockQty: NaN,
      quantity: String(i.quantity),
      unitCost: i.unitCost,
    })) ?? [],
  )
  const [showErrors, setShowErrors] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState<'draft' | 'submit' | null>(null)
  const [newSupplier, setNewSupplier] = useState(false)
  const [addingLow, setAddingLow] = useState(false)

  const suppliers = useQuery({ queryKey: ['suppliers', 'all'], queryFn: () => supplierApi.list({ size: 100, sort: 'name,asc' }) })

  function addProduct(p: Product, quantity = 1) {
    setLines((ls) =>
      ls.some((l) => l.productId === p.id)
        ? // 再次扫到同一商品：数量 +1
          ls.map((l) => (l.productId === p.id ? { ...l, quantity: String((Number(l.quantity) || 0) + quantity) } : l))
        : [...ls, { productId: p.id, name: p.name, sku: p.sku, unit: p.unit, stockQty: p.stockQty, quantity: String(quantity), unitCost: p.costPrice ?? '0' }],
    )
  }

  async function addLowStock() {
    setAddingLow(true)
    try {
      const res = await productApi.list({ lowStock: true, status: 'ACTIVE', size: 100, sort: 'stockQty,asc' })
      const fresh = res.items.filter((p) => !lines.some((l) => l.productId === p.id))
      fresh.forEach((p) => addProduct(p, Math.max(p.lowStockThreshold * 2 - p.stockQty, 1)))
      toast[fresh.length ? 'success' : 'info'](fresh.length ? `已添加 ${fresh.length} 个低库存商品，建议数量可修改` : '没有需要补货的商品')
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setAddingLow(false)
    }
  }

  function updateLine(idx: number, patch: Partial<Line>) {
    setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, ...patch } : l)))
  }

  const lineErrors = lines.map((l) => ({
    quantity: /^\d+$/.test(l.quantity) && Number(l.quantity) >= 1 && Number(l.quantity) <= 1_000_000 ? null : '请填写 1 以上的整数',
    unitCost: isMoney(l.unitCost) ? null : '金额格式不正确',
  }))
  const totalCents = useMemo(
    () =>
      lines.reduce((sum, l) => {
        const c = toCents(l.unitCost)
        const q = /^\d+$/.test(l.quantity) ? Number(l.quantity) : 0
        return c === null ? sum : sum + c * q
      }, 0),
    [lines],
  )
  const totalQty = lines.reduce((s, l) => s + (/^\d+$/.test(l.quantity) ? Number(l.quantity) : 0), 0)

  async function save(submit: boolean, e?: FormEvent) {
    e?.preventDefault()
    setShowErrors(true)
    setFormError(null)
    if (!supplierId) return setFormError('请选择供应商')
    if (lines.length === 0) return setFormError('请至少添加一个商品')
    if (lineErrors.some((le) => le.quantity || le.unitCost)) return setFormError('请修正标红的数量或单价')
    if (notes.length > 500) return setFormError('备注最多 500 字')

    setSaving(submit ? 'submit' : 'draft')
    const body = {
      supplierId: Number(supplierId),
      expectedDate: expectedDate || null,
      notes,
      submit,
      items: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity), unitCost: l.unitCost.trim() })),
    }
    try {
      const saved = order ? await purchaseApi.update(order.id, { ...body, version: order.version }) : await purchaseApi.create(body)
      queryClient.setQueryData(['purchase', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['purchases'] })
      toast.success(saved.status === 'ORDERED' && (submit || order?.status === 'ORDERED') ? `采购单 ${saved.orderNo} 已下单` : `草稿 ${saved.orderNo} 已保存`)
      navigate(`/purchases/${saved.id}`, { replace: true })
    } catch (err) {
      const code = errorCode(err)
      setFormError(
        code === 'CONCURRENT_MODIFICATION'
          ? '这张采购单刚被其他人修改过，请返回详情页刷新后再修改。'
          : code === 'INVALID_STATUS'
            ? '采购单状态已变化（可能已收货或取消），不能再修改。'
            : errorMessage(err, '保存失败'),
      )
    } finally {
      setSaving(null)
    }
  }

  const ordered = order?.status === 'ORDERED'

  return (
    <div>
      <PageHeader
        title={order ? `编辑采购单 ${order.orderNo}` : '新建采购单'}
        back={order ? { to: `/purchases/${order.id}`, label: '返回采购单' } : { to: '/purchases', label: '返回采购列表' }}
      />
      {/* 在输入框中按回车：只保存（草稿 / 修改），不会意外下单 */}
      <form onSubmit={(e) => void save(false, e)} noValidate className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <div className="space-y-6 lg:col-span-2">
          <FormError message={formError} />
          <Card>
            <CardHeader
              title="采购商品"
              description={`${lines.length} 种，共 ${totalQty} 件`}
              action={
                <Button size="sm" variant="secondary" loading={addingLow} onClick={() => void addLowStock()}>
                  <AlertTriangle aria-hidden />
                  添加低库存商品
                </Button>
              }
            />
            <CardBody className="space-y-4">
              <ProductPicker
                onPick={(p) => addProduct(p)}
                excludeIds={lines.map((l) => l.productId)}
                renderMeta={(p) => (p.costPrice ? `成本 ${money(p.costPrice)}` : '')}
              />
              {lines.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 py-8 text-center text-sm text-slate-500">
                  搜索并添加要采购的商品，或点击「添加低库存商品」一键补货
                </p>
              ) : (
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                  {lines.map((l, idx) => {
                    const err = showErrors ? lineErrors[idx] : { quantity: null, unitCost: null }
                    const c = toCents(l.unitCost)
                    const sub = c !== null && /^\d+$/.test(l.quantity) ? fromCents(c * Number(l.quantity)) : null
                    return (
                      <li key={l.productId} className="grid grid-cols-[1fr_1fr_1fr_auto] items-start gap-x-3 gap-y-2 p-3">
                        <div className="col-span-full min-w-0">
                          <p className="truncate text-sm font-medium text-slate-900">{l.name}</p>
                          <p className="text-xs text-slate-500">
                            {l.sku}
                            {!Number.isNaN(l.stockQty) && ` · 现有库存 ${l.stockQty} ${l.unit}`}
                          </p>
                        </div>
                        <label className="text-xs text-slate-500">
                          数量（{l.unit}）
                          <Input
                            inputMode="numeric"
                            value={l.quantity}
                            onChange={(e) => updateLine(idx, { quantity: e.target.value.trim() })}
                            invalid={!!err.quantity}
                            className="mt-1 h-9"
                            aria-label={`${l.name} 数量`}
                          />
                          {err.quantity && <span className="mt-1 block text-red-600">{err.quantity}</span>}
                        </label>
                        <label className="text-xs text-slate-500">
                          采购单价
                          <Input
                            inputMode="decimal"
                            value={l.unitCost}
                            onChange={(e) => updateLine(idx, { unitCost: e.target.value })}
                            invalid={!!err.unitCost}
                            className="mt-1 h-9"
                            aria-label={`${l.name} 单价`}
                          />
                          {err.unitCost && <span className="mt-1 block text-red-600">{err.unitCost}</span>}
                        </label>
                        <div className="text-xs text-slate-500">
                          小计
                          <p className="mt-1 flex h-9 items-center text-sm font-medium tabular-nums text-slate-900">{sub ? money(sub) : '—'}</p>
                        </div>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="mt-5 size-9 hover:text-red-600"
                          aria-label={`移除 ${l.name}`}
                          onClick={() => setLines((ls) => ls.filter((_, i) => i !== idx))}
                        >
                          <Trash2 />
                        </Button>
                      </li>
                    )
                  })}
                </ul>
              )}
              <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-3">
                <span className="text-sm text-slate-500">合计</span>
                <span className="text-xl font-semibold tabular-nums">{money(fromCents(totalCents))}</span>
              </div>
            </CardBody>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader title="采购信息" />
            <CardBody className="space-y-4">
              <Field label="供应商" htmlFor="po-supplier" required error={showErrors && !supplierId ? '请选择供应商' : undefined}>
                <Select id="po-supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)} invalid={showErrors && !supplierId}>
                  <option value="">请选择供应商</option>
                  {suppliers.data?.items.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </Field>
              <button type="button" onClick={() => setNewSupplier(true)} className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
                <Plus className="size-4" aria-hidden />
                新增供应商
              </button>
              <Field label="预计到货日期" htmlFor="po-expected">
                <Input id="po-expected" type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} />
              </Field>
              <Field label="备注" htmlFor="po-notes" error={notes.length > 500 ? '最多 500 字' : undefined}>
                <Textarea id="po-notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="例如：顺丰到付、月底结算" />
              </Field>
            </CardBody>
          </Card>

          <div className={cn('flex gap-2 lg:flex-col')}>
            {ordered ? (
              <Button size="lg" className="flex-1" loading={saving !== null} onClick={() => void save(false)}>
                保存修改
              </Button>
            ) : (
              <>
                <Button size="lg" className="flex-1" loading={saving === 'submit'} disabled={saving !== null} onClick={() => void save(true)}>
                  保存并下单
                </Button>
                <Button size="lg" variant="secondary" className="flex-1" loading={saving === 'draft'} disabled={saving !== null} onClick={() => void save(false)}>
                  保存草稿
                </Button>
              </>
            )}
          </div>
          <p className="text-xs text-slate-500">下单后不会立即增加库存，到货时在采购单上点「确认收货」才会入库。</p>
        </div>
      </form>

      <SupplierFormDialog
        open={newSupplier}
        onClose={() => setNewSupplier(false)}
        onSaved={async (s) => {
          await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
          setSupplierId(String(s.id))
        }}
      />
    </div>
  )
}
