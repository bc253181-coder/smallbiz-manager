import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ReceiptText } from 'lucide-react'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { errorCode, errorMessage } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { useCanManage } from '@/lib/hooks'
import { CUSTOMER_TYPE_LABELS, PRICE_TIER_LABELS, customerApi } from '../api'
import { CustomerFormDialog } from '../CustomerFormDialog'
import { HistoryComingSoon, NotFoundCard, PartnerDetailLayout } from './PartnerDetail'

const BACK = { to: '/customers', label: '返回客户列表' }

export function CustomerDetailPage() {
  const id = Number(useParams().id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canManage = useCanManage()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)

  const { data: c, isLoading, isError, error, refetch } = useQuery({ queryKey: ['customer', id], queryFn: () => customerApi.get(id) })
  const remove = useMutation({
    mutationFn: () => customerApi.remove(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['customer', id] })
      await queryClient.invalidateQueries({ queryKey: ['customers'] })
      toast.success('客户已删除')
      navigate('/customers', { replace: true })
    },
    onError: (e) => {
      setDeleting(false)
      toast.error(errorMessage(e, '删除失败'))
    },
  })

  if (isLoading) return <div className="h-80 animate-pulse rounded-xl bg-slate-100" aria-label="加载中" />
  if (isError || !c) {
    return (
      <NotFoundCard
        back={BACK}
        title="客户"
        error={errorMessage(error)}
        onRetry={() => void refetch()}
        notFound={errorCode(error) === 'CUSTOMER_NOT_FOUND'}
      />
    )
  }

  return (
    <>
      <PartnerDetailLayout
        back={BACK}
        title={c.name}
        badge={<Badge tone={c.priceTier === 'WHOLESALE' ? 'blue' : 'slate'}>{PRICE_TIER_LABELS[c.priceTier]}客户</Badge>}
        canManage={canManage}
        onEdit={() => setEditing(true)}
        onDelete={() => setDeleting(true)}
        info={[
          { label: '客户类型', value: CUSTOMER_TYPE_LABELS[c.customerType] },
          { label: '联系电话', value: c.phone },
          { label: '邮箱', value: c.email },
          { label: '地址', value: c.address },
          { label: '备注', value: c.notes },
          { label: '添加时间', value: formatDateTime(c.createdAt) },
        ]}
        history={
          <HistoryComingSoon
            title="购买记录"
            icon={ReceiptText}
            description="销售模块上线后，这里会显示该客户的历史订单、累计消费金额和最近购买时间。"
          />
        }
      />
      <CustomerFormDialog open={editing} customer={c} onClose={() => setEditing(false)} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title={`删除客户「${c.name}」？`}
        description="删除后不再出现在客户列表中。已有订单中的客户名称会保留。"
        confirmText="删除"
        danger
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </>
  )
}
