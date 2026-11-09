import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, FolderTree, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Input } from '@/components/ui/form'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/page'
import { errorMessage } from '@/lib/api'
import { useCanManage } from '@/lib/hooks'
import { categoryApi } from '../api'
import type { CategoryNode } from '../types'

import { tr } from '@/lib/i18n'
export function CategoriesPage() {
  const canManage = useCanManage()
  const queryClient = useQueryClient()
  const { data, isLoading, isError, refetch } = useQuery({ queryKey: ['categories'], queryFn: categoryApi.tree })
  const [toDelete, setToDelete] = useState<CategoryNode | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['categories'] })

  const remove = useMutation({
    mutationFn: (id: number) => categoryApi.remove(id),
    onSuccess: async () => {
      await refresh()
      await queryClient.invalidateQueries({ queryKey: ['products'] })
      toast.success(tr('分类已删除'))
      setToDelete(null)
    },
    onError: (e) => {
      setToDelete(null)
      toast.error(errorMessage(e, tr('删除失败')))
    },
  })

  return (
    <div>
      <PageHeader
        title={tr('商品分类')}
        description={tr('最多两级，例如「手机配件 → 数据线」')}
        back={{ to: '/products', label: tr('返回商品列表') }}
      />

      <Card>
        {canManage && (
          <div className="border-b border-slate-100 p-4">
            <AddCategoryForm parentId={null} placeholder={tr('新增一级分类，例如：手机配件')} onDone={refresh} />
          </div>
        )}
        {isLoading ? (
          <div className="h-40 animate-pulse bg-slate-50" />
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : !data || data.length === 0 ? (
          <EmptyState icon={FolderTree} title={tr('还没有分类')} description={tr('分类可以帮助你更快地筛选和统计商品')} />
        ) : (
          <ul className="divide-y divide-slate-100">
            {data.map((root) => (
              <li key={root.id} className="p-4">
                <CategoryRow node={root} canManage={canManage} onChanged={refresh} onDelete={setToDelete} />
                <ul className="mt-2 space-y-1 border-l-2 border-slate-100 pl-4 sm:ml-2">
                  {root.children.map((child) => (
                    <li key={child.id}>
                      <CategoryRow node={child} canManage={canManage} onChanged={refresh} onDelete={setToDelete} />
                    </li>
                  ))}
                  {canManage && (
                    <li className="pt-1">
                      <AddCategoryForm parentId={root.id} placeholder={tr('在「{{name}}」下新增子分类', { name: root.name })} onDone={refresh} compact />
                    </li>
                  )}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <ConfirmDialog
        open={!!toDelete}
        onOpenChange={(o) => !o && setToDelete(null)}
        title={tr('删除分类「{{v0}}」？', { v0: toDelete?.name ?? '' })}
        description={tr('只能删除没有商品和子分类的分类。')}
        confirmText={tr('删除')}
        danger
        loading={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
      />
    </div>
  )
}

function CategoryRow({
  node,
  canManage,
  onChanged,
  onDelete,
}: {
  node: CategoryNode
  canManage: boolean
  onChanged: () => Promise<unknown>
  onDelete: (n: CategoryNode) => void
}) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(node.name)
  const rename = useMutation({
    mutationFn: () => categoryApi.rename(node.id, name.trim()),
    onSuccess: async () => {
      await onChanged()
      setEditing(false)
      toast.success(tr('已重命名'))
    },
    onError: (e) => toast.error(errorMessage(e, tr('重命名失败'))),
  })

  const isRoot = node.parentId === null
  const count = isRoot ? node.productCount + node.children.reduce((s, c) => s + c.productCount, 0) : node.productCount

  if (editing) {
    return (
      <form
        className="flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (name.trim()) rename.mutate()
        }}
      >
        <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} autoFocus aria-label={tr('分类名称')} className="h-9" />
        <Button type="submit" size="icon" className="size-9 shrink-0" loading={rename.isPending} aria-label={tr('保存')}>
          {!rename.isPending && <Check />}
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="size-9 shrink-0"
          aria-label={tr('取消')}
          onClick={() => {
            setName(node.name)
            setEditing(false)
          }}
        >
          <X />
        </Button>
      </form>
    )
  }

  return (
    <div className="flex items-center gap-2">
      <span className={isRoot ? 'font-medium text-slate-900' : 'text-sm text-slate-700'}>{node.name}</span>
      <span className="text-xs text-slate-500">{count} {tr('件商品')}</span>
      <span className="flex-1" />
      {canManage && (
        <>
          <Button variant="ghost" size="icon" className="size-8" aria-label={tr('重命名 {{name}}', { name: node.name })} onClick={() => setEditing(true)}>
            <Pencil />
          </Button>
          <Button variant="ghost" size="icon" className="size-8 hover:text-red-600" aria-label={tr('删除 {{name}}', { name: node.name })} onClick={() => onDelete(node)}>
            <Trash2 />
          </Button>
        </>
      )}
    </div>
  )
}

function AddCategoryForm({
  parentId,
  placeholder,
  onDone,
  compact,
}: {
  parentId: number | null
  placeholder: string
  onDone: () => Promise<unknown>
  compact?: boolean
}) {
  const [name, setName] = useState('')
  const create = useMutation({
    mutationFn: () => categoryApi.create({ name: name.trim(), parentId }),
    onSuccess: async () => {
      await onDone()
      setName('')
      toast.success(tr('分类已添加'))
    },
    onError: (e) => toast.error(errorMessage(e, tr('添加失败'))),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    if (name.trim()) create.mutate()
  }

  return (
    <form onSubmit={submit} className="flex items-center gap-2">
      <Input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder={placeholder}
        maxLength={50}
        aria-label={placeholder}
        className={compact ? 'h-9' : undefined}
      />
      <Button type="submit" variant={compact ? 'secondary' : 'primary'} size={compact ? 'sm' : 'md'} loading={create.isPending} disabled={!name.trim()} className={compact ? 'h-9' : undefined}>
        <Plus aria-hidden />
        {tr('添加')}
      </Button>
    </form>
  )
}
