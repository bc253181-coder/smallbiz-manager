import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Plus, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, FormError, Input, Select, Textarea } from '@/components/ui/form'
import { PageHeader } from '@/components/ui/page'
import { ProductPicker } from '@/features/catalog/components/ProductPicker'
import type { Product } from '@/features/catalog/types'
import { customerApi, PRICE_TIER_LABELS, type PriceTier } from '@/features/partners/api'
import { CustomerFormDialog } from '@/features/partners/CustomerFormDialog'
import { errorCode, errorMessage, type ApiErrorBody } from '@/lib/api'
import { useFeature } from '@/features/billing/api'
import { useMoney } from '@/lib/format'
import { fromCents, isMoney, toCents } from '@/lib/money'
import { cn } from '@/lib/utils'
import { AxiosError } from 'axios'
import { receivableApi } from '@/features/receivables/api'
import { useAuth } from '@/features/auth/AuthProvider'
import { readLastWarehouse, saveLastWarehouse, useWarehouses, warehouseApi } from '@/features/warehouse/api'
import { customerPriceApi } from '@/features/partners/customerPrices'
import { saleApi } from '../api'

import { tr } from '@/lib/i18n'
import { warehouseName } from '@/lib/systemText'
interface Line {
  productId: number
  name: string
  sku: string
  unit: string
  stockQty: number
  retailPrice: string
  wholesalePrice: string | null
  quantity: string
  unitPrice: string
  /** 手动改过单价：切换价格类型时不再自动覆盖 */
  priceEdited: boolean
  /** 当前单价来自客户协议价 */
  contract: boolean
}

function tierPrice(l: Pick<Line, 'retailPrice' | 'wholesalePrice'>, tier: PriceTier): string {
  return tier === 'WHOLESALE' && l.wholesalePrice ? l.wholesalePrice : l.retailPrice
}

export function SaleFormPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const money = useMoney()
  const [params] = useSearchParams()

  const [customerId, setCustomerId] = useState<string>(params.get('customerId') ?? '')
  const [tier, setTier] = useState<PriceTier>('RETAIL')
  const [lines, setLines] = useState<Line[]>([])
  const [discount, setDiscount] = useState('')
  const [notes, setNotes] = useState('')
  const [showErrors, setShowErrors] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [newCustomer, setNewCustomer] = useState(false)

  const canCustomers = useFeature('CUSTOMERS')
  const canWholesale = useFeature('WHOLESALE_PRICE')
  const canCredit = useFeature('RECEIVABLES')
  const [paid, setPaid] = useState('')
  const customers = useQuery({
    queryKey: ['customers', 'all'],
    queryFn: () => customerApi.list({ size: 100, sort: 'name,asc' }),
    enabled: canCustomers,
  })
  // 从客户详情页"开单"进入时，按该客户的默认价格
  const [initialTierApplied, setInitialTierApplied] = useState(false)
  if (!initialTierApplied && customerId && customers.data) {
    const c = customers.data.items.find((x) => String(x.id) === customerId)
    if (c) setTier(c.priceTier)
    setInitialTierApplied(true)
  }

  function changeTier(next: PriceTier) {
    setTier(next)
    setLines((ls) => ls.map((l) => (l.priceEdited || l.contract ? l : { ...l, unitPrice: tierPrice(l, next) })))
  }

  // 客户协议价（专业版）：选客户、加商品时取价；手动改过的行不覆盖
  const canContract = useFeature('CUSTOMER_PRICES')
  const customerRef = useRef(customerId)
  async function applyContractPrices(forCustomer: string, productIds: number[]) {
    if (!canContract || !forCustomer || productIds.length === 0) return
    try {
      const quotes = await customerPriceApi.quote(Number(forCustomer), productIds)
      if (customerRef.current !== forCustomer) return // 期间又换了客户
      const byId = new Map(quotes.map((q) => [q.productId, q.price]))
      setLines((ls) =>
        ls.map((l) => (l.priceEdited || !byId.has(l.productId) ? l : { ...l, unitPrice: byId.get(l.productId)!, contract: true })),
      )
    } catch {
      // 取不到协议价时按零售 / 批发价，开单时后端会再按规则核对价格来源
    }
  }

  function changeCustomer(id: string) {
    setCustomerId(id)
    customerRef.current = id
    const c = customers.data?.items.find((x) => String(x.id) === id)
    const next = c ? c.priceTier : 'RETAIL'
    setTier(next)
    // 换客户：未手改的行先回到该客户的零售 / 批发价，再套用新客户的协议价
    setLines((ls) => ls.map((l) => (l.priceEdited ? l : { ...l, unitPrice: tierPrice(l, next), contract: false })))
    void applyContractPrices(id, lines.map((l) => l.productId))
  }

  function addProduct(p: Product) {
    if (!lines.some((l) => l.productId === p.id)) void applyContractPrices(customerId, [p.id])
    setLines((ls) =>
      ls.some((l) => l.productId === p.id)
        ? ls.map((l) => (l.productId === p.id ? { ...l, quantity: String((Number(l.quantity) || 0) + 1) } : l))
        : [
            ...ls,
            {
              productId: p.id,
              name: p.name,
              sku: p.sku,
              unit: p.unit,
              stockQty: p.stockQty,
              retailPrice: p.retailPrice,
              wholesalePrice: p.wholesalePrice,
              quantity: '1',
              unitPrice: tierPrice(p, tier),
              priceEdited: false,
              contract: false,
            },
          ],
    )
  }

  function updateLine(idx: number, patch: Partial<Line>) {
    setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, ...patch } : l)))
  }

  // 多仓库：选择出货仓库，库存按该仓库显示和判断（只有一个仓库时不显示）
  const { session } = useAuth()
  const tenantId = session?.currentTenant?.id
  const wh = useWarehouses()
  const [pickedWarehouse, setPickedWarehouse] = useState<number | null>(null)
  const lastWarehouse = readLastWarehouse(tenantId)
  const warehouseId =
    pickedWarehouse ??
    (wh.usable.some((w) => w.id === lastWarehouse) ? lastWarehouse : null) ??
    wh.defaultWarehouse?.id ??
    null
  const lineIds = lines.map((l) => l.productId)
  const warehouseStock = useQuery({
    queryKey: ['warehouse-stocks', warehouseId, lineIds],
    queryFn: () => warehouseApi.stocks(warehouseId!, lineIds),
    enabled: wh.multi && !!warehouseId && lineIds.length > 0,
  })
  const stockOf = (l: Line) => (wh.multi ? (warehouseStock.data?.get(l.productId) ?? l.stockQty) : l.stockQty)

  const qtyOf = (l: Line) => (/^\d+$/.test(l.quantity) ? Number(l.quantity) : 0)
  const lineErrors = lines.map((l) => ({
    quantity: /^\d+$/.test(l.quantity) && Number(l.quantity) >= 1 && Number(l.quantity) <= 1_000_000 ? null : tr('请填写 1 以上的整数'),
    unitPrice: isMoney(l.unitPrice) ? null : tr('金额格式不正确'),
  }))
  const subtotalCents = lines.reduce((s, l) => s + (toCents(l.unitPrice) ?? 0) * qtyOf(l), 0)
  const discountCents = discount.trim() ? toCents(discount) : 0
  const discountError =
    discountCents === null ? tr('金额格式不正确') : discountCents > subtotalCents ? tr('优惠不能超过商品合计') : null
  const totalCents = subtotalCents - (discountCents ?? 0)
  const totalQty = lines.reduce((s, l) => s + qtyOf(l), 0)
  // 赊账（专业版）：只有选了客户才显示「本次实收」；留空 = 全额
  const creditEnabled = canCustomers && canCredit && !!customerId
  const paidCents = creditEnabled && paid.trim() ? toCents(paid) : null
  const paidError = !creditEnabled || !paid.trim() ? null : paidCents === null ? tr('金额格式不正确') : paidCents > Math.max(totalCents, 0) ? tr('实收不能超过应收') : null
  const owedCents = paidCents === null ? 0 : Math.max(totalCents, 0) - paidCents
  const customerDebt = useQuery({
    queryKey: ['customer-receivable', Number(customerId)],
    queryFn: () => receivableApi.customer(Number(customerId)),
    enabled: creditEnabled,
  })

  async function submit() {
    if (saving) return
    setShowErrors(true)
    setFormError(null)
    if (lines.length === 0) return setFormError(tr('请至少添加一个商品'))
    if (lineErrors.some((x) => x.quantity || x.unitPrice) || discountError || paidError) return setFormError(tr('请修正标红的内容'))
    if (notes.length > 500) return setFormError(tr('备注最多 500 字'))

    setSaving(true)
    try {
      const sale = await saleApi.create({
        customerId: canCustomers && customerId ? Number(customerId) : null,
        priceTier: canWholesale ? tier : 'RETAIL',
        discountAmount: discount.trim() || null,
        notes,
        paidAmount: paidCents === null ? null : fromCents(paidCents),
        items: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity), unitPrice: l.unitPrice.trim() })),
        warehouseId: wh.multi && warehouseId ? warehouseId : undefined,
      })
      if (wh.multi && warehouseId) saveLastWarehouse(tenantId, warehouseId)
      queryClient.setQueryData(['sale', sale.id], sale)
      await Promise.all(
        [['sales'], ['products'], ['product'], ['product-movements'], ['inventory'], ['customer-stats'], ['receivables'], ['customer-receivable']].map((k) =>
          queryClient.invalidateQueries({ queryKey: k }),
        ),
      )
      toast.success(
        Number(sale.outstanding) > 0
          ? tr('开单成功：{{orderNo}}，实收 {{paidAmount}}，欠款 {{outstanding}}', { orderNo: sale.orderNo, paidAmount: money(sale.paidAmount), outstanding: money(sale.outstanding) })
          : tr('开单成功：{{orderNo}}，实收 {{totalAmount}}', { orderNo: sale.orderNo, totalAmount: money(sale.totalAmount) }),
      )
      navigate(`/sales/${sale.id}`, { replace: true })
    } catch (err) {
      const code = errorCode(err)
      if (code === 'INSUFFICIENT_STOCK' && err instanceof AxiosError) {
        const d = (err.response?.data as ApiErrorBody).details as { productId?: number; available?: number } | undefined
        const line = lines.find((l) => l.productId === d?.productId)
        setFormError(tr('「{{v0}}」{{v1}}库存不足，当前只有 {{v2}} {{v3}}。请减少数量，或先入库{{v4}}。', { v0: line?.name ?? tr('商品'), v1: wh.multi ? tr('该仓库') : '', v2: d?.available ?? 0, v3: line?.unit ?? '', v4: wh.multi ? tr(' / 调拨') : '' }))
      } else {
        setFormError(errorMessage(err, tr('开单失败')))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader title={tr('开单')} description={tr('开单后立即扣减库存')} back={{ to: '/sales', label: tr('返回销售列表') }} />
      {/* 开单会立即扣库存：只允许点击按钮提交，输入框里按回车不会误开单 */}
      <form onSubmit={(e) => e.preventDefault()} noValidate className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <div className="space-y-6 lg:col-span-2">
          <FormError message={formError} />
          <Card>
            <CardHeader title={tr('商品')} description={tr('{{length}} 种，共 {{totalQty}} 件', { length: lines.length, totalQty: totalQty })} />
            <CardBody className="space-y-4">
              <ProductPicker
                activeOnly
                onPick={addProduct}
                placeholder={tr('搜索商品名称、SKU 或扫码输入条码')}
                renderMeta={(p) => tr('零售 {{retailPrice}}{{v1}}', { retailPrice: money(p.retailPrice), v1: p.wholesalePrice ? tr(' / 批发 {{wholesalePrice}}', { wholesalePrice: money(p.wholesalePrice) }) : '' })}
              />
              {lines.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 py-8 text-center text-sm text-slate-500">
                  {tr('搜索商品添加到订单。再次选择同一商品会增加数量。')}
                </p>
              ) : (
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                  {lines.map((l, idx) => {
                    const err = showErrors ? lineErrors[idx] : { quantity: null, unitPrice: null }
                    const c = toCents(l.unitPrice)
                    const sub = c !== null ? fromCents(c * qtyOf(l)) : null
                    const short = qtyOf(l) > stockOf(l)
                    return (
                      <li key={l.productId} className="grid grid-cols-[1fr_1fr_1fr_auto] items-start gap-x-3 gap-y-2 p-3">
                        <div className="col-span-full min-w-0">
                          <p className="truncate text-sm font-medium text-slate-900">{l.name}</p>
                          <p className={cn('text-xs', short ? 'font-medium text-amber-700' : 'text-slate-500')}>
                            {l.sku} · {wh.multi ? tr('该仓库') : ''}{tr('库存')} {stockOf(l)} {l.unit}
                            {short && tr('（库存不足）')}
                          </p>
                        </div>
                        <label className="text-xs text-slate-500">
                          {tr('数量（')}{l.unit}）
                          <Input
                            inputMode="numeric"
                            value={l.quantity}
                            onChange={(e) => updateLine(idx, { quantity: e.target.value.trim() })}
                            invalid={!!err.quantity}
                            className="mt-1 h-9"
                            aria-label={tr('{{name}} 数量', { name: l.name })}
                          />
                          {err.quantity && <span className="mt-1 block text-red-600">{err.quantity}</span>}
                        </label>
                        <label className="text-xs text-slate-500">
                          {tr('单价')}
                          {l.priceEdited ? (
                            tr('（已改价）')
                          ) : l.contract ? (
                            <span className="ml-1 rounded bg-violet-50 px-1 py-px text-[11px] font-medium text-violet-700">{tr('协议价')}</span>
                          ) : null}
                          <Input
                            inputMode="decimal"
                            value={l.unitPrice}
                            onChange={(e) => updateLine(idx, { unitPrice: e.target.value, priceEdited: true })}
                            invalid={!!err.unitPrice}
                            className="mt-1 h-9"
                            aria-label={tr('{{name}} 单价', { name: l.name })}
                          />
                          {err.unitPrice && <span className="mt-1 block text-red-600">{err.unitPrice}</span>}
                        </label>
                        <div className="text-xs text-slate-500">
                          {tr('小计')}
                          <p className="mt-1 flex h-9 items-center text-sm font-medium tabular-nums text-slate-900">{sub ? money(sub) : '—'}</p>
                        </div>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="mt-5 size-9 hover:text-red-600"
                          aria-label={tr('移除 {{name}}', { name: l.name })}
                          onClick={() => setLines((ls) => ls.filter((_, i) => i !== idx))}
                        >
                          <Trash2 />
                        </Button>
                      </li>
                    )
                  })}
                </ul>
              )}
              {lines.some((l) => qtyOf(l) > stockOf(l)) && (
                <p className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  <AlertTriangle className="size-4 shrink-0" aria-hidden />
                  {tr('有商品库存不足。除非在「设置」中开启了负库存，否则开单会失败。')}
                </p>
              )}
            </CardBody>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader title={tr('客户与价格')} />
            <CardBody className="space-y-4">
              {wh.multi && (
                <Field label={tr('出货仓库')} htmlFor="sale-warehouse">
                  <Select id="sale-warehouse" value={warehouseId ?? ''} onChange={(e) => setPickedWarehouse(Number(e.target.value))}>
                    {wh.usable.map((w) => (
                      <option key={w.id} value={w.id}>
                        {warehouseName(w.name)}
                      </option>
                    ))}
                  </Select>
                </Field>
              )}
              {!canCustomers && (
                <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
                  {tr('普通会员开单按散客零售价。升级高级永久会员可记录客户、使用批发价。')}{' '}
                  <Link to="/settings/billing" className="font-medium text-blue-600 hover:underline">
                    {tr('查看套餐')}
                  </Link>
                </p>
              )}
              {canCustomers && (<>
              <Field label={tr('客户')} htmlFor="so-customer">
                <Select id="so-customer" value={customerId} onChange={(e) => changeCustomer(e.target.value)}>
                  <option value="">{tr('散客（不记录客户）')}</option>
                  {customers.data?.items.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                      {c.priceTier === 'WHOLESALE' ? tr('（批发）') : ''}
                    </option>
                  ))}
                </Select>
              </Field>
              <button type="button" onClick={() => setNewCustomer(true)} className="inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline">
                <Plus className="size-4" aria-hidden />
                {tr('新增客户')}
              </button>
              </>)}
              {canWholesale && (
              <fieldset>
                <legend className="mb-1.5 text-sm font-medium text-slate-700">{tr('价格')}</legend>
                <div className="grid grid-cols-2 gap-2">
                  {(['RETAIL', 'WHOLESALE'] as PriceTier[]).map((t) => (
                    <button
                      key={t}
                      type="button"
                      aria-pressed={tier === t}
                      onClick={() => changeTier(t)}
                      className={cn(
                        'h-10 rounded-lg border text-sm font-medium',
                        tier === t ? 'border-blue-600 bg-blue-50 text-blue-700' : 'border-slate-300 text-slate-700 hover:bg-slate-50',
                      )}
                    >
                      {PRICE_TIER_LABELS[t]}
                    </button>
                  ))}
                </div>
                <p className="mt-1 text-xs text-slate-500">{tr('切换后，未手动改过的单价会自动更新')}</p>
              </fieldset>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardBody className="space-y-3">
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">{tr('商品合计')}</span>
                <span className="tabular-nums">{money(fromCents(subtotalCents))}</span>
              </div>
              <Field label={tr('整单优惠')} htmlFor="so-discount" error={showErrors || discountCents !== 0 ? (discountError ?? undefined) : undefined}>
                <Input id="so-discount" inputMode="decimal" placeholder="0.00" value={discount} onChange={(e) => setDiscount(e.target.value)} invalid={!!discountError && discount !== ''} />
              </Field>
              <div className="flex items-end justify-between border-t border-slate-100 pt-3">
                <span className="text-sm text-slate-500">{tr('应收')}</span>
                <span className="text-2xl font-semibold tabular-nums text-slate-900">{money(fromCents(Math.max(totalCents, 0)))}</span>
              </div>
              {creditEnabled && (
                <div className="space-y-1">
                  <Field label={tr('本次实收')} htmlFor="so-paid" hint={tr('留空 = 全额收款；少收的部分记为客户欠款')} error={showErrors || paid ? (paidError ?? undefined) : undefined}>
                    <Input id="so-paid" inputMode="decimal" placeholder={fromCents(Math.max(totalCents, 0))} value={paid} onChange={(e) => setPaid(e.target.value)} invalid={!!paidError} />
                  </Field>
                  {owedCents > 0 && !paidError && (
                    <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
                      {tr('本单欠款')} <span className="font-semibold tabular-nums">{money(fromCents(owedCents))}</span>
                      {customerDebt.data && Number(customerDebt.data.outstanding) > 0 && (
                        <>{tr('，该客户原有欠款')} {money(customerDebt.data.outstanding)}</>
                      )}
                    </p>
                  )}
                </div>
              )}
              <Field label={tr('备注')} htmlFor="so-notes" error={notes.length > 500 ? tr('最多 500 字') : undefined}>
                <Textarea id="so-notes" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
              </Field>
              <Button size="lg" className="w-full" loading={saving} onClick={() => void submit()}>
                {tr('完成开单')}
              </Button>
            </CardBody>
          </Card>
        </div>
      </form>

      <CustomerFormDialog
        open={newCustomer}
        onClose={() => setNewCustomer(false)}
        onSaved={async (c) => {
          await queryClient.invalidateQueries({ queryKey: ['customers'] })
          setCustomerId(String(c.id))
          changeTier(c.priceTier)
        }}
      />
    </div>
  )
}
