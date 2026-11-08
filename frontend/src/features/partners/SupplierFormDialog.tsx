import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Field, FormError, Input, Textarea } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { supplierApi, type Supplier } from './api'

const schema = z.object({
  name: z.string().trim().min(1, '请填写供应商名称').max(100, '最多 100 字'),
  contactPerson: z.string().trim().max(50, '最多 50 字'),
  phone: z.string().trim().max(30, '最多 30 位').regex(/^[0-9+\- ()]*$/, '电话格式不正确'),
  email: z.union([z.literal(''), z.string().trim().pipe(z.email('邮箱格式不正确'))]),
  address: z.string().trim().max(300, '最多 300 字'),
  suppliedProductsNote: z.string().trim().max(500, '最多 500 字'),
  notes: z.string().max(2000, '最多 2000 字'),
})
type Values = z.infer<typeof schema>

const EMPTY: Values = { name: '', contactPerson: '', phone: '', email: '', address: '', suppliedProductsNote: '', notes: '' }

export function SupplierFormDialog({
  open,
  supplier,
  onClose,
  onSaved,
}: {
  open: boolean
  supplier?: Supplier
  onClose: () => void
  onSaved?: (s: Supplier) => void
}) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: EMPTY })

  useEffect(() => {
    if (!open) return
    setFormError(null)
    reset(
      supplier
        ? {
            name: supplier.name,
            contactPerson: supplier.contactPerson ?? '',
            phone: supplier.phone ?? '',
            email: supplier.email ?? '',
            address: supplier.address ?? '',
            suppliedProductsNote: supplier.suppliedProductsNote ?? '',
            notes: supplier.notes ?? '',
          }
        : EMPTY,
    )
  }, [open, supplier, reset])

  const onSubmit = handleSubmit(async (v) => {
    setFormError(null)
    try {
      const saved = supplier ? await supplierApi.update(supplier.id, { ...v, version: supplier.version }) : await supplierApi.create(v)
      queryClient.setQueryData(['supplier', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      toast.success(supplier ? '供应商资料已保存' : `已添加供应商「${saved.name}」`)
      onSaved?.(saved)
      onClose()
    } catch (e) {
      const f = fieldErrors(e)
      Object.entries(f).forEach(([k, m]) => setError(k as keyof Values, { message: m }))
      if (errorCode(e) === 'CONCURRENT_MODIFICATION') setFormError('该供应商刚被其他人修改过，请关闭后刷新页面再修改。')
      else if (!Object.keys(f).length) setFormError(errorMessage(e, '保存失败'))
    }
  })

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title={supplier ? '编辑供应商' : '新增供应商'}
      submitText={supplier ? '保存' : '添加供应商'}
      saving={isSubmitting}
      onSubmit={onSubmit}
    >
      <FormError message={formError} />
      <Field label="供应商名称" htmlFor="s-name" error={errors.name?.message} required>
        <Input id="s-name" autoFocus invalid={!!errors.name} placeholder="例如：深圳华强配件厂" {...register('name')} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="联系人" htmlFor="s-contact" error={errors.contactPerson?.message}>
          <Input id="s-contact" invalid={!!errors.contactPerson} {...register('contactPerson')} />
        </Field>
        <Field label="联系电话" htmlFor="s-phone" error={errors.phone?.message}>
          <Input id="s-phone" type="tel" invalid={!!errors.phone} {...register('phone')} />
        </Field>
      </div>
      <Field label="邮箱" htmlFor="s-email" error={errors.email?.message}>
        <Input id="s-email" type="email" invalid={!!errors.email} {...register('email')} />
      </Field>
      <Field label="地址" htmlFor="s-address" error={errors.address?.message}>
        <Input id="s-address" invalid={!!errors.address} {...register('address')} />
      </Field>
      <Field label="供应商品" htmlFor="s-products" error={errors.suppliedProductsNote?.message} hint="简单描述主要供应哪些商品，方便搜索">
        <Input id="s-products" invalid={!!errors.suppliedProductsNote} placeholder="例如：手机壳、数据线、充电头" {...register('suppliedProductsNote')} />
      </Field>
      <Field label="备注" htmlFor="s-notes" error={errors.notes?.message}>
        <Textarea id="s-notes" rows={2} invalid={!!errors.notes} placeholder="例如：月结 30 天、满 500 包邮" {...register('notes')} />
      </Field>
    </FormDialog>
  )
}
