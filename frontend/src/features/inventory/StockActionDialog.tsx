import { useQueryClient } from '@tanstack/react-query'
import { ArrowRight, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useEffect, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Field, FormError, Input } from '@/components/ui/form'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { cn } from '@/lib/utils'
import { inventoryApi, invalidateStock } from './api'

export type StockMode = 'in' | 'out' | 'adjust'

export interface StockTarget {
  id: number
  name: string
  sku: string
  unit: string
  stockQty: number
}

const CONFIG: Record<StockMode, { title: string; submit: string; reasons: string[]; qtyLabel: string }> = {
  in: { title: '入库', submit: '确认入库', reasons: ['补货', '退货入库', '调拨入库'], qtyLabel: '入库数量' },
  out: { title: '出库', submit: '确认出库', reasons: ['损耗', '自用', '退货给供应商', '赠送'], qtyLabel: '出库数量' },
  adjust: { title: '盘点调整', submit: '确认调整', reasons: ['月底盘点', '盘点差异', '录入错误更正'], qtyLabel: '实际清点数量' },
}

export function StockActionDialog({
  target,
  mode,
  onClose,
}: {
  target: StockTarget | null
  mode: StockMode
  onClose: () => void
}) {
  const open = target !== null
  const queryClient = useQueryClient()
  const cfg = CONFIG[mode]
  const [qty, setQty] = useState('')
  const [unitCost, setUnitCost] = useState('')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setQty(mode === 'adjust' && target ? String(target.stockQty) : '')
      setUnitCost('')
      setReason(mode === 'in' ? '补货' : '')
      setErrors({})
      setFormError(null)
    }
  }, [open, mode, target])

  if (!target) return null

  const n = /^\d+$/.test(qty) ? Number(qty) : null
  const after = n === null ? null : mode === 'in' ? target.stockQty + n : mode === 'out' ? target.stockQty - n : n

  function validate(): boolean {
    const e: Record<string, string> = {}
    if (n === null) e.qty = '请填写 0 或正整数'
    else if (mode !== 'adjust' && n < 1) e.qty = '数量至少为 1'
    else if (n > 1_000_000_000) e.qty = '数量过大'
    else if (mode === 'adjust' && target && n === target.stockQty) e.qty = '与当前库存相同，无需调整'
    if (mode === 'in' && unitCost && !/^\d{1,12}(\.\d{1,2})?$/.test(unitCost.trim())) e.unitCost = '单价格式不正确（最多两位小数）'
    if (mode !== 'in' && !reason.trim()) e.reason = mode === 'out' ? '请填写出库原因' : '请填写调整原因'
    if (reason.length > 200) e.reason = '最多 200 字'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault()
    if (!target || !validate() || n === null) return
    setSaving(true)
    setFormError(null)
    try {
      const r =
        mode === 'in'
          ? await inventoryApi.stockIn({ productId: target.id, quantity: n, unitCost: unitCost.trim() || null, reason: reason.trim() })
          : mode === 'out'
            ? await inventoryApi.stockOut({ productId: target.id, quantity: n, reason: reason.trim() })
            : await inventoryApi.adjust({ productId: target.id, actualQty: n, reason: reason.trim() })
      await invalidateStock(queryClient, target.id)
      toast.success(`「${target.name}」${cfg.title}成功，当前库存 ${r.stockQty} ${target.unit}`)
      onClose()
    } catch (e) {
      const f = fieldErrors(e)
      if (Object.keys(f).length) {
        setErrors({ qty: f.quantity ?? f.actualQty ?? '', unitCost: f.unitCost ?? '', reason: f.reason ?? '' })
      } else if (errorCode(e) === 'INSUFFICIENT_STOCK') {
        setFormError(`${errorMessage(e)}：当前只有 ${target.stockQty} ${target.unit}。如需先卖后补货，可在「设置」中开启允许负库存。`)
      } else {
        setFormError(errorMessage(e, `${cfg.title}失败`))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={(o) => !o && !saving && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-slate-900/40" />
        <Dialog.Content
          className="fixed inset-x-0 bottom-0 z-50 max-h-[90dvh] overflow-y-auto rounded-t-2xl bg-white p-5 shadow-xl sm:inset-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-md sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-xl sm:p-6"
          aria-describedby={undefined}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <Dialog.Title className="text-base font-semibold text-slate-900">{cfg.title}</Dialog.Title>
              <p className="mt-0.5 truncate text-sm text-slate-500">
                {target.name} · {target.sku}
              </p>
            </div>
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label="关闭" disabled={saving}>
              <X className="size-5" />
            </Dialog.Close>
          </div>

          <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
            <FormError message={formError} />

            <div className="flex items-center justify-center gap-4 rounded-lg bg-slate-50 py-3 text-center">
              <div>
                <p className="text-xs text-slate-500">当前库存</p>
                <p className="text-xl font-semibold tabular-nums">{target.stockQty}</p>
              </div>
              <ArrowRight className="size-4 text-slate-400" aria-hidden />
              <div>
                <p className="text-xs text-slate-500">操作后</p>
                <p className={cn('text-xl font-semibold tabular-nums', after !== null && after < 0 ? 'text-red-600' : 'text-blue-700')}>
                  {after ?? '—'}
                </p>
              </div>
              <span className="text-sm text-slate-500">{target.unit}</span>
            </div>

            <Field label={cfg.qtyLabel} htmlFor="stock-qty" error={errors.qty} required>
              <Input
                id="stock-qty"
                inputMode="numeric"
                autoFocus
                value={qty}
                onChange={(e) => setQty(e.target.value.trim())}
                invalid={!!errors.qty}
              />
            </Field>

            {mode === 'in' && (
              <Field label="入库单价（选填）" htmlFor="stock-cost" error={errors.unitCost} hint="填写后按加权平均自动更新进货成本">
                <Input id="stock-cost" inputMode="decimal" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} invalid={!!errors.unitCost} />
              </Field>
            )}

            <Field label={mode === 'in' ? '备注' : '原因'} htmlFor="stock-reason" error={errors.reason} required={mode !== 'in'}>
              <Input id="stock-reason" value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} invalid={!!errors.reason} />
            </Field>
            <div className="flex flex-wrap gap-2">
              {cfg.reasons.map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setReason(r)}
                  className={cn(
                    'rounded-full border px-3 py-1 text-xs',
                    reason === r ? 'border-blue-600 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50',
                  )}
                >
                  {r}
                </button>
              ))}
            </div>

            <div className="flex gap-2 pt-2">
              <Button variant="secondary" className="flex-1" onClick={onClose} disabled={saving}>
                取消
              </Button>
              <Button type="submit" className="flex-1" loading={saving}>
                {cfg.submit}
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
