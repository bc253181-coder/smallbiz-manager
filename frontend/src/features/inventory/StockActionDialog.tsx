import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { useEffect, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Field, FormError, Input, Select } from '@/components/ui/form'
import { warehouseApi, type Warehouse } from '@/features/warehouse/api'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { cn } from '@/lib/utils'
import { inventoryApi, invalidateStock } from './api'

import { tr } from '@/lib/i18n'
import { warehouseName } from '@/lib/systemText'
export type StockMode = 'in' | 'out' | 'adjust'

export interface StockTarget {
  id: number
  name: string
  sku: string
  unit: string
  stockQty: number
}

const CONFIG: Record<StockMode, { title: string; submit: string; reasons: string[]; qtyLabel: string }> = {
  in: { title: tr('入库'), submit: tr('确认入库'), reasons: [tr('补货'), tr('退货入库'), tr('调拨入库')], qtyLabel: tr('入库数量') },
  out: { title: tr('出库'), submit: tr('确认出库'), reasons: [tr('损耗'), tr('自用'), tr('退货给供应商'), tr('赠送')], qtyLabel: tr('出库数量') },
  adjust: { title: tr('盘点调整'), submit: tr('确认调整'), reasons: [tr('月底盘点'), tr('盘点差异'), tr('录入错误更正')], qtyLabel: tr('实际清点数量') },
}

export function StockActionDialog({
  target,
  mode,
  onClose,
  warehouses = [],
  initialWarehouseId,
}: {
  target: StockTarget | null
  mode: StockMode
  onClose: () => void
  /** 可操作的仓库；超过 1 个时显示仓库选择，数量按所选仓库计算 */
  warehouses?: Warehouse[]
  initialWarehouseId?: number
}) {
  const open = target !== null
  const queryClient = useQueryClient()
  const multi = warehouses.length > 1
  const [warehouseId, setWarehouseId] = useState<number | undefined>(initialWarehouseId)
  const wid = warehouseId ?? warehouses[0]?.id
  const warehouseQty = useQuery({
    queryKey: ['warehouse-stocks', wid, target?.id],
    queryFn: () => warehouseApi.stocks(wid!, [target!.id]),
    enabled: multi && !!target && !!wid,
  })
  const cfg = CONFIG[mode]
  const [qty, setQty] = useState('')
  const [unitCost, setUnitCost] = useState('')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // 多仓库时「当前库存」是所选仓库的数量
  const current = !target ? 0 : multi ? (warehouseQty.data?.get(target.id) ?? null) : target.stockQty

  useEffect(() => {
    if (open) {
      setWarehouseId(initialWarehouseId)
      setQty(mode === 'adjust' && target && !multi ? String(target.stockQty) : '')
      setUnitCost('')
      setReason(mode === 'in' ? tr('补货') : '')
      setErrors({})
      setFormError(null)
    }
  }, [open, mode, target]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!target) return null

  const n = /^\d+$/.test(qty) ? Number(qty) : null
  const after = n === null || current === null ? null : mode === 'in' ? current + n : mode === 'out' ? current - n : n

  function validate(): boolean {
    const e: Record<string, string> = {}
    if (n === null) e.qty = tr('请填写 0 或正整数')
    else if (mode !== 'adjust' && n < 1) e.qty = tr('数量至少为 1')
    else if (n > 1_000_000_000) e.qty = tr('数量过大')
    else if (mode === 'adjust' && current !== null && n === current) e.qty = tr('与当前库存相同，无需调整')
    if (mode === 'in' && unitCost && !/^\d{1,12}(\.\d{1,2})?$/.test(unitCost.trim())) e.unitCost = tr('单价格式不正确（最多两位小数）')
    if (mode !== 'in' && !reason.trim()) e.reason = mode === 'out' ? tr('请填写出库原因') : tr('请填写调整原因')
    if (reason.length > 200) e.reason = tr('最多 200 字')
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function submit(ev: FormEvent) {
    ev.preventDefault()
    if (!target || !validate() || n === null) return
    setSaving(true)
    setFormError(null)
    try {
      const w = multi ? wid : undefined
      const r =
        mode === 'in'
          ? await inventoryApi.stockIn({ productId: target.id, quantity: n, unitCost: unitCost.trim() || null, reason: reason.trim(), warehouseId: w })
          : mode === 'out'
            ? await inventoryApi.stockOut({ productId: target.id, quantity: n, reason: reason.trim(), warehouseId: w })
            : await inventoryApi.adjust({ productId: target.id, actualQty: n, reason: reason.trim(), warehouseId: w })
      await invalidateStock(queryClient, target.id)
      const wname = warehouses.find((x) => x.id === r.warehouseId)?.name
      toast.success(
        multi
          ? tr('「{{name}}」{{title}}成功，{{wname}} 现有 {{warehouseQty}} {{unit}}（总库存 {{stockQty}}）', { name: target.name, title: cfg.title, wname: wname, warehouseQty: r.warehouseQty, unit: target.unit, stockQty: r.stockQty })
          : tr('「{{name}}」{{title}}成功，当前库存 {{stockQty}} {{unit}}', { name: target.name, title: cfg.title, stockQty: r.stockQty, unit: target.unit }),
      )
      onClose()
    } catch (e) {
      const f = fieldErrors(e)
      if (Object.keys(f).length) {
        setErrors({ qty: f.quantity ?? f.actualQty ?? '', unitCost: f.unitCost ?? '', reason: f.reason ?? '' })
      } else if (errorCode(e) === 'INSUFFICIENT_STOCK') {
        setFormError(tr('{{e}}：当前只有 {{v1}} {{unit}}。如需先卖后补货，可在「设置」中开启允许负库存。', { e: errorMessage(e), v1: current ?? 0, unit: target.unit }))
      } else {
        setFormError(errorMessage(e, tr('{{title}}失败', { title: cfg.title })))
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
            <Dialog.Close className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" aria-label={tr('关闭')} disabled={saving}>
              <X className="size-5" />
            </Dialog.Close>
          </div>

          <form onSubmit={submit} className="mt-5 space-y-4" noValidate>
            <FormError message={formError} />

            {multi && (
              <Field label={tr('仓库')} htmlFor="stock-warehouse">
                <Select id="stock-warehouse" value={wid ?? ''} onChange={(e) => setWarehouseId(Number(e.target.value))}>
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>
                      {warehouseName(w.name)}
                    </option>
                  ))}
                </Select>
              </Field>
            )}

            <div className="flex items-center justify-center gap-4 rounded-lg bg-slate-50 py-3 text-center">
              <div>
                <p className="text-xs text-slate-500">{multi ? tr('该仓库现有') : tr('当前库存')}</p>
                <p className="text-xl font-semibold tabular-nums">{current ?? '…'}</p>
              </div>
              <ArrowRight className="size-4 text-slate-400" aria-hidden />
              <div>
                <p className="text-xs text-slate-500">{tr('操作后')}</p>
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
              <Field label={tr('入库单价（选填）')} htmlFor="stock-cost" error={errors.unitCost} hint={tr('填写后按加权平均自动更新进货成本')}>
                <Input id="stock-cost" inputMode="decimal" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} invalid={!!errors.unitCost} />
              </Field>
            )}

            <Field label={mode === 'in' ? tr('备注') : tr('原因')} htmlFor="stock-reason" error={errors.reason} required={mode !== 'in'}>
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
                {tr('取消')}
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
