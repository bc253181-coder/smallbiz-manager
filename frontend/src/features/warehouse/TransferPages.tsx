import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeftRight, ArrowRight, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardBody, CardHeader } from '@/components/ui/card'
import { Field, FormError, Input, Select, Textarea } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { Pagination } from '@/components/ui/pagination'
import { ProductPicker } from '@/features/catalog/components/ProductPicker'
import type { Product } from '@/features/catalog/types'
import { invalidateStock } from '@/features/inventory/api'
import { errorCode, errorMessage, type ApiErrorBody } from '@/lib/api'
import { formatDateTime, formatQty } from '@/lib/format'
import { cn } from '@/lib/utils'
import { AxiosError } from 'axios'
import { useWarehouses, warehouseApi } from './api'

import { tr } from '@/lib/i18n'
import { warehouseName } from '@/lib/systemText'
// ---------------- 列表 ----------------

export function TransfersPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['transfers', page],
    queryFn: () => warehouseApi.transfers({ page, size: 20 }),
    placeholderData: keepPreviousData,
  })
  return (
    <div>
      <PageHeader
        title={tr('调拨')}
        description={tr('把货从一个仓库调到另一个仓库。调拨单保存即生效、不能修改；调错了再做一张反向调拨。')}
        back={{ to: '/inventory', label: tr('库存') }}
        actions={
          <Link to="/inventory/transfers/new" className={buttonVariants()}>
            <Plus aria-hidden />
            {tr('新建调拨')}
          </Link>
        }
      />
      <Card>
        {isLoading ? (
          <div className="h-40 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : !data?.items.length ? (
          <EmptyState icon={ArrowLeftRight} title={tr('还没有调拨记录')} description={tr('例如：从主仓库调 20 件到门店')} />
        ) : (
          <>
            <ul className="divide-y divide-slate-100">
              {data.items.map((t) => (
                <li key={t.id}>
                  <Link to={`/inventory/transfers/${t.id}`} className="flex items-center justify-between gap-3 px-5 py-3 hover:bg-slate-50">
                    <div className="min-w-0">
                      <p className="flex flex-wrap items-center gap-1.5 text-sm font-medium text-slate-900">
                        {warehouseName(t.fromWarehouseName)}
                        <ArrowRight className="size-3.5 text-slate-400" aria-hidden />
                        {warehouseName(t.toWarehouseName)}
                      </p>
                      <p className="text-xs text-slate-500">
                        {t.transferNo} · {formatDateTime(t.createdAt)} · {t.createdByName ?? ''}
                      </p>
                    </div>
                    <span className="shrink-0 text-sm tabular-nums text-slate-700">
                      {t.itemCount} {tr('种 ·')} {formatQty(t.totalQuantity)} {tr('件')}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            <div className="border-t border-slate-100 px-4 py-3">
              <Pagination page={data.page} totalPages={data.totalPages} totalItems={data.totalItems} unit={tr('张调拨单')} onChange={setPage} />
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

// ---------------- 新建 ----------------

interface Line {
  productId: number
  name: string
  sku: string
  unit: string
  quantity: string
}

export function TransferFormPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const wh = useWarehouses()
  const [fromId, setFromId] = useState<number | null>(null)
  const [toId, setToId] = useState<number | null>(null)
  const [lines, setLines] = useState<Line[]>([])
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // 调出：所有启用的仓库（到期后其他仓库仍可调出）；调入：当前套餐可用的仓库
  const from = fromId ?? wh.defaultWarehouse?.id ?? null
  const toOptions = wh.usable.filter((w) => w.id !== from)
  const to = toId !== null && toOptions.some((w) => w.id === toId) ? toId : (toOptions[0]?.id ?? null)

  const ids = lines.map((l) => l.productId)
  const available = useQuery({
    queryKey: ['warehouse-stocks', from, ids],
    queryFn: () => warehouseApi.stocks(from!, ids),
    enabled: !!from && ids.length > 0,
  })

  function add(p: Product) {
    setLines((ls) =>
      ls.some((l) => l.productId === p.id)
        ? ls.map((l) => (l.productId === p.id ? { ...l, quantity: String((Number(l.quantity) || 0) + 1) } : l))
        : [...ls, { productId: p.id, name: p.name, sku: p.sku, unit: p.unit, quantity: '1' }],
    )
  }

  const qtyOk = (l: Line) => /^\d+$/.test(l.quantity) && Number(l.quantity) >= 1
  const short = (l: Line) => available.data !== undefined && Number(l.quantity) > (available.data.get(l.productId) ?? 0)

  async function submit() {
    setError(null)
    if (!from || !to) return setError(tr('请选择调出和调入仓库'))
    if (lines.length === 0) return setError(tr('请至少添加一个商品'))
    if (lines.some((l) => !qtyOk(l))) return setError(tr('数量需为 1 以上的整数'))
    setSaving(true)
    try {
      const t = await warehouseApi.createTransfer({
        fromWarehouseId: from,
        toWarehouseId: to,
        notes,
        items: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity) })),
      })
      await Promise.all([...lines.map((l) => invalidateStock(queryClient, l.productId)), queryClient.invalidateQueries({ queryKey: ['transfers'] })])
      toast.success(tr('调拨成功：{{transferNo}}', { transferNo: t.transferNo }))
      navigate(`/inventory/transfers/${t.id}`, { replace: true })
    } catch (e) {
      if (errorCode(e) === 'INSUFFICIENT_STOCK' && e instanceof AxiosError) {
        const d = (e.response?.data as ApiErrorBody).details as { productId?: number; available?: number } | undefined
        const line = lines.find((l) => l.productId === d?.productId)
        setError(tr('「{{v0}}」调出仓库只有 {{v1}} {{v2}}，不够调拨', { v0: line?.name ?? tr('商品'), v1: d?.available ?? 0, v2: line?.unit ?? '' }))
      } else {
        setError(errorMessage(e, tr('调拨失败')))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <PageHeader title={tr('新建调拨')} back={{ to: '/inventory/transfers', label: tr('调拨') }} />
      <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <div className="space-y-6 lg:col-span-2">
          <FormError message={error} />
          <Card>
            <CardHeader title={tr('商品')} description={tr('{{length}} 种', { length: lines.length })} />
            <CardBody className="space-y-3">
              <ProductPicker onPick={add} placeholder={tr('搜索或扫码添加要调拨的商品')} />
              {lines.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 py-8 text-center text-sm text-slate-500">{tr('添加要调拨的商品')}</p>
              ) : (
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                  {lines.map((l, i) => (
                    <li key={l.productId} className="flex items-center gap-3 p-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-slate-900">{l.name}</p>
                        <p className={cn('text-xs', short(l) ? 'font-medium text-red-600' : 'text-slate-500')}>
                          {l.sku} {tr('· 调出仓库现有')} {available.data ? (available.data.get(l.productId) ?? 0) : '…'} {l.unit}
                          {short(l) && tr('（不够）')}
                        </p>
                      </div>
                      <Input
                        inputMode="numeric"
                        className="h-9 w-24"
                        value={l.quantity}
                        invalid={!qtyOk(l)}
                        aria-label={tr('{{name}} 调拨数量', { name: l.name })}
                        onChange={(e) => setLines((ls) => ls.map((x, j) => (j === i ? { ...x, quantity: e.target.value.trim() } : x)))}
                      />
                      <Button variant="ghost" size="icon" className="size-9 hover:text-red-600" aria-label={tr('移除 {{name}}', { name: l.name })} onClick={() => setLines((ls) => ls.filter((_, j) => j !== i))}>
                        <Trash2 aria-hidden />
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>
        </div>
        <Card className="h-fit">
          <CardHeader title={tr('仓库')} />
          <CardBody className="space-y-4">
            <Field label={tr('调出仓库')} htmlFor="tf-from">
              <Select id="tf-from" value={from ?? ''} onChange={(e) => setFromId(Number(e.target.value))}>
                {wh.active.map((w) => (
                  <option key={w.id} value={w.id}>
                    {warehouseName(w.name)}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label={tr('调入仓库')} htmlFor="tf-to" hint={toOptions.length === 0 ? tr('没有可以调入的仓库') : undefined}>
              <Select id="tf-to" value={to ?? ''} onChange={(e) => setToId(Number(e.target.value))}>
                {toOptions.map((w) => (
                  <option key={w.id} value={w.id}>
                    {warehouseName(w.name)}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label={tr('备注')} htmlFor="tf-notes">
              <Textarea id="tf-notes" rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Field>
            <Button className="w-full" size="lg" loading={saving} onClick={() => void submit()}>
              {tr('确认调拨')}
            </Button>
            <p className="text-xs text-slate-500">{tr('确认后立即生效：调出仓库减少、调入仓库增加，总库存不变。')}</p>
          </CardBody>
        </Card>
      </div>
    </div>
  )
}

// ---------------- 详情 ----------------

export function TransferDetailPage() {
  const id = Number(useParams().id)
  const { data: t, isLoading, isError, error, refetch } = useQuery({ queryKey: ['transfer', id], queryFn: () => warehouseApi.transfer(id) })
  if (isLoading) return <div className="h-60 animate-pulse rounded-xl bg-slate-100" />
  if (isError || !t) return <ErrorState onRetry={() => void refetch()} message={errorMessage(error)} />
  return (
    <div>
      <PageHeader
        title={t.transferNo}
        description={`${formatDateTime(t.createdAt)}${t.createdByName ? ` · ${t.createdByName}` : ''}`}
        back={{ to: '/inventory/transfers', label: tr('调拨') }}
      />
      <Card>
        <CardBody className="flex flex-wrap items-center gap-3 text-sm">
          <span className="rounded-lg bg-slate-100 px-3 py-1.5 font-medium text-slate-800">{warehouseName(t.fromWarehouseName)}</span>
          <ArrowRight className="size-4 text-slate-400" aria-hidden />
          <span className="rounded-lg bg-blue-50 px-3 py-1.5 font-medium text-blue-800">{warehouseName(t.toWarehouseName)}</span>
          <span className="text-slate-500">
            {t.itemCount} {tr('种 · 共')} {formatQty(t.totalQuantity)} {tr('件')}
          </span>
          {t.notes && <span className="w-full text-slate-600">{tr('备注：')}{t.notes}</span>}
        </CardBody>
        <ul className="divide-y divide-slate-100 border-t border-slate-100">
          {t.items.map((i) => (
            <li key={i.productId} className="flex items-center justify-between gap-3 px-5 py-3">
              <Link to={`/products/${i.productId}`} className="min-w-0 truncate text-sm font-medium text-slate-900 hover:text-blue-600">
                {i.productName}
                <span className="ml-2 text-xs font-normal text-slate-500">{i.sku}</span>
              </Link>
              <span className="shrink-0 tabular-nums text-sm">
                {formatQty(i.quantity)} {i.unit}
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  )
}
