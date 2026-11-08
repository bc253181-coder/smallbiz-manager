import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { supplierApi } from '../api'
import { SupplierFormDialog } from '../SupplierFormDialog'
import { SupplierPurchaseHistory } from '@/features/purchasing/SupplierPurchaseHistory'
import { NotFoundCard, PartnerDetailLayout } from './PartnerDetail'

const BACK = { to: '/suppliers', label: '返回供应商列表' }

export function SupplierDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const { data: s, isLoading, isError, error, refetch } = useQuery({ queryKey: ['supplier', id], queryFn: () => supplierApi.get(id) })
  const remove = useMutation({
    mutationFn: () => supplierApi.remove(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['supplier', id] })
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success('供应商已删除')
      navigate('/suppliers', { replace: true })
    },
    onError: (e) => {
      setDeleting(false)
      toast.error(errorMessage(e, '删除失败'))
    },
  })

  if (isLoading) return <div className="h-80 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  if (isError || !s) {
    return (
      <NotFoundCard
        back={BACK}
        title="供应商"
        error={errorMessage(error)}
        onRetry={() => void refetch()}
        notFound={errorCode(error) === 'SUPPLIER_NOT_FOUND'}
      />
    )
  }

  return (
    <>
      <PartnerDetailLayout
        back={BACK}
        title={s.name}
        canManage
        onEdit={() => setEditing(true)}
        onDelete={() => setDeleting(true)}
        info={[
          { label: '联系人', value: s.contactPerson },
          { label: '联系电话', value: s.phone },
          { label: '邮箱', value: s.email },
          { label: '地址', value: s.address },
          { label: '供应商品', value: s.suppliedProductsNote },
          { label: '备注', value: s.notes },
          { label: '添加时间', value: formatDateTime(s.createdAt) },
        ]}
        history={<SupplierPurchaseHistory supplierId={s.id} />}
      />
      <SupplierFormDialog open={editing} supplier={s} onClose={() => setEditing(false)} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title={`删除供应商「${s.name}」？`}
        description="删除后不再出现在供应商列表中。已有采购单中的供应商信息会保留。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </>
  )
}
