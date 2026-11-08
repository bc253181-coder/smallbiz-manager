import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Field, FormError, Input, Select, Textarea } from '@/components/ui/form'
import { FormDialog } from '@/components/ui/form-dialog'
import { errorCode, errorMessage, fieldErrors } from '@/lib/api'
import { CUSTOMER_TYPE_LABELS, PRICE_TIER_LABELS, customerApi, type Customer, type CustomerType, type PriceTier } from './api'

const schema = z.object({
  name: z.string().trim().min(1, '请填写客户名称').max(100, '最多 100 字'),
  phone: z.string().trim().max(30, '最多 30 位').regex(/^[0-9+\- ()]*$/, '电话格式不正确'),
  email: z.union([z.literal(''), z.string().trim().pipe(z.email('邮箱格式不正确'))]),
  address: z.string().trim().max(300, '最多 300 字'),
  customerType: z.enum(['INDIVIDUAL', 'BUSINESS']),
  priceTier: z.enum(['RETAIL', 'WHOLESALE']),
  notes: z.string().max(2000, '最多 2000 字'),
})
type Values = z.infer<typeof schema>

const EMPTY: Values = { name: '', phone: '', email: '', address: '', customerType: 'INDIVIDUAL', priceTier: 'RETAIL', notes: '' }

/** 新增 / 编辑客户。customer 为空时为新增。 */
export function CustomerFormDialog({
  open,
  customer,
  onClose,
  onSaved,
}: {
  open: boolean
  customer?: Customer
  onClose: () => void
  onSaved?: (c: Customer) => void
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
      customer
        ? {
            name: customer.name,
            phone: customer.phone ?? '',
            email: customer.email ?? '',
            address: customer.address ?? '',
            customerType: customer.customerType,
            priceTier: customer.priceTier,
            notes: customer.notes ?? '',
          }
        : EMPTY,
    )
  }, [open, customer, reset])

  const onSubmit = handleSubmit(async (v) => {
    setFormError(null)
    try {
      const saved = customer ? await customerApi.update(customer.id, { ...v, version: customer.version }) : await customerApi.create(v)
      queryClient.setQueryData(['customer', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['customers'] })
      toast.success(customer ? '客户资料已保存' : `已添加客户「${saved.name}」`)
      onSaved?.(saved)
      onClose()
    } catch (e) {
      const f = fieldErrors(e)
      Object.entries(f).forEach(([k, m]) => setError(k as keyof Values, { message: m }))
      if (errorCode(e) === 'CONCURRENT_MODIFICATION') setFormError('该客户刚被其他人修改过，请关闭后刷新页面再修改。')
      else if (!Object.keys(f).length) setFormError(errorMessage(e, '保存失败'))
    }
  })

  return (
    <FormDialog
      open={open}
      onClose={onClose}
      title={customer ? '编辑客户' : '新增客户'}
      submitText={customer ? '保存' : '添加客户'}
      saving={isSubmitting}
      onSubmit={onSubmit}
    >
      <FormError message={formError} />
      <Field label="客户名称" htmlFor="c-name" error={errors.name?.message} required>
        <Input id="c-name" autoFocus invalid={!!errors.name} placeholder="个人姓名或公司/店铺名称" {...register('name')} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="联系电话" htmlFor="c-phone" error={errors.phone?.message}>
          <Input id="c-phone" type="tel" invalid={!!errors.phone} {...register('phone')} />
        </Field>
        <Field label="邮箱" htmlFor="c-email" error={errors.email?.message}>
          <Input id="c-email" type="email" invalid={!!errors.email} {...register('email')} />
        </Field>
        <Field label="客户类型" htmlFor="c-type">
          <Select id="c-type" {...register('customerType')}>
            {(Object.keys(CUSTOMER_TYPE_LABELS) as CustomerType[]).map((k) => (
              <option key={k} value={k}>
                {CUSTOMER_TYPE_LABELS[k]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="默认价格" htmlFor="c-tier" hint="开单时自动带出对应价格">
          <Select id="c-tier" {...register('priceTier')}>
            {(Object.keys(PRICE_TIER_LABELS) as PriceTier[]).map((k) => (
              <option key={k} value={k}>
                {PRICE_TIER_LABELS[k]}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <Field label="地址" htmlFor="c-address" error={errors.address?.message}>
        <Input id="c-address" invalid={!!errors.address} {...register('address')} />
      </Field>
      <Field label="备注" htmlFor="c-notes" error={errors.notes?.message}>
        <Textarea id="c-notes" rows={2} invalid={!!errors.notes} placeholder="例如：每月初补货、喜欢红色系" {...register('notes')} />
      </Field>
    </FormDialog>
  )
}
