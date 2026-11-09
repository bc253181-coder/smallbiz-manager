import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Warehouse as WarehouseIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Field, Input, Textarea } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { ErrorState, PageHeader } from '@/components/ui/page'
import { errorMessage, fieldErrors } from '@/lib/api'
import { formatQty, useMoney } from '@/lib/format'
import { useWarehouses, warehouseApi, type Warehouse } from './api'

/** 设置 → 仓库（店主/管理员）。 */
export function WarehousesPage() {
  const money = useMoney()
  const queryClient = useQueryClient()
  const wh = useWarehouses()
  const [editing, setEditing] = useState<Warehouse | 'new' | null>(null)
  const [deactivating, setDeactivating] = useState<Warehouse | null>(null)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['warehouses'] })

  const deactivate = useMutation({
    mutationFn: (id: number) => warehouseApi.deactivate(id),
    onSuccess: async () => {
      await refresh()
      toast.success(`已停用「${deactivating?.name}」`)
      setDeactivating(null)
    },
    onError: (e) => toast.error(errorMessage(e, '停用失败')),
  })

  const activeCount = wh.active.length
  const full = !!wh.data && activeCount >= wh.data.max

  return (
    <div className="space-y-6">
      <PageHeader
        title="仓库"
        description="有门店、仓库、档口等多个放货的地方时，可以分开记录库存并互相调拨。只有一个仓库时，其他页面不会显示仓库选择。"
        back={{ to: '/settings', label: '设置' }}
        actions={
          wh.data?.allowed && (
            <Button onClick={() => setEditing('new')} disabled={full}>
              <Plus aria-hidden />
              新增仓库
            </Button>
          )
        }
      />

      {wh.isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-slate-100" />
      ) : wh.isError ? (
        <ErrorState onRetry={() => void wh.refetch()} message={errorMessage(wh.error)} />
      ) : (
        <>
          {!wh.data?.allowed ? (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
              <p className="font-medium">多仓库是专业版功能</p>
              <p className="mt-1 text-amber-800">
                专业版最多 5 个仓库，支持仓库间调拨、按仓库开单和盘点。
                {wh.all.length > 1 && '专业版到期期间，主仓库照常使用；其他仓库只能把库存调拨出去，续费后恢复。'}
              </p>
              <Link to="/settings/billing" className={buttonVariants({ size: 'sm', className: 'mt-3' })}>
                查看套餐
              </Link>
            </div>
          ) : (
            <p className="text-sm text-slate-600">
              已启用 <span className="font-semibold tabular-nums text-slate-900">{activeCount} / {wh.data.max}</span> 个仓库
              {full && <span className="ml-2 text-amber-700">已达上限，可停用不用的仓库</span>}
            </p>
          )}

          <Card>
            <ul className="divide-y divide-slate-100">
              {wh.all.map((w) => (
                <li key={w.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center">
                  <div className="flex min-w-0 flex-1 items-start gap-3">
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                      <WarehouseIcon className="size-4.5" aria-hidden />
                    </span>
                    <div className="min-w-0">
                      <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
                        {w.name}
                        {w.isDefault && <Badge tone="blue">默认</Badge>}
                        {!w.active && <Badge>已停用</Badge>}
                        {w.active && !w.usable && <Badge tone="amber">只能调出</Badge>}
                      </p>
                      {w.address && <p className="truncate text-xs text-slate-500">{w.address}</p>}
                      <p className="text-xs text-slate-500">
                        {w.productCount} 种商品 · 共 {formatQty(w.totalQty)} 件
                        {w.stockValue !== null && ` · 价值 ${money(w.stockValue)}`}
                      </p>
                    </div>
                  </div>
                  {w.active && (
                    <div className="flex gap-2">
                      <Button variant="ghost" size="sm" onClick={() => setEditing(w)}>
                        <Pencil aria-hidden />
                        编辑
                      </Button>
                      {!w.isDefault && (
                        <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50" onClick={() => setDeactivating(w)}>
                          停用
                        </Button>
                      )}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        </>
      )}

      {editing && <WarehouseDialog warehouse={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
      <ConfirmDialog
        open={!!deactivating}
        onOpenChange={(o) => !o && setDeactivating(null)}
        title={`停用「${deactivating?.name ?? ''}」？`}
        description="停用后不能再在这个仓库开单、入库或调入；历史记录保留。仓库里还有库存时不能停用，请先调拨到其他仓库或盘点为 0。"
        confirmText="停用"
        danger
        loading={deactivate.isPending}
        onConfirm={() => deactivating && deactivate.mutate(deactivating.id)}
      />
    </div>
  )
}

function WarehouseDialog({ warehouse, onClose, onSaved }: { warehouse: Warehouse | null; onClose: () => void; onSaved: () => Promise<unknown> }) {
  const [name, setName] = useState(warehouse?.name ?? '')
  const [address, setAddress] = useState(warehouse?.address ?? '')
  const [notes, setNotes] = useState(warehouse?.notes ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useMutation({
    mutationFn: async () => {
      const body = { name: name.trim(), address: address.trim(), notes: notes.trim() }
      if (warehouse) await warehouseApi.update(warehouse.id, body)
      else await warehouseApi.create(body)
    },
    onSuccess: async () => {
      await onSaved()
      toast.success(warehouse ? '仓库已保存' : `已新增仓库「${name.trim()}」`)
      onClose()
    },
    onError: (e) => {
      const f = fieldErrors(e)
      setErrors(Object.keys(f).length ? f : { name: errorMessage(e, '保存失败') })
    },
  })
  return (
    <FormDialog
      open
      onClose={onClose}
      title={warehouse ? '编辑仓库' : '新增仓库'}
      submitText="保存"
      saving={save.isPending}
      onSubmit={(e) => {
        e.preventDefault()
        if (!name.trim()) return setErrors({ name: '请填写仓库名称' })
        save.mutate()
      }}
    >
      <Field label="仓库名称" htmlFor="wh-name" error={errors.name} required hint="例如：门店、东区仓库、批发市场档口">
        <Input id="wh-name" maxLength={50} value={name} onChange={(e) => setName(e.target.value)} invalid={!!errors.name} autoFocus />
      </Field>
      <Field label="地址" htmlFor="wh-address" error={errors.address}>
        <Input id="wh-address" maxLength={300} value={address} onChange={(e) => setAddress(e.target.value)} />
      </Field>
      <Field label="备注" htmlFor="wh-notes" error={errors.notes}>
        <Textarea id="wh-notes" rows={2} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </Field>
    </FormDialog>
  )
}
