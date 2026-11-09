import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Pencil, Tags, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { useFeature } from '@/features/billing/api'
import { ProductPicker } from '@/features/catalog/components/ProductPicker'
import type { Product } from '@/features/catalog/types'
import { ImportButton } from '@/features/excel/ImportDialog'
import { errorMessage } from '@/lib/api'
import { useMoney } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { isMoney, toCents } from '@/lib/money'
import { customerPriceApi, type CustomerPrice } from './customerPrices'

/** 编辑中的协议价：来自已有行或刚选中的商品。 */
interface Editing {
  productId: number
  productName: string
  unit: string
  retailPrice: string
  wholesalePrice: string | null
  costPrice: string | null
  price: string
  notes: string
  isNew: boolean
}

/** 比参考价（批发价，没有批发价时用零售价）低多少。 */
function discountText(price: string, wholesale: string | null, retail: string) {
  const ref = toCents(wholesale ?? retail)
  const p = toCents(price)
  if (!ref || p === null || p >= ref) return null
  return `比${wholesale ? '批发价' : '零售价'}低 ${(((ref - p) / ref) * 100).toFixed(1)}%`
}

/** 客户详情页的「协议价」卡片（店主/管理员）。 */
export function CustomerPricesCard({ customerId, customerName }: { customerId: number; customerName: string }) {
  const canManage = useCanManage()
  const allowed = useFeature('CUSTOMER_PRICES')
  const money = useMoney()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Editing | null>(null)
  const [removing, setRemoving] = useState<CustomerPrice | null>(null)
  const [filter, setFilter] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['customer-prices', customerId],
    queryFn: () => customerPriceApi.list(customerId),
    enabled: canManage,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['customer-prices', customerId] })

  const save = useMutation({
    mutationFn: (e: Editing) => customerPriceApi.set(customerId, e.productId, e.price.trim(), e.notes),
    onSuccess: async (r) => {
      await refresh()
      await queryClient.invalidateQueries({ queryKey: ['product-customer-prices', r.productId] })
      toast.success(`已设置「${r.productName}」的协议价 ${money(r.price)}`)
      setEditing(null)
    },
    onError: (e) => toast.error(errorMessage(e, '保存失败')),
  })
  const remove = useMutation({
    mutationFn: (productId: number) => customerPriceApi.remove(customerId, productId),
    onSuccess: async () => {
      await refresh()
      toast.success('协议价已删除')
      setRemoving(null)
    },
    onError: (e) => toast.error(errorMessage(e, '删除失败')),
  })

  if (!canManage) return null
  const rows = (data ?? []).filter((r) => {
    const f = filter.trim().toLowerCase()
    return !f || r.productName.toLowerCase().includes(f) || r.sku.toLowerCase().includes(f) || r.barcode === f
  })

  function startNew(p: Product) {
    const existing = data?.find((r) => r.productId === p.id)
    setEditing({
      productId: p.id,
      productName: p.name,
      unit: p.unit,
      retailPrice: p.retailPrice,
      wholesalePrice: p.wholesalePrice,
      costPrice: p.costPrice,
      price: existing?.price ?? p.wholesalePrice ?? p.retailPrice,
      notes: existing?.notes ?? '',
      isNew: !existing,
    })
  }

  const priceOk = editing ? isMoney(editing.price) : true
  const belowCost = editing && priceOk && editing.costPrice !== null && (toCents(editing.price) ?? 0) < (toCents(editing.costPrice) ?? 0)

  return (
    <Card>
      <CardHeader
        title="协议价"
        description={
          allowed
            ? '给这个客户的专门价格，开单时自动带出（优先于批发价 / 零售价）'
            : data?.length
              ? '专业版已到期：协议价暂不生效，开单按批发价 / 零售价，续费后恢复'
              : '专业版功能：为老客户设置固定价格，开单自动带出'
        }
        action={allowed && <ImportButton type="customer-prices" size="sm" />}
      />
      {!allowed && !data?.length ? (
        <div className="px-5 pb-5">
          <Link to="/settings/billing" className={buttonVariants({ size: 'sm', variant: 'secondary' })}>
            <Tags aria-hidden />
            了解专业版
          </Link>
        </div>
      ) : (
        <div className="space-y-3 px-5 pb-5">
          {allowed && <ProductPicker onPick={startNew} placeholder="搜索或扫码添加商品，设置协议价" />}
          {isLoading ? (
            <div className="h-20 animate-pulse rounded-lg bg-slate-100" />
          ) : !data?.length ? (
            <p className="rounded-lg border border-dashed border-slate-300 py-6 text-center text-sm text-slate-500">
              还没有协议价。添加商品后，给「{customerName}」开单会自动使用协议价。
            </p>
          ) : (
            <>
              {data.length > 8 && (
                <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="在协议价中查找商品" aria-label="查找协议价商品" />
              )}
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                {rows.map((r) => {
                  const off = discountText(r.price, r.wholesalePrice, r.retailPrice)
                  return (
                    <li key={r.productId} className="flex items-center gap-3 px-3 py-2.5">
                      <div className="min-w-0 flex-1">
                        <Link to={`/products/${r.productId}`} className="block truncate text-sm font-medium text-slate-900 hover:text-blue-600">
                          {r.productName}
                        </Link>
                        <p className="truncate text-xs text-slate-500">
                          {r.wholesalePrice ? `批发 ${money(r.wholesalePrice)} · ` : ''}零售 {money(r.retailPrice)}
                          {r.notes && ` · ${r.notes}`}
                        </p>
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="text-sm font-semibold tabular-nums text-violet-700">{money(r.price)}</p>
                        {r.belowCost ? (
                          <p className="flex items-center justify-end gap-1 text-xs text-red-600">
                            <AlertTriangle className="size-3" aria-hidden />
                            低于成本
                          </p>
                        ) : (
                          off && <p className="text-xs text-slate-500">{off}</p>
                        )}
                      </div>
                      {allowed && (
                        <div className="flex shrink-0">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-8"
                            aria-label={`修改 ${r.productName} 的协议价`}
                            onClick={() =>
                              setEditing({ ...r, costPrice: r.costPrice, notes: r.notes ?? '', isNew: false })
                            }
                          >
                            <Pencil aria-hidden />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-8 text-slate-400 hover:text-red-600"
                            aria-label={`删除 ${r.productName} 的协议价`}
                            onClick={() => setRemoving(r)}
                          >
                            <Trash2 aria-hidden />
                          </Button>
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            </>
          )}
        </div>
      )}

      {editing && (
        <FormDialog
          open
          onClose={() => setEditing(null)}
          title={editing.isNew ? '设置协议价' : '修改协议价'}
          description={`${customerName} · ${editing.productName}`}
          submitText="保存"
          saving={save.isPending}
          onSubmit={(e) => {
            e.preventDefault()
            if (priceOk) save.mutate(editing)
          }}
        >
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
            零售价 {money(editing.retailPrice)}
            {editing.wholesalePrice && ` · 批发价 ${money(editing.wholesalePrice)}`}
            {editing.costPrice && ` · 进货成本 ${money(editing.costPrice)}`}
          </p>
          <Field label={`协议价（每${editing.unit}）`} htmlFor="cp-price" error={priceOk ? undefined : '金额格式不正确'} required>
            <Input
              id="cp-price"
              inputMode="decimal"
              autoFocus
              value={editing.price}
              invalid={!priceOk}
              onChange={(e) => setEditing({ ...editing, price: e.target.value })}
            />
          </Field>
          {belowCost && (
            <p className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
              <AlertTriangle className="size-4 shrink-0" aria-hidden />
              协议价低于进货成本，按这个价格卖会亏本。仍可保存。
            </p>
          )}
          <Field label="备注" htmlFor="cp-notes" hint="例如：2026 年框架协议">
            <Input id="cp-notes" maxLength={200} value={editing.notes} onChange={(e) => setEditing({ ...editing, notes: e.target.value })} />
          </Field>
        </FormDialog>
      )}
      <ConfirmDialog
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title={`删除「${removing?.productName ?? ''}」的协议价？`}
        description="删除后给这个客户开单会按批发价 / 零售价。已开的单不受影响。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.productId)}
      />
    </Card>
  )
}
