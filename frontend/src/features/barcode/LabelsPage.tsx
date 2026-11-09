import { useQueries } from '@tanstack/react-query'
import JsBarcode from 'jsbarcode'
import { Minus, Plus, Printer, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, Select } from '@/components/ui/form'
import { PageHeader } from '@/components/ui/page'
import { ProductPicker } from '@/features/catalog/components/ProductPicker'
import { productApi } from '@/features/catalog/api'
import type { Product } from '@/features/catalog/types'
import { useMoney } from '@/lib/format'
import { cn } from '@/lib/utils'

type Size = '40x30' | '50x30' | 'a4'

/** 标签纸：单张标签机（每页一张）或 A4 不干胶（3 列 × 8 行，每张 70 × 37 mm）。 */
const SIZES: Record<Size, { label: string; page: string; w: number; h: number; perPage: number }> = {
  '40x30': { label: '标签机 40 × 30 mm', page: '40mm 30mm', w: 40, h: 30, perPage: 1 },
  '50x30': { label: '标签机 50 × 30 mm', page: '50mm 30mm', w: 50, h: 30, perPage: 1 },
  a4: { label: 'A4 不干胶 3 × 8（70 × 37 mm）', page: 'A4', w: 70, h: 37, perPage: 24 },
}

interface Line {
  product: Product
  copies: number
}

/** 13 位纯数字且校验位正确 → EAN-13，否则用 Code 128（没有条码时用 SKU，扫 SKU 同样能找到商品）。 */
function barcodeFor(p: Product): { value: string; format: 'EAN13' | 'CODE128'; fromSku: boolean } {
  const v = p.barcode?.trim() || p.sku
  if (/^\d{13}$/.test(v)) {
    const sum = [...v.slice(0, 12)].reduce((s, d, i) => s + Number(d) * (i % 2 === 0 ? 1 : 3), 0)
    if ((10 - (sum % 10)) % 10 === Number(v[12])) return { value: v, format: 'EAN13', fromSku: !p.barcode }
  }
  return { value: v, format: 'CODE128', fromSku: !p.barcode }
}

export function LabelsPage() {
  const money = useMoney()
  const [params] = useSearchParams()
  const initialIds = (params.get('ids') ?? '').split(',').map(Number).filter((n) => n > 0).slice(0, 100)
  const initial = useQueries({ queries: initialIds.map((id) => ({ queryKey: ['product', id], queryFn: () => productApi.get(id) })) })
  const [lines, setLines] = useState<Line[]>([])
  const [loadedInitial, setLoadedInitial] = useState(initialIds.length === 0)
  const [size, setSize] = useState<Size>('40x30')
  const [showPrice, setShowPrice] = useState(true)

  // 从商品详情 / 列表带过来的商品：加载完成后放入列表（只做一次）
  const allLoaded = initial.every((q) => q.isSuccess || q.isError)
  if (!loadedInitial && allLoaded) {
    setLoadedInitial(true)
    setLines(initial.filter((q) => q.data).map((q) => ({ product: q.data!, copies: 1 })))
  }

  function add(p: Product) {
    setLines((ls) => (ls.some((l) => l.product.id === p.id) ? ls.map((l) => (l.product.id === p.id ? { ...l, copies: l.copies + 1 } : l)) : [...ls, { product: p, copies: 1 }]))
  }
  function setCopies(id: number, copies: number) {
    setLines((ls) => ls.map((l) => (l.product.id === id ? { ...l, copies: Math.max(1, Math.min(500, copies)) } : l)))
  }

  const labels = lines.flatMap((l) => Array.from({ length: l.copies }, () => l.product))
  const spec = SIZES[size]
  const total = labels.length

  return (
    <div>
      <PageHeader
        title="打印条码标签"
        description="选择商品和张数，用浏览器打印到标签纸上。没有条码的商品会用 SKU 生成条码，扫码同样能找到。"
        back={{ to: '/products', label: '商品' }}
        actions={
          <Button onClick={() => window.print()} disabled={total === 0}>
            <Printer aria-hidden />
            打印 {total > 0 ? `${total} 张` : ''}
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] [&>*]:min-w-0">
        <Card>
          <CardHeader title="商品" description={lines.length ? `${lines.length} 个商品 · 共 ${total} 张` : undefined} />
          <CardBody className="space-y-3">
            <ProductPicker onPick={add} placeholder="搜索或扫码添加商品" />
            {lines.length === 0 ? (
              <p className="rounded-lg border border-dashed border-slate-300 py-8 text-center text-sm text-slate-500">
                {loadedInitial ? '添加要打印标签的商品' : '加载中…'}
              </p>
            ) : (
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                {lines.map((l) => {
                  const b = barcodeFor(l.product)
                  return (
                    <li key={l.product.id} className="flex items-center gap-3 px-3 py-2">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">{l.product.name}</p>
                        <p className="truncate text-xs text-slate-500">
                          {b.value}
                          {b.fromSku && <span className="text-amber-700"> · 没有条码，使用 SKU</span>}
                        </p>
                      </div>
                      <div className="flex items-center gap-1">
                        <Button variant="ghost" size="icon" className="size-8" aria-label="减少一张" onClick={() => setCopies(l.product.id, l.copies - 1)}>
                          <Minus aria-hidden />
                        </Button>
                        <input
                          type="number"
                          min={1}
                          max={500}
                          value={l.copies}
                          onChange={(e) => setCopies(l.product.id, Number(e.target.value) || 1)}
                          className="h-8 w-14 rounded-md border border-slate-200 text-center text-sm tabular-nums"
                          aria-label={`${l.product.name} 张数`}
                        />
                        <Button variant="ghost" size="icon" className="size-8" aria-label="增加一张" onClick={() => setCopies(l.product.id, l.copies + 1)}>
                          <Plus aria-hidden />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-8 text-slate-400 hover:text-red-600"
                          aria-label={`移除 ${l.product.name}`}
                          onClick={() => setLines((ls) => ls.filter((x) => x.product.id !== l.product.id))}
                        >
                          <Trash2 aria-hidden />
                        </Button>
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="标签设置" />
          <CardBody className="space-y-4">
            <Field label="标签纸" htmlFor="label-size">
              <Select id="label-size" value={size} onChange={(e) => setSize(e.target.value as Size)}>
                {(Object.keys(SIZES) as Size[]).map((s) => (
                  <option key={s} value={s}>
                    {SIZES[s].label}
                  </option>
                ))}
              </Select>
            </Field>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" className="size-4 accent-blue-600" checked={showPrice} onChange={(e) => setShowPrice(e.target.checked)} />
              显示零售价
            </label>
            <p className="text-xs text-slate-500">打印时在浏览器的打印设置里选择对应纸张，边距设为「无」，缩放选「100%」。</p>
            {lines[0] && (
              <div>
                <p className="mb-2 text-xs font-medium text-slate-500">预览</p>
                <div className="flex justify-center rounded-lg bg-slate-100 p-4">
                  <Label product={lines[0].product} w={spec.w} h={spec.h} showPrice={showPrice} money={money} className="bg-white shadow-sm" />
                </div>
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* 打印内容：屏幕上隐藏，打印时只显示这一部分 */}
      <style>{`
        @media print {
          @page { size: ${spec.page}; margin: 0; }
          body * { visibility: hidden !important; }
          #label-print, #label-print * { visibility: visible !important; }
          #label-print { position: absolute; left: 0; top: 0; display: block !important; }
        }
      `}</style>
      <div id="label-print" className="hidden">
        {size === 'a4' ? (
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(3, ${spec.w}mm)`, gridAutoRows: `${spec.h}mm`, width: '210mm', padding: '0' }}>
            {labels.map((p, i) => (
              <Label key={i} product={p} w={spec.w} h={spec.h} showPrice={showPrice} money={money} style={i > 0 && i % 24 === 0 ? { breakBefore: 'page' } : undefined} />
            ))}
          </div>
        ) : (
          labels.map((p, i) => (
            <Label key={i} product={p} w={spec.w} h={spec.h} showPrice={showPrice} money={money} style={{ breakAfter: 'page' }} />
          ))
        )}
      </div>
    </div>
  )
}

function Label({
  product,
  w,
  h,
  showPrice,
  money,
  className,
  style,
}: {
  product: Product
  w: number
  h: number
  showPrice: boolean
  money: ReturnType<typeof useMoney>
  className?: string
  style?: CSSProperties
}) {
  const svg = useRef<SVGSVGElement>(null)
  const b = barcodeFor(product)
  useEffect(() => {
    if (!svg.current) return
    try {
      JsBarcode(svg.current, b.value, { format: b.format, displayValue: true, fontSize: 12, height: 40, margin: 0, width: 1.4 })
    } catch {
      // 内容不能生成条码（理论上不会：Code 128 支持所有 ASCII）
    }
  }, [b.value, b.format])
  return (
    <div
      className={cn('flex flex-col items-center justify-between overflow-hidden px-[2mm] py-[1.5mm] text-black', className)}
      style={{ width: `${w}mm`, height: `${h}mm`, ...style }}
      data-testid="barcode-label"
    >
      <p className="w-full truncate text-center text-[9pt] font-medium leading-tight">{product.name}</p>
      <svg ref={svg} className="max-h-[60%] w-full" aria-label={`条码 ${b.value}`} role="img" />
      {showPrice && <p className="text-[10pt] font-semibold leading-tight">{money(product.retailPrice)}</p>}
    </div>
  )
}
